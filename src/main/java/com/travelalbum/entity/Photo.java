package com.travelalbum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "photos",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_photo_event_name",
        columnNames = {"event_id", "original_name"}
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    /** Path tương đối lưu trên disk, KHÔNG bao giờ trả trực tiếp ra client — xem SEC-01/SEC-02. */
    @Column(nullable = false, length = 500)
    private String path;

    @Column(nullable = false)
    private Long size;

    @Column(name = "mime_type", nullable = false, length = 50)
    private String mimeType;

    private Integer width;
    private Integer height;

    @Column(nullable = false, length = 64)
    private String checksum;

    /** Path tương đối của bản xem trước JPEG — chỉ có với ảnh RAW (xem docs/STORAGE_NAMING.md). */
    @Column(name = "preview_path", length = 500)
    private String previewPath;

    // EXIF thật đọc lúc upload (V12) — tất cả có thể null
    @Column(name = "camera_model", length = 120)
    private String cameraModel;

    @Column(name = "lens_model", length = 120)
    private String lensModel;

    @Column(name = "focal_length", length = 20)
    private String focalLength;

    @Column(length = 20)
    private String aperture;

    @Column(name = "shutter_speed", length = 30)
    private String shutterSpeed;

    private Integer iso;

    @Column(name = "taken_at")
    private LocalDateTime takenAt;

    @Column(name = "uploaded_by")
    private Long uploadedBy;

    @CreationTimestamp
    @Column(name = "uploaded_time", updatable = false)
    private LocalDateTime uploadedTime;
}
