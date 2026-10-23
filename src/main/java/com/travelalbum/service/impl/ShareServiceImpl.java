package com.travelalbum.service.impl;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.ShareInfoResponse;
import com.travelalbum.dto.response.ShareLinkResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.EventMember;
import com.travelalbum.entity.ShareLink;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.mapper.EventMapper;
import com.travelalbum.mapper.PhotoMapper;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.ShareLinkRepository;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.ShareService;
import com.travelalbum.util.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShareServiceImpl implements ShareService {

    private final ShareLinkRepository shareLinkRepository;
    private final EventRepository eventRepository;
    private final PhotoRepository photoRepository;
    private final EventMemberRepository eventMemberRepository;
    private final EventMapper eventMapper;
    private final PhotoMapper photoMapper;
    private final AuditLogService auditLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public ShareLinkResponse create(Long eventId, Long userId, EventMemberRole role) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(userId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        EventMemberRole linkRole = role != null ? role : EventMemberRole.VIEWER;
        String token = generateUniqueToken();
        String tokenHash = TokenHashUtil.sha256(token);
        LocalDateTime expiredAt = LocalDateTime.now().plusDays(DEFAULT_LINK_TTL_DAYS);
        ShareLink link = ShareLink.builder()
            .event(event)
            .token(null)
            .tokenHash(tokenHash)
            .createdBy(userId)
            .active(true)
            .role(linkRole)
            .expiredAt(expiredAt)
            .build();
        shareLinkRepository.save(link);
        auditLogService.log(userId, "SHARE_EVENT", "EVENT", eventId, null, null, "SUCCESS");

        return ShareLinkResponse.builder()
            .token(token)
            .shareUrl(frontendUrl + "/share/" + token)
            .active(true)
            .role(linkRole)
            .expiredAt(expiredAt)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ShareInfoResponse getShareInfo(String token, Long requesterId) {
        ShareLink link = findActiveLink(token);
        EventResponse event = requesterId != null
                ? eventMapper.toResponse(link.getEvent(), requesterId)
                : eventMapper.toResponse(link.getEvent());
        return ShareInfoResponse.builder()
                .event(event)
                .linkRole(link.getRole())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse getEventByToken(String token) {
        // @Transactional bắt buộc ở đây: ShareLink.event là quan hệ LAZY, eventMapper
        // đọc nhiều field của Event (name, description, startDate...) chứ không chỉ id
        // — nếu không có transaction mở sẵn, proxy sẽ ném LazyInitializationException
        // (open-in-view: false, mỗi repository call tự đóng session ngay khi trả về).
        ShareLink link = findActiveLink(token);
        return eventMapper.toResponse(link.getEvent());
    }

    @Override
    public Page<PhotoResponse> listPhotosByToken(String token, Pageable pageable) {
        ShareLink link = findActiveLink(token);
        return photoRepository.findByEventId(link.getEvent().getId(), pageable).map(photoMapper::toResponse);
    }

    @Override
    @Transactional
    public void revoke(String token, Long requesterId, boolean isAdmin) {
        String tokenHash = TokenHashUtil.sha256(token);
        ShareLink link = shareLinkRepository.findByTokenHashAndActiveTrue(tokenHash)
            .orElseThrow(() -> new NotFoundException("Share link not found"));
        if (!isAdmin && !link.getEvent().getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        link.setActive(false);
        shareLinkRepository.save(link);
        auditLogService.log(requesterId, "REVOKE_SHARE", "EVENT", link.getEvent().getId(), null, null, "SUCCESS");
    }

    @Override
    @Transactional
    public Long joinByToken(String token, Long userId) {
        ShareLink link = findActiveLink(token);
        Long eventId = link.getEvent().getId();
        // Owner tự bấm link share của chính mình thì không tạo membership (họ đã có full quyền)
        // — cùng tinh thần với guard CANNOT_INVITE_SELF trong EventInviteServiceImpl.invite.
        if (link.getEvent().getOwnerId().equals(userId)) {
            return eventId;
        }
        if (eventMemberRepository.existsByEventIdAndUserId(eventId, userId)) {
            return eventId;
        }
        EventMember member = EventMember.builder()
            .event(link.getEvent())
            .userId(userId)
            .role(link.getRole())
            .invitedBy(link.getCreatedBy())
            .build();
        eventMemberRepository.save(member);
        auditLogService.log(userId, "JOIN_EVENT", "EVENT", eventId, null, null, "SUCCESS");
        return eventId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ShareLinkResponse> listByEvent(Long eventId, Long requesterId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        return shareLinkRepository.findByEventIdAndActiveTrue(eventId).stream()
            .map(link -> ShareLinkResponse.builder()
                .id(link.getId())
                .shareUrl(frontendUrl + "/share/[hidden]")
                .active(link.isActive())
                .role(link.getRole())
                .expiredAt(link.getExpiredAt())
                .createdAt(link.getCreatedAt())
                .build())
            .toList();
    }

    private ShareLink findActiveLink(String token) {
        String tokenHash = TokenHashUtil.sha256(token);
        return shareLinkRepository.findByTokenHashAndActiveTrue(tokenHash)
            .filter(l -> l.getExpiredAt() == null || l.getExpiredAt().isAfter(LocalDateTime.now()))
            .orElseThrow(() -> new NotFoundException("Share link not found or expired"));
    }

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_LENGTH = 32;
    private static final long DEFAULT_LINK_TTL_DAYS = 30;

    @Override
    @Transactional
    public void revokeById(Long eventId, Long linkId, Long requesterId) {
        Event event = eventRepository.findById(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));
        if (!event.getOwnerId().equals(requesterId)) {
            throw new AccessDeniedException("Not the owner of this event");
        }
        ShareLink link = shareLinkRepository.findById(linkId)
            .orElseThrow(() -> new NotFoundException("Share link not found"));
        if (!link.getEvent().getId().equals(eventId)) {
            throw new AccessDeniedException("Link does not belong to this event");
        }
        link.setActive(false);
        shareLinkRepository.save(link);
        auditLogService.log(requesterId, "REVOKE_SHARE_LINK", "EVENT", eventId, null, null, "SUCCESS");
    }

    private String generateUniqueToken() {
        String token;
        do {
            token = RandomStringUtils.random(TOKEN_LENGTH, 0, 0, true, true, null, SECURE_RANDOM);
        } while (shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256(token)).isPresent());
        return token;
    }
}
