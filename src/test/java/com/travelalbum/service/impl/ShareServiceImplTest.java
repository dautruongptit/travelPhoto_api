package com.travelalbum.service.impl;

import com.travelalbum.dto.response.EventResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.travelalbum.util.TokenHashUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceImplTest {

    @Mock private ShareLinkRepository shareLinkRepository;
    @Mock private EventRepository eventRepository;
    @Mock private PhotoRepository photoRepository;
    @Mock private EventMemberRepository eventMemberRepository;
    @Mock private EventMapper eventMapper;
    @Mock private PhotoMapper photoMapper;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ShareServiceImpl shareService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(shareService, "frontendUrl", "http://triptravel.example.com");
    }

    @Test
    void create_throwsAccessDenied_whenRequesterIsNotOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> shareService.create(1L, 2L, EventMemberRole.VIEWER))
                .isInstanceOf(AccessDeniedException.class);
        verify(shareLinkRepository, never()).save(any());
    }

    @Test
    void create_buildsShareUrlFromFrontendUrl_whenOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(shareLinkRepository.findByTokenHashAndActiveTrue(anyString())).thenReturn(Optional.empty());

        ShareLinkResponse response = shareService.create(1L, 1L, EventMemberRole.VIEWER);

        assertThat(response.isActive()).isTrue();
        assertThat(response.getToken()).isNotBlank();
        assertThat(response.getShareUrl()).isEqualTo("http://triptravel.example.com/share/" + response.getToken());
        assertThat(response.getRole()).isEqualTo(EventMemberRole.VIEWER);
        verify(shareLinkRepository).save(any(ShareLink.class));
        verify(auditLogService).log(eq(1L), eq("SHARE_EVENT"), eq("EVENT"), eq(1L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void create_defaultsToViewerRole_whenRoleNotProvided() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(shareLinkRepository.findByTokenHashAndActiveTrue(anyString())).thenReturn(Optional.empty());

        ShareLinkResponse response = shareService.create(1L, 1L, null);

        assertThat(response.getRole()).isEqualTo(EventMemberRole.VIEWER);
    }

    @Test
    void revoke_throwsAccessDenied_whenNotOwnerAndNotAdmin() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").tokenHash(TokenHashUtil.sha256("tok")).event(event).active(true).build();
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("tok"))).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> shareService.revoke("tok", 2L, false)).isInstanceOf(AccessDeniedException.class);
        assertThat(link.isActive()).isTrue();
    }

    @Test
    void revoke_deactivatesLink_whenOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").tokenHash(TokenHashUtil.sha256("tok")).event(event).active(true).build();
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("tok"))).thenReturn(Optional.of(link));

        shareService.revoke("tok", 1L, false);

        assertThat(link.isActive()).isFalse();
        verify(shareLinkRepository).save(link);
        verify(auditLogService).log(eq(1L), eq("REVOKE_SHARE"), eq("EVENT"), eq(1L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void revoke_succeeds_whenAdminButNotOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").tokenHash(TokenHashUtil.sha256("tok")).event(event).active(true).build();
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("tok"))).thenReturn(Optional.of(link));

        shareService.revoke("tok", 999L, true);

        assertThat(link.isActive()).isFalse();
    }

    @Test
    void getShareInfo_throwsAccessDenied_whenEditorLinkAndNoRequester() {
        Event event = Event.builder().id(1L).name("Da Lat").build();
        ShareLink link = ShareLink.builder().tokenHash(TokenHashUtil.sha256("tok")).event(event)
                .active(true).role(EventMemberRole.EDITOR).build();
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("tok"))).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> shareService.getShareInfo("tok", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getShareInfo_returnsLinkRoleAndEvent_whenViewerLinkAndNoRequester() {
        Event event = Event.builder().id(1L).name("Da Lat").build();
        ShareLink link = ShareLink.builder().tokenHash(TokenHashUtil.sha256("tok")).event(event)
                .active(true).role(EventMemberRole.VIEWER).build();
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("tok"))).thenReturn(Optional.of(link));
        when(eventMapper.toResponse(event)).thenReturn(EventResponse.builder().id(1L).name("Da Lat").build());

        ShareInfoResponse info = shareService.getShareInfo("tok", null);

        assertThat(info.getLinkRole()).isEqualTo(EventMemberRole.VIEWER);
        assertThat(info.getEvent().getId()).isEqualTo(1L);
    }

    @Test
    void getShareInfo_returnsUserRole_whenRequesterIsMember() {
        Event event = Event.builder().id(1L).name("Da Lat").build();
        ShareLink link = ShareLink.builder().tokenHash(TokenHashUtil.sha256("tok")).event(event)
                .active(true).role(EventMemberRole.VIEWER).build();
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("tok"))).thenReturn(Optional.of(link));
        EventResponse resp = EventResponse.builder().id(1L).name("Da Lat").userRole("editor").build();
        when(eventMapper.toResponse(event, 2L)).thenReturn(resp);

        ShareInfoResponse info = shareService.getShareInfo("tok", 2L);

        assertThat(info.getLinkRole()).isEqualTo(EventMemberRole.VIEWER);
        assertThat(info.getEvent().getUserRole()).isEqualTo("editor");
    }

    @Test
    void revokeById_throwsAccessDenied_whenNotOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> shareService.revokeById(1L, 10L, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revokeById_throwsAccessDenied_whenLinkBelongsToOtherEvent() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        Event otherEvent = Event.builder().id(2L).build();
        ShareLink link = ShareLink.builder().id(10L).event(otherEvent).active(true).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(shareLinkRepository.findById(10L)).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> shareService.revokeById(1L, 10L, 1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void revokeById_deactivatesLink_whenOwner() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().id(10L).event(event).active(true).build();
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(shareLinkRepository.findById(10L)).thenReturn(Optional.of(link));

        shareService.revokeById(1L, 10L, 1L);

        assertThat(link.isActive()).isFalse();
        verify(shareLinkRepository).save(link);
        verify(auditLogService).log(eq(1L), eq("REVOKE_SHARE_LINK"), eq("EVENT"), eq(1L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void joinByToken_throwsNotFound_whenTokenUnknown() {
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("bad-token"))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shareService.joinByToken("bad-token", 2L)).isInstanceOf(NotFoundException.class);
        verify(eventMemberRepository, never()).save(any());
    }

    @Test
    void joinByToken_createsMemberWithLinkRole() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").tokenHash(TokenHashUtil.sha256("tok")).event(event).active(true)
                .role(EventMemberRole.EDITOR).createdBy(1L).build();
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("tok"))).thenReturn(Optional.of(link));
        when(eventMemberRepository.existsByEventIdAndUserId(1L, 2L)).thenReturn(false);

        Long returnedEventId = shareService.joinByToken("tok", 2L);

        assertThat(returnedEventId).isEqualTo(1L);
        verify(eventMemberRepository).save(argThat((EventMember m) ->
                m.getUserId().equals(2L) && m.getRole() == EventMemberRole.EDITOR && m.getInvitedBy().equals(1L)));
        verify(auditLogService).log(eq(2L), eq("JOIN_EVENT"), eq("EVENT"), eq(1L), any(), any(), eq("SUCCESS"));
    }

    @Test
    void joinByToken_isIdempotent_whenAlreadyMember() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").tokenHash(TokenHashUtil.sha256("tok")).event(event).active(true)
                .role(EventMemberRole.VIEWER).build();
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("tok"))).thenReturn(Optional.of(link));
        when(eventMemberRepository.existsByEventIdAndUserId(1L, 2L)).thenReturn(true);

        Long returnedEventId = shareService.joinByToken("tok", 2L);

        assertThat(returnedEventId).isEqualTo(1L);
        verify(eventMemberRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void joinByToken_isNoOp_whenOwnerJoinsTheirOwnLink() {
        Event event = Event.builder().id(1L).ownerId(1L).build();
        ShareLink link = ShareLink.builder().token("tok").tokenHash(TokenHashUtil.sha256("tok")).event(event).active(true)
                .role(EventMemberRole.EDITOR).build();
        when(shareLinkRepository.findByTokenHashAndActiveTrue(TokenHashUtil.sha256("tok"))).thenReturn(Optional.of(link));

        Long returnedEventId = shareService.joinByToken("tok", 1L);

        assertThat(returnedEventId).isEqualTo(1L);
        verify(eventMemberRepository, never()).save(any());
        verify(auditLogService, never()).log(any(), any(), any(), any(), any(), any(), any());
    }
}
