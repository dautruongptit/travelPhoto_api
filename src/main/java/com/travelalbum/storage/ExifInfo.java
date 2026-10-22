package com.travelalbum.storage;

import java.time.LocalDateTime;

/** Thông số chụp đọc từ EXIF. Mọi trường đều có thể null (ảnh không có EXIF / thiếu tag). */
public record ExifInfo(
        String cameraModel,
        String lensModel,
        String focalLength,
        String focalLength35mm,
        String aperture,
        String shutterSpeed,
        Integer iso,
        LocalDateTime takenAt,
        Integer orientation
) {
    public boolean isEmpty() {
        return cameraModel == null && lensModel == null && focalLength == null && focalLength35mm == null
                && aperture == null && shutterSpeed == null && iso == null && takenAt == null
                && orientation == null;
    }

    /** EXIF Orientation 5..8 = ảnh được hiển thị xoay 90°, nên width/height lưu trong file bị đảo. */
    public boolean swapsDimensions() {
        return orientation != null && orientation >= 5 && orientation <= 8;
    }
}
