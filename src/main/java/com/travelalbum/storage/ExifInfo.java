package com.travelalbum.storage;

import java.time.LocalDateTime;

/** Thông số chụp đọc từ EXIF. Mọi trường đều có thể null (ảnh không có EXIF / thiếu tag). */
public record ExifInfo(
        String cameraModel,
        String lensModel,
        String focalLength,
        String aperture,
        String shutterSpeed,
        Integer iso,
        LocalDateTime takenAt
) {
    public boolean isEmpty() {
        return cameraModel == null && lensModel == null && focalLength == null
                && aperture == null && shutterSpeed == null && iso == null && takenAt == null;
    }
}
