package com.travelalbum.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class PhotoResponse {
    private Long id;
    private String originalName;
    private String url;
    private String thumbnailUrl;
    /** URL ảnh dùng để HIỂN THỊ (RAW -> JPEG xem trước; ảnh thường -> chính ảnh gốc). */
    private String previewUrl;
    /** true nếu file gốc là RAW (trình duyệt không hiển thị được, chỉ tải về). */
    private boolean raw;
    private Long size;
    private Integer width;
    private Integer height;
    private String uploadedBy;
    // EXIF thật (null nếu ảnh không có / ảnh cũ chưa đọc EXIF)
    private String cameraModel;
    private String lensModel;
    private String focalLength;
    private String focalLength35mm;
    private String aperture;
    private String shutterSpeed;
    private Integer iso;
    private LocalDateTime takenAt;
    private LocalDateTime uploadedTime;
}
