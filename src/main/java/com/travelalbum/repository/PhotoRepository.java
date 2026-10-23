package com.travelalbum.repository;

import com.travelalbum.entity.Photo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

    @Query("SELECT COUNT(p) > 0 FROM Photo p WHERE p.event.id = :eventId AND p.originalName = :name AND p.deletedAt IS NULL")
    boolean existsByEventIdAndOriginalName(@Param("eventId") Long eventId, @Param("name") String name);

    @Query("SELECT p.id FROM Photo p WHERE p.event.id = :eventId AND p.deletedAt IS NULL ORDER BY p.id DESC")
    List<Long> findPhotoIdsByEventId(@Param("eventId") Long eventId, Pageable pageable);

    /** Ảnh chưa đọc EXIF (upload trước khi có tính năng EXIF) — dùng cho ExifBackfillService. */
    List<Photo> findTop50ByExifReadAtIsNullOrderByIdAsc();

    Optional<Photo> findByChecksumAndEventId(String checksum, Long eventId);

    /**
     * Lấy ownerId qua join JPQL thay vì Photo.getEvent().getOwnerId() — Photo.event là
     * quan hệ LAZY, nếu gọi ngoài transaction (vd trong Controller sau khi findById() đã
     * đóng session) sẽ ném LazyInitializationException.
     */
    @Query("SELECT p.event.ownerId FROM Photo p WHERE p.id = :photoId")
    Optional<Long> findOwnerIdByPhotoId(@Param("photoId") Long photoId);

    @Query("SELECT p FROM Photo p WHERE p.event.id = :eventId AND p.deletedAt IS NULL")
    Page<Photo> findByEventId(@Param("eventId") Long eventId, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.id IN :ids AND p.deletedAt IS NULL")
    List<Photo> findByIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT COUNT(p) FROM Photo p WHERE p.event.id = :eventId AND p.deletedAt IS NULL")
    long countByEvent(@Param("eventId") Long eventId);

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND lower(p.originalName) LIKE lower(concat('%', :kw, '%'))")
    Page<Photo> search(@Param("kw") String keyword, Pageable pageable);

    @Query("SELECT p FROM Photo p WHERE p.deletedAt IS NULL AND p.event.ownerId = :ownerId AND lower(p.originalName) LIKE lower(concat('%', :kw, '%'))")
    Page<Photo> searchByOwner(@Param("kw") String keyword, @Param("ownerId") Long ownerId, Pageable pageable);

    long countByUploadedTimeBetween(LocalDateTime from, LocalDateTime to);

    @Query(value = "SELECT DATE_FORMAT(uploaded_time, '%Y-%m') AS month, COUNT(*) AS cnt "
            + "FROM photos "
            + "WHERE uploaded_time >= :from "
            + "GROUP BY month "
            + "ORDER BY month",
            nativeQuery = true)
    List<Object[]> countUploadsGroupedByMonth(@Param("from") LocalDateTime from);
}