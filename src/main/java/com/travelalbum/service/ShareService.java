package com.travelalbum.service;

import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.ShareInfoResponse;
import com.travelalbum.dto.response.ShareLinkResponse;
import com.travelalbum.enums.EventMemberRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ShareService {
    ShareLinkResponse create(Long eventId, Long userId, EventMemberRole role);
    EventResponse getEventByToken(String token);
    ShareInfoResponse getShareInfo(String token, Long requesterId);
    Page<PhotoResponse> listPhotosByToken(String token, Long requesterId, Pageable pageable);
    void revoke(String token, Long requesterId, boolean isAdmin);
    Long joinByToken(String token, Long userId);
    List<ShareLinkResponse> listByEvent(Long eventId, Long requesterId);
    void revokeById(Long eventId, Long linkId, Long requesterId);
}
