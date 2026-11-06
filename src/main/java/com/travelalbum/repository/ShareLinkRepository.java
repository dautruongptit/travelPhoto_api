package com.travelalbum.repository;

import com.travelalbum.entity.ShareLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {
    Optional<ShareLink> findByTokenHashAndActiveTrue(String tokenHash);
    List<ShareLink> findByEventId(Long eventId);
    List<ShareLink> findByEventIdAndActiveTrue(Long eventId);

    /** Album có ít nhất 1 link chia sẻ còn hiệu lực (active và chưa hết hạn). */
    @Query("SELECT COUNT(s) > 0 FROM ShareLink s WHERE s.event.id = :eventId AND s.active = true "
            + "AND (s.expiredAt IS NULL OR s.expiredAt > :now)")
    boolean existsActiveByEventId(@Param("eventId") Long eventId, @Param("now") LocalDateTime now);

    /** Id các album (trong danh sách) có link chia sẻ còn hiệu lực — 1 query cho cả danh sách. */
    @Query("SELECT DISTINCT s.event.id FROM ShareLink s WHERE s.event.id IN :eventIds AND s.active = true "
            + "AND (s.expiredAt IS NULL OR s.expiredAt > :now)")
    List<Long> findEventIdsWithActiveLink(@Param("eventIds") Collection<Long> eventIds, @Param("now") LocalDateTime now);
}
