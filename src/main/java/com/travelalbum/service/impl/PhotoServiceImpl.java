package com.travelalbum.service.impl;

import com.travelalbum.dto.response.BatchDeleteFailure;
import com.travelalbum.dto.response.BatchDeleteResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.UploadFailure;
import com.travelalbum.dto.response.UploadResultResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.Photo;
import com.travelalbum.entity.User;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.mapper.PhotoMapper;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.ShareLinkRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.PhotoService;
import com.travelalbum.storage.ExifInfo;
import com.travelalbum.storage.HeicSupport;
import com.travelalbum.storage.RawImageSupport;
import com.travelalbum.storage.RawPreview;
import com.travelalbum.storage.StorageService;
import com.travelalbum.storage.StoredFile;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PhotoServiceImpl implements PhotoService {

    private static final long MAX_SIZE = 50L * 1024 * 1024;
    private static final int MAX_FILES = 100;
    private static final int MAX_BATCH_DELETE = 100;
    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");

    private final PhotoRepository photoRepository;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final UserRepository userRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final StorageService storageService;
    private final PhotoMapper photoMapper;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public UploadResultResponse uploadMultiple(Long eventId, MultipartFile[] files, Long userId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));
        boolean isOwner = event.getOwnerId().equals(userId);
        boolean isEditor = eventMemberRepository.existsByEventIdAndUserIdAndRole(eventId, userId, EventMemberRole.EDITOR);
        if (!isOwner && !isEditor) {
            throw new AccessDeniedException("Not allowed to upload to this event");
        }
        if (files.length > MAX_FILES) {
            throw new BusinessException("Too many files in one request", "TOO_MANY_FILES");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));
        long incomingSize = Arrays.stream(files).mapToLong(MultipartFile::getSize).sum();
        if (user.getStorageUsed() + incomingSize > user.getStorageQuota()) {
            throw new BusinessException("Storage quota exceeded", "QUOTA_EXCEEDED");
        }

        List<PhotoResponse> uploaded = new ArrayList<>();
        List<UploadFailure> failed = new ArrayList<>();
        long actualUploadedSize = 0L;

        for (MultipartFile file : files) {
            try {
                validate(file);
                if (photoRepository.existsByEventIdAndOriginalName(eventId, file.getOriginalFilename())) {
                    failed.add(new UploadFailure(file.getOriginalFilename(), "PHOTO_EXIST"));
                    continue;
                }
                StoredFile stored = storageService.store(user.getStorageFolder(), event.getStorageFolder(), file);
                // EXIF thật (máy ảnh, ống kính, khẩu độ, ISO, ngày chụp...) — lỗi đọc không ảnh hưởng upload
                ExifInfo exif = storageService.readExif(user.getStorageFolder(), stored.relativePath());
                Integer width = stored.width();
                Integer height = stored.height();
                String previewPath = null;
                if (exif != null && exif.swapsDimensions() && !HeicSupport.needsPreview(file.getOriginalFilename())) {
                    // Ảnh chụp dọc bằng điện thoại: file lưu 2560x1184 + Orientation=6 -> hiển thị 1184x2560
                    Integer tmp = width;
                    width = height;
                    height = tmp;
                }
                if (HeicSupport.needsPreview(file.getOriginalFilename())) {
                    // RAW / HEIC: giữ file gốc để tải về + tạo JPEG xem trước để hiển thị trên web.
                    RawPreview preview = storageService.createRawPreview(user.getStorageFolder(), stored.relativePath());
                    if (preview != null) {
                        previewPath = preview.relativePath();
                        width = preview.width();
                        height = preview.height();
                    }
                }
                Photo photo = Photo.builder()
                        .event(event)
                        .cameraModel(exif != null ? exif.cameraModel() : null)
                        .lensModel(exif != null ? exif.lensModel() : null)
                        .focalLength(exif != null ? exif.focalLength() : null)
                        .focalLength35mm(exif != null ? exif.focalLength35mm() : null)
                        .exifReadAt(LocalDateTime.now())
                        .aperture(exif != null ? exif.aperture() : null)
                        .shutterSpeed(exif != null ? exif.shutterSpeed() : null)
                        .iso(exif != null ? exif.iso() : null)
                        .takenAt(exif != null ? exif.takenAt() : null)
                        .fileName(stored.fileName())
                        .originalName(file.getOriginalFilename())
                        .path(stored.relativePath())
                        .previewPath(previewPath)
                        .size(file.getSize())
                        .mimeType(resolveMimeType(file))
                        .width(width)
                        .height(height)
                        .checksum(stored.checksum())
                        .uploadedBy(userId)
                        .build();
                Photo saved = photoRepository.save(photo);
                uploaded.add(photoMapper.toResponse(saved));
                actualUploadedSize += file.getSize();
            } catch (BusinessException ex) {
                failed.add(new UploadFailure(file.getOriginalFilename(), ex.getErrorCode()));
            } catch (Exception ex) {
                failed.add(new UploadFailure(file.getOriginalFilename(), "UPLOAD_FAILED"));
            }
        }

        event.setPhotoCount(event.getPhotoCount() + uploaded.size());
        event.setTotalSize(event.getTotalSize() + actualUploadedSize);
        eventRepository.save(event);

        user.setStorageUsed(user.getStorageUsed() + actualUploadedSize);
        userRepository.save(user);

        auditLogService.log(userId, "UPLOAD", "EVENT", eventId, null, null, "SUCCESS");
        return new UploadResultResponse(uploaded.size(), failed.size(), uploaded, failed);
    }

    @Override
    public Page<PhotoResponse> listByEvent(Long eventId, Pageable pageable) {
        return photoRepository.findByEventId(eventId, pageable).map(photoMapper::toResponse);
    }

    @Override
    public Page<PhotoResponse> search(String keyword, Pageable pageable, Long requesterId, boolean isAdmin) {
        Page<Photo> page = isAdmin
                ? photoRepository.search(keyword, pageable)
                : photoRepository.searchByOwner(keyword, requesterId, pageable);
        return page.map(photoMapper::toResponse);
    }

    @Override
    @Transactional
    public void delete(Long photoId, Long requesterId, boolean isAdmin) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found"));
        Long eventOwnerId = photo.getEvent().getOwnerId();
        boolean isEventOwner = eventOwnerId.equals(requesterId);
        boolean isUploader = requesterId.equals(photo.getUploadedBy());
        if (!isAdmin && !isEventOwner && !isUploader) {
            throw new AccessDeniedException("Not allowed to delete this photo");
        }

        // Ảnh vật lý nằm trong storage folder của NGƯỜI UPLOAD (xem uploadMultiple), không
        // phải owner của event — chỉ fallback về owner khi ảnh cũ không còn uploadedBy
        // (tài khoản gốc đã bị xoá, FK ON DELETE SET NULL).
        Long fileOwnerUserId = photo.getUploadedBy() != null ? photo.getUploadedBy() : eventOwnerId;
        User fileOwner = userRepository.findById(fileOwnerUserId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        storageService.delete(fileOwner.getStorageFolder(), photo.getPath());
        if (photo.getPreviewPath() != null) {
            storageService.delete(fileOwner.getStorageFolder(), photo.getPreviewPath());
        }
        storageService.delete(fileOwner.getStorageFolder(), com.travelalbum.storage.ThumbnailSupport.thumbPathFor(photo.getPath()));

        Event event = photo.getEvent();
        event.setPhotoCount(Math.max(0, event.getPhotoCount() - 1));
        event.setTotalSize(Math.max(0, event.getTotalSize() - photo.getSize()));
        eventRepository.save(event);

        fileOwner.setStorageUsed(Math.max(0, fileOwner.getStorageUsed() - photo.getSize()));
        userRepository.save(fileOwner);

        photoRepository.delete(photo);
        auditLogService.log(requesterId, "DELETE_PHOTO", "PHOTO", photoId, null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public BatchDeleteResponse deleteBatch(List<Long> photoIds, Long requesterId, boolean isAdmin) {
        if (photoIds.isEmpty()) {
            throw new BusinessException("No photo id provided", "EMPTY_LIST");
        }
        if (photoIds.size() > MAX_BATCH_DELETE) {
            throw new BusinessException("Too many photos in one request", "TOO_MANY_ITEMS");
        }

        List<Long> deletedIds = new ArrayList<>();
        List<BatchDeleteFailure> failures = new ArrayList<>();
        for (Long photoId : photoIds) {
            try {
                // Gọi lại delete() để tái dùng nguyên logic check quyền/dọn storage/audit log —
                // item nào lỗi thì bỏ qua, không làm hỏng cả batch.
                delete(photoId, requesterId, isAdmin);
                deletedIds.add(photoId);
            } catch (NotFoundException ex) {
                failures.add(new BatchDeleteFailure(photoId, "NOT_FOUND"));
            } catch (AccessDeniedException ex) {
                failures.add(new BatchDeleteFailure(photoId, "ACCESS_DENIED"));
            }
        }
        return new BatchDeleteResponse(deletedIds, failures);
    }

    @Override
    @Transactional(readOnly = true)
    public Photo getPhotoForDownload(Long photoId, String token, Long requesterId, boolean isAdmin) {
        Photo photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new NotFoundException("Photo not found"));

        if (hasAccessToPhoto(photo, token, requesterId, isAdmin)) {
            return photo;
        }
        throw new AccessDeniedException("Access denied to this photo");
    }

    @Override
    @Transactional(readOnly = true)
    public List<Photo> getPhotosForZipDownload(List<Long> photoIds, Long requesterId, boolean isAdmin) {
        List<Photo> photos = photoRepository.findByIdIn(photoIds);
        List<Photo> authorizedPhotos = new ArrayList<>();
        for (Photo photo : photos) {
            if (hasAccessToPhoto(photo, null, requesterId, isAdmin)) {
                authorizedPhotos.add(photo);
            }
        }
        if (authorizedPhotos.isEmpty()) {
            throw new AccessDeniedException("No photos authorized for download");
        }
        return authorizedPhotos;
    }

    private boolean hasAccessToPhoto(Photo photo, String token, Long requesterId, boolean isAdmin) {
        if (isAdmin) {
            return true;
        }

        Long eventId = photo.getEvent().getId();
        Long ownerId = photo.getEvent().getOwnerId();

        // 1. Owner
        if (requesterId != null && ownerId.equals(requesterId)) {
            return true;
        }

        // 2. Member
        if (requesterId != null && eventMemberRepository.existsByEventIdAndUserId(eventId, requesterId)) {
            return true;
        }

        // 3. Share token
        if (token != null && !token.isBlank()) {
            return shareLinkRepository.findByTokenAndActiveTrue(token)
                    .filter(l -> l.getExpiredAt() == null || l.getExpiredAt().isAfter(LocalDateTime.now()))
                    .map(l -> l.getEvent().getId().equals(eventId))
                    .orElse(false);
        }

        return false;
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("Empty file", "EMPTY_FILE");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BusinessException("File too large", "FILE_TOO_LARGE");
        }
        if (HeicSupport.isHeicFileName(file.getOriginalFilename())) {
            // HEIC/HEIF (ảnh iPhone gốc): kiểm tra chữ ký ISO BMFF, Content-Type do client gửi không đáng tin
            if (!HeicSupport.hasHeicSignature(readHeader(file))) {
                throw new BusinessException("Invalid HEIC file", "INVALID_TYPE");
            }
            return;
        }
        if (RawImageSupport.isRawFileName(file.getOriginalFilename())) {
            // RAW: trình duyệt thường gửi application/octet-stream nên không tin Content-Type,
            // kiểm tra chữ ký (magic bytes) để file đổi đuôi .cr2 nhưng không phải RAW bị từ chối.
            if (!RawImageSupport.hasRawSignature(readHeader(file))) {
                throw new BusinessException("Invalid RAW file", "INVALID_TYPE");
            }
            return;
        }
        if (!ALLOWED_MIME.contains(file.getContentType())) {
            throw new BusinessException("Invalid file type", "INVALID_TYPE");
        }
    }

    private byte[] readHeader(MultipartFile file) {
        try (java.io.InputStream in = file.getInputStream()) {
            return in.readNBytes(RawImageSupport.HEADER_BYTES);
        } catch (java.io.IOException ex) {
            throw new BusinessException("Cannot read file", "UPLOAD_FAILED");
        }
    }

    /** Cột mime_type NOT NULL, dài 50: RAW thường không có Content-Type hợp lệ. */
    private String resolveMimeType(MultipartFile file) {
        String type = file.getContentType();
        if (type == null || type.isBlank() || type.length() > 50) {
            return "application/octet-stream";
        }
        return type;
    }
}