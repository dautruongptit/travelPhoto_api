package com.travelalbum.repository;

import com.travelalbum.entity.Event;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Event> findByIdAndOwnerId(Long id, Long ownerId);

    Page<Event> findByOwnerId(Long ownerId, Pageable pageable);

    /** Event của chính mình + event mình đã tham gia (member qua link chia sẻ / lời mời). */
    @Query("SELECT e FROM Event e WHERE e.ownerId = :userId "
            + "OR e.id IN (SELECT m.event.id FROM EventMember m WHERE m.userId = :userId)")
    Page<Event> findAccessibleByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE (e.ownerId = :userId "
            + "OR e.id IN (SELECT m.event.id FROM EventMember m WHERE m.userId = :userId)) "
            + "AND lower(e.name) LIKE lower(concat('%', :kw, '%'))")
    Page<Event> searchAccessibleByUser(@Param("kw") String keyword, @Param("userId") Long userId, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE lower(e.name) LIKE lower(concat('%', :kw, '%'))")
    Page<Event> search(@Param("kw") String keyword, Pageable pageable);

    @Query("SELECT e FROM Event e WHERE e.ownerId = :ownerId AND lower(e.name) LIKE lower(concat('%', :kw, '%'))")
    Page<Event> searchByOwner(@Param("kw") String keyword, @Param("ownerId") Long ownerId, Pageable pageable);
}
