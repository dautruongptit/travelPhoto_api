package com.travelalbum.controller;

import com.travelalbum.audit.Auditable;
import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.BatchDeleteResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.UploadResultResponse;
import com.travelalbum.entity.Photo;
import com.travelalbum.entity.User;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.PhotoService;
import com.travelalbum.storage.StorageService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequiredArgsConstructor
public class PhotoController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PhotoController.class);

    private final PhotoService photoService;
    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    @Auditable(action = "UPLOAD", targetType = "EVENT")
    @PostMapping(value = "/api/events/{eventId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.canUpload(#eventId, authentication)")
    public ApiResponse<UploadResultResponse> upload(@PathVariable Long eventId,
                                                    @RequestParam("files") org.springframework.web.multipart.MultipartFile[] files,
                                                    @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Upload processed",
                photoService.uploadMultiple(eventId, files, principal.getId()));
    }

    @GetMapping("/api/events/{eventId}/photos")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.canView(#eventId, authentication)")
    public ApiResponse<Page<PhotoResponse>> listByEvent(@PathVariable Long eventId, Pageable pageable) {
        return ApiResponse.success("OK", photoService.listByEvent(eventId, pageable));
    }

    // Quyền được check đầy đủ (admin/owner/uploader) bên trong PhotoService.delete —
    // @PreAuthorize chỉ cần yêu cầu đăng nhập. @photoSecurity.isOwner chỉ biết owner của
    // event nên sẽ chặn nhầm EDITOR xoá đúng ảnh mình đã upload.
    @Auditable(action = "DELETE_PHOTO", targetType = "PHOTO")
    @DeleteMapping("/api/photos/{id}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> delete(@PathVariable Long id, @AuthenticationPrincipal UserPrincipal principal) {
        photoService.delete(id, principal.getId(), principal.isAdmin());
        return ApiResponse.success("Photo deleted", null);
    }

    // Quyền sở hữu của từng photo được check bên trong PhotoService.deleteBatch (item nào
    // không phải của mình thì bỏ qua, không throw cho cả request) nên chỉ cần yêu cầu đăng nhập.
    @DeleteMapping("/api/photos")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<BatchDeleteResponse> deleteBatch(@RequestParam List<Long> ids,
                                                        @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Batch delete processed",
                photoService.deleteBatch(ids, principal.getId(), principal.isAdmin()));
    }

    @Auditable(action = "DOWNLOAD", targetType = "PHOTO")
    @GetMapping("/api/photos/download/{id}")
    public void download(@PathVariable Long id,
                         @RequestParam(required = false) String token,
                         @AuthenticationPrincipal UserPrincipal principal,
                         HttpServletResponse response) throws IOException {
        Long requesterId = principal != null ? principal.getId() : null;
        boolean isAdmin = principal != null && principal.isAdmin();

        Photo photo = photoService.getPhotoForDownload(id, token, requesterId, isAdmin);
        String parentFolder = resolveStorageFolder(photo);
        Resource resource = storageService.load(parentFolder, photo.getPath());
        // File có bản ghi trong DB nhưng không còn trên đĩa (vd ảnh upload từ môi trường khác
        // dùng chung DB, hoặc sai STORAGE_HOST_PATH) -> trả 404 rõ ràng thay vì 500.
        if (!resource.exists()) {
            log.warn("Photo file missing on disk: photoId={}, path={}", id, photo.getPath());
            throw new NotFoundException("Photo file not found in storage");
        }
        response.setContentType(photo.getMimeType());
        // filename* (RFC 6266) để giữ đúng tên gốc có dấu tiếng Việt / ký tự đặc biệt và
        // chống chèn header qua tên file.
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(photo.getOriginalName(), StandardCharsets.UTF_8).build().toString());
        try (InputStream in = resource.getInputStream(); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    /**
     * Ảnh để HIỂN THỊ trên web: RAW -> JPEG xem trước đã tạo lúc upload; ảnh thường -> chính
     * file gốc. Cùng quy tắc quyền với download (owner/member/share token).
     */
    @GetMapping("/api/photos/{id}/preview")
    public void preview(@PathVariable Long id,
                        @RequestParam(required = false) String token,
                        @AuthenticationPrincipal UserPrincipal principal,
                        HttpServletResponse response) throws IOException {
        Long requesterId = principal != null ? principal.getId() : null;
        boolean isAdmin = principal != null && principal.isAdmin();

        Photo photo = photoService.getPhotoForDownload(id, token, requesterId, isAdmin);
        String parentFolder = resolveStorageFolder(photo);

        boolean raw = com.travelalbum.storage.RawImageSupport.isRawFileName(photo.getOriginalName());
        String relativePath = raw ? photo.getPreviewPath() : photo.getPath();
        if (relativePath == null) {
            throw new NotFoundException("No preview available for this photo");
        }
        Resource resource = storageService.load(parentFolder, relativePath);
        if (!resource.exists()) {
            log.warn("Photo preview missing on disk: photoId={}, path={}", id, relativePath);
            throw new NotFoundException("Photo file not found in storage");
        }
        response.setContentType(raw ? MediaType.IMAGE_JPEG_VALUE : photo.getMimeType());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");
        try (InputStream in = resource.getInputStream(); OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }

    @Auditable(action = "DOWNLOAD", targetType = "PHOTO")
    @PostMapping("/api/photos/download-zip")
    public void downloadZip(@RequestParam List<Long> ids,
                            @AuthenticationPrincipal UserPrincipal principal,
                            HttpServletResponse response) throws IOException {
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=photos.zip");

        Long requesterId = principal != null ? principal.getId() : null;
        boolean isAdmin = principal != null && principal.isAdmin();

        List<Photo> photos = photoService.getPhotosForZipDownload(ids, requesterId, isAdmin);
        try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
            Set<String> seenNames = new HashSet<>();
            for (Photo photo : photos) {
                String entryName = photo.getOriginalName();
                if (seenNames.contains(entryName)) {
                    int dotIndex = entryName.lastIndexOf('.');
                    if (dotIndex != -1) {
                        String name = entryName.substring(0, dotIndex);
                        String ext = entryName.substring(dotIndex);
                        entryName = name + "_" + photo.getId() + ext;
                    } else {
                        entryName = entryName + "_" + photo.getId();
                    }
                }
                seenNames.add(entryName);

                String parentFolder = resolveStorageFolder(photo);
                Resource resource = storageService.load(parentFolder, photo.getPath());
                if (!resource.exists()) {
                    log.warn("Photo file missing on disk, skip in zip: photoId={}, path={}", photo.getId(), photo.getPath());
                    continue;
                }
                zos.putNextEntry(new ZipEntry(entryName));
                try (InputStream is = resource.getInputStream()) {
                    is.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }

    private String resolveStorageFolder(Photo photo) {
        // Ảnh nằm trong storage folder của người upload (xem PhotoServiceImpl.uploadMultiple),
        // không phải owner của event. photo.getUploadedBy() là cột thường (không LAZY) nên đọc
        // trực tiếp an toàn; chỉ fallback sang owner qua join JPQL khi ảnh cũ không có uploader
        // (tài khoản gốc đã bị xoá, FK ON DELETE SET NULL).
        Long fileOwnerUserId = photo.getUploadedBy() != null
                ? photo.getUploadedBy()
                : photoRepository.findOwnerIdByPhotoId(photo.getId())
                        .orElseThrow(() -> new NotFoundException("Event not found"));
        User user = userRepository.findById(fileOwnerUserId)
                .orElseThrow(() -> new NotFoundException("Owner not found"));
        return user.getStorageFolder();
    }
}