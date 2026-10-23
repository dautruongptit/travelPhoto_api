package com.travelalbum.mapper;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.EventMember;
import com.travelalbum.entity.User;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.ShareLinkRepository;
import com.travelalbum.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class EventMapper {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PhotoUrlBuilder photoUrlBuilder;
    private final PhotoRepository photoRepository;
    private final ShareLinkRepository shareLinkRepository;
    private final EventMemberRepository eventMemberRepository;

    public EventResponse toResponse(Event event) {
        return toResponse(event, (Long) null);
    }

    public EventResponse toResponse(Event event, Long requesterId) {
        String ownerName = userRepository.findById(event.getOwnerId())
                .map(userMapper::toDisplayName)
                .orElse(null);
        boolean hasActiveLink = !event.isPublicAlbum()
                && shareLinkRepository.existsActiveByEventId(event.getId(), LocalDateTime.now());
        String userRole = resolveUserRole(event, requesterId);
        return toResponse(event, ownerName, hasActiveLink, userRole);
    }

    public List<EventResponse> toResponseList(List<Event> events) {
        return toResponseList(events, null);
    }

    public List<EventResponse> toResponseList(List<Event> events, Long requesterId) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> ownerIds = events.stream().map(Event::getOwnerId).distinct().toList();
        Map<Long, String> ownerNameById = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, userMapper::toDisplayName));
        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Set<Long> withActiveLink = new HashSet<>(shareLinkRepository.findEventIdsWithActiveLink(eventIds, LocalDateTime.now()));
        return events.stream()
                .map(event -> {
                    String userRole = resolveUserRole(event, requesterId);
                    return toResponse(event, ownerNameById.get(event.getOwnerId()), withActiveLink.contains(event.getId()), userRole);
                })
                .toList();
    }

    /** Tối đa 3 ảnh mới nhất của album -> URL thumbnail (album trống thì không query). */
    private List<String> previewUrls(Event event) {
        if (event.getPhotoCount() <= 0 || event.getId() == null) {
            return List.of();
        }
        return photoRepository.findPhotoIdsByEventId(event.getId(), PageRequest.of(0, 3)).stream()
                .map(photoUrlBuilder::buildThumbnailUrl)
                .toList();
    }

    private static String visibilityOf(Event event, boolean hasActiveLink) {
        if (event.isPublicAlbum()) {
            return "public";
        }
        return hasActiveLink ? "link" : "private";
    }

    private String resolveUserRole(Event event, Long requesterId) {
        if (requesterId == null) {
            return null;
        }
        if (event.getOwnerId().equals(requesterId)) {
            return "owner";
        }
        return eventMemberRepository.findByEventIdAndUserId(event.getId(), requesterId)
                .map(member -> member.getRole() == EventMemberRole.EDITOR ? "editor" : "viewer")
                .orElse(null);
    }

    private EventResponse toResponse(Event event, String ownerName, boolean hasActiveLink, String userRole) {
        String coverImageUrl = event.getCoverImagePath() != null
                ? photoUrlBuilder.buildEventCoverUrl(event.getId())
                : null;
        return EventResponse.builder()
                .id(event.getId())
                .name(event.getName())
                .description(event.getDescription())
                .ownerName(ownerName)
                .ownerId(event.getOwnerId())
                .startDate(event.getStartDate())
                .endDate(event.getEndDate())
                .location(event.getLocation())
                .coverImageUrl(coverImageUrl)
                .visibility(visibilityOf(event, hasActiveLink))
                .previewImageUrls(previewUrls(event))
                .photoCount(event.getPhotoCount())
                .totalSize(event.getTotalSize())
                .createdAt(event.getCreatedAt())
                .userRole(userRole)
                .build();
    }
}