package com.travelalbum.security;

import com.travelalbum.entity.Event;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.enums.Role;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventSecurityTest {

    @Mock private EventRepository eventRepository;
    @Mock private EventMemberRepository eventMemberRepository;
    @Mock private Authentication authentication;

    @InjectMocks
    private EventSecurity eventSecurity;

    private void asUser(long userId) {
        lenient().when(authentication.getPrincipal())
                .thenReturn(new UserPrincipal(userId, "u" + userId + "@test.com", Role.USER));
    }

    @Test
    void canView_true_whenOwner() {
        asUser(1L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));

        assertThat(eventSecurity.canView(10L, authentication)).isTrue();
    }

    @Test
    void canView_true_whenMember() {
        asUser(2L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 2L)).thenReturn(true);

        assertThat(eventSecurity.canView(10L, authentication)).isTrue();
    }

    @Test
    void canView_false_whenNeitherOwnerNorMember() {
        asUser(3L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));
        when(eventMemberRepository.existsByEventIdAndUserId(10L, 3L)).thenReturn(false);

        assertThat(eventSecurity.canView(10L, authentication)).isFalse();
    }

    @Test
    void canUpload_true_whenOwner() {
        asUser(1L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));

        assertThat(eventSecurity.canUpload(10L, authentication)).isTrue();
    }

    @Test
    void canUpload_true_whenEditorMember() {
        asUser(2L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));
        when(eventMemberRepository.existsByEventIdAndUserIdAndRole(10L, 2L, EventMemberRole.EDITOR)).thenReturn(true);

        assertThat(eventSecurity.canUpload(10L, authentication)).isTrue();
    }

    @Test
    void canUpload_false_whenViewerMember() {
        asUser(2L);
        when(eventRepository.findById(10L)).thenReturn(Optional.of(Event.builder().id(10L).ownerId(1L).build()));
        when(eventMemberRepository.existsByEventIdAndUserIdAndRole(10L, 2L, EventMemberRole.EDITOR)).thenReturn(false);

        assertThat(eventSecurity.canUpload(10L, authentication)).isFalse();
    }

    @Test
    void canView_true_whenAlbumIsPublic_forAnyLoggedInUser() {
        asUser(3L);
        Event pub = Event.builder().id(10L).ownerId(1L).publicAlbum(true).build();
        when(eventRepository.findById(10L)).thenReturn(Optional.of(pub));

        assertThat(eventSecurity.canView(10L, authentication)).isTrue();
    }

    @Test
    void canUpload_false_whenAlbumIsPublic_butNotOwnerOrEditor() {
        asUser(3L);
        Event pub = Event.builder().id(10L).ownerId(1L).publicAlbum(true).build();
        when(eventRepository.findById(10L)).thenReturn(Optional.of(pub));

        assertThat(eventSecurity.canUpload(10L, authentication)).isFalse();
    }
}
