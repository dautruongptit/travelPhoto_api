package com.travelalbum.mapper;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.User;
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

    public EventResponse toResponse(Event event) {
        String ownerName = userRepository.findById(event.getOwnerId())
                .map(userMapper::toDisplayName)
                .orElse(null);
        boolean hasActiveLink = !event.isPublicAlbum()
                && shareLinkRepository.existsActiveByEventId(event.getId(), LocalDateTime.now());
        return toResponse(event, ownerName, hasActiveLink);
    }

    /**
     * Map cả danh sách event trong 1 lần gọi, chỉ query owner 1 lần cho cả danh sách
     * (thay vì 1 query/event như toResponse(Event) — tránh N+1 khi list/search events).
     */
    public List<EventResponse> toResponseList(List<Event> events) {
        if (events.isEmpty()) {
            return List.of();
        }
        List<Long> ownerIds = events.stream().map(Event::getOwnerId).distinct().toList();
        Map<Long, String> ownerNameById = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, userMapper::toDisplayName));
        List<Long> eventIds = events.stream().map(Event::getId).toList();
        Set<Long> withActiveLink = new HashSet<>(shareLinkRepository.findEventIdsWithActiveLink(eventIds, LocalDateTime.now()));
        return events.stream()
                .map(event -> toResponse(event, ownerNameById.get(event.getOwnerId()), withActiveLink.contains(event.getId())))
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

    private EventResponse toResponse(Event event, String ownerName, boolean hasActiveLink) {
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
                .build();
    }
}