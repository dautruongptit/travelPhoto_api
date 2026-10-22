package com.travelalbum.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "events", uniqueConstraints = @UniqueConstraint(name = "uq_event_name", columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** Tên thư mục Event đã format sẵn (SEC-26), KHÔNG chứa path tuyệt đối — xem SEC-11. */
    @Column(name = "storage_folder", nullable = false)
    private String storageFolder;

    /** Ngày bắt đầu sự kiện — dùng làm phần yyyyMMdd trong tên thư mục (SEC-26). */
    @Column(name = "start_date")
    private LocalDate startDate;

    /** Ngày kết thúc sự kiện — tuỳ chọn, chỉ mang tính hiển thị, KHÔNG dùng trong tên thư mục (SEC-27). */
    @Column(name = "end_date")
    private LocalDate endDate;

    /** Địa điểm sự kiện, vd "Đà Lạt, Lâm Đồng" (SEC-27). */
    @Column(length = 255)
    private String location;

    /** Path tương đối của ảnh bìa trong storage, giống Photo.path — KHÔNG lộ path tuyệt đối (SEC-27). */
    @Column(name = "cover_image_path", length = 500)
    private String coverImagePath;

    /** Chủ album bật "Công khai": mọi người đã đăng nhập xem được (chỉ đọc, không sửa/xóa). */
    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean publicAlbum = false;

    @Column(name = "photo_count", nullable = false)
    @Builder.Default
    private int photoCount = 0;

    @Column(name = "total_size", nullable = false)
    @Builder.Default
    private long totalSize = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}