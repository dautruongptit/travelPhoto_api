package com.travelalbum.repository;

import com.travelalbum.entity.EventMember;
import com.travelalbum.enums.EventMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventMemberRepository extends JpaRepository<EventMember, Long> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    boolean existsByEventIdAndUserIdAndRole(Long eventId, Long userId, EventMemberRole role);

    Optional<EventMember> findByEventIdAndUserId(Long eventId, Long userId);

    List<EventMember> findByEventId(Long eventId);
}
