package com.travelalbum.security;

import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component("eventSecurity")
@RequiredArgsConstructor
public class EventSecurity {

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;

    public boolean isOwner(Long eventId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return eventRepository.findById(eventId)
            .map(e -> e.getOwnerId().equals(principal.getId()))
            .orElse(false);
    }

    /** Owner, member (VIEWER/EDITOR) hoặc bất kỳ người đã đăng nhập nếu album ở chế độ công khai. */
    public boolean canView(Long eventId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return isOwner(eventId, authentication)
            || eventMemberRepository.existsByEventIdAndUserId(eventId, principal.getId())
            || eventRepository.findById(eventId).map(e -> e.isPublicAlbum()).orElse(false);
    }

    /** Owner hoặc member role EDITOR mới upload được. */
    public boolean canUpload(Long eventId, Authentication authentication) {
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return isOwner(eventId, authentication)
            || eventMemberRepository.existsByEventIdAndUserIdAndRole(eventId, principal.getId(), EventMemberRole.EDITOR);
    }
}
