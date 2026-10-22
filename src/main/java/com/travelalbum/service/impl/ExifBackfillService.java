package com.travelalbum.service.impl;

import com.travelalbum.entity.Photo;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.storage.ExifInfo;
import com.travelalbum.storage.RawImageSupport;
import com.travelalbum.storage.StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Đọc bù EXIF cho ảnh đã upload TRƯỚC khi có tính năng EXIF (photos.exif_read_at IS NULL).
 * Mỗi ảnh chỉ được đọc đúng 1 lần: dù đọc được hay không, exif_read_at luôn được đánh dấu để
 * không lặp lại ở lần khởi động sau.
 */
@Service
@RequiredArgsConstructor
public class ExifBackfillService {

    private static final Logger log = LoggerFactory.getLogger(ExifBackfillService.class);
    public static final int BATCH_SIZE = 50;

    private final PhotoRepository photoRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    /** Xử lý 1 lô ảnh chưa đọc EXIF. Trả về số ảnh đã xử lý (0 = xong hết). */
    @Transactional
    public int backfillBatch() {
        List<Photo> batch = photoRepository.findTop50ByExifReadAtIsNullOrderByIdAsc();
        for (Photo photo : batch) {
            try {
                backfillOne(photo);
            } catch (RuntimeException ex) {
                log.warn("EXIF backfill failed for photoId={}: {}", photo.getId(), ex.toString());
            } finally {
                photo.setExifReadAt(LocalDateTime.now()); // luôn đánh dấu để không lặp vô hạn
            }
        }
        photoRepository.saveAll(batch);
        return batch.size();
    }

    private void backfillOne(Photo photo) {
        // File nằm trong thư mục của NGƯỜI UPLOAD; ảnh cũ mất uploader thì fallback về owner event.
        Long fileOwnerId = photo.getUploadedBy() != null
                ? photo.getUploadedBy()
                : photoRepository.findOwnerIdByPhotoId(photo.getId()).orElse(null);
        if (fileOwnerId == null) {
            return;
        }
        String folder = userRepository.findById(fileOwnerId).map(u -> u.getStorageFolder()).orElse(null);
        if (folder == null) {
            return;
        }
        ExifInfo exif = storageService.readExif(folder, photo.getPath());
        if (exif == null) {
            return;
        }
        photo.setCameraModel(exif.cameraModel());
        photo.setLensModel(exif.lensModel());
        photo.setFocalLength(exif.focalLength());
        photo.setFocalLength35mm(exif.focalLength35mm());
        photo.setAperture(exif.aperture());
        photo.setShutterSpeed(exif.shutterSpeed());
        photo.setIso(exif.iso());
        photo.setTakenAt(exif.takenAt());
        // Ảnh cũ lưu width/height thô của file: đảo lại nếu EXIF Orientation là xoay 90°
        if (exif.swapsDimensions() && !RawImageSupport.isRawFileName(photo.getOriginalName())
                && photo.getWidth() != null && photo.getHeight() != null) {
            Integer w = photo.getWidth();
            photo.setWidth(photo.getHeight());
            photo.setHeight(w);
        }
    }
}
