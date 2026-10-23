package com.travelalbum.mapper;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.entity.Event;
import com.travelalbum.entity.User;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.PhotoRepository;
import com.travelalbum.repository.ShareLinkRepository;
import com.travelalbum.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventMapperTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserMapper userMapper = new UserMapper();
    private final PhotoUrlBuilder photoUrlBuilder = mock(PhotoUrlBuilder.class);
    private final PhotoRepository photoRepository = mock(PhotoRepository.class);
    private final ShareLinkRepository shareLinkRepository = mock(ShareLinkRepository.class);
    private final EventMemberRepository eventMemberRepository = mock(EventMemberRepository.class);
    private final EventMapper eventMapper = new EventMapper(userRepository, userMapper, photoUrlBuilder, photoRepository, shareLinkRepository, eventMemberRepository);

    private Event event(long id, long ownerId) {
        return Event.builder().id(id).ownerId(ownerId).name("Event " + id).build();
    }

    @Test
    void toResponseList_batchesOwnerLookup_insteadOfOnePerEvent() {
        // 3 events, only 2 distinct owners -> phải chỉ có đúng 1 lần gọi findAllById,
        // không được gọi findById lặp lại cho từng event (tránh N+1).
        User owner1 = User.builder().id(1L).fullName("Truong").build();
        User owner2 = User.builder().id(2L).fullName("An").build();
        List<Event> events = List.of(event(10L, 1L), event(11L, 2L), event(12L, 1L));

        when(userRepository.findAllById(anyIterableOf(1L, 2L))).thenReturn(List.of(owner1, owner2));

        List<EventResponse> result = eventMapper.toResponseList(events);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getOwnerName()).isEqualTo("Truong");
        assertThat(result.get(1).getOwnerName()).isEqualTo("An");
        assertThat(result.get(2).getOwnerName()).isEqualTo("Truong");

        verify(userRepository, times(1)).findAllById(anyIterableOf(1L, 2L));
        verify(userRepository, never()).findById(anyLong());
    }

    @Test
    void toResponseList_ownerNameNull_whenOwnerMissing() {
        List<Event> events = List.of(event(10L, 99L));
        when(userRepository.findAllById(anyIterableOf(99L))).thenReturn(List.of());

        List<EventResponse> result = eventMapper.toResponseList(events);

        assertThat(result.get(0).getOwnerName()).isNull();
    }

    @Test
    void toResponseList_returnsEmpty_whenNoEvents() {
        List<EventResponse> result = eventMapper.toResponseList(List.of());

        assertThat(result).isEmpty();
        verify(userRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }

    private static Iterable<Long> anyIterableOf(Long... expected) {
        return org.mockito.ArgumentMatchers.argThat((Iterable<Long> arg) -> {
            var actual = new java.util.HashSet<Long>();
            arg.forEach(actual::add);
            return actual.equals(new java.util.HashSet<>(java.util.Arrays.asList(expected)));
        });
    }

    @Test
    void visibility_private_link_public() {
        Event priv = event(1L, 1L);
        Event linked = event(2L, 1L);
        Event pub = event(3L, 1L);
        pub.setPublicAlbum(true);
        when(userRepository.findAllById(anyIterableOf(1L))).thenReturn(List.of());
        when(shareLinkRepository.findEventIdsWithActiveLink(org.mockito.ArgumentMatchers.anyCollection(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(2L, 3L));

        List<EventResponse> r = eventMapper.toResponseList(List.of(priv, linked, pub));

        assertThat(r.get(0).getVisibility()).isEqualTo("private");
        assertThat(r.get(1).getVisibility()).isEqualTo("link");
        assertThat(r.get(2).getVisibility()).isEqualTo("public"); // công khai ưu tiên hơn link
    }
}
