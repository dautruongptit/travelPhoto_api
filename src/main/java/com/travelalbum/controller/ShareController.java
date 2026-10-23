package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.EventResponse;
import com.travelalbum.dto.response.PhotoResponse;
import com.travelalbum.dto.response.ShareInfoResponse;
import com.travelalbum.dto.response.ShareLinkResponse;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.ShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;

    @PostMapping("/api/events/{id}/share")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<ShareLinkResponse> create(@PathVariable Long id,
                                                  @RequestParam(value = "role", required = false) EventMemberRole role,
                                                  @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Share link created", shareService.create(id, principal.getId(), role));
    }

    @GetMapping("/api/events/{id}/shares")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<List<ShareLinkResponse>> listByEvent(@PathVariable Long id,
                                                             @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("OK", shareService.listByEvent(id, principal.getId()));
    }

    @DeleteMapping("/api/events/{id}/shares/{linkId}")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#id, authentication)")
    public ApiResponse<Void> revokeById(@PathVariable Long id,
                                         @PathVariable Long linkId,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        shareService.revokeById(id, linkId, principal.getId());
        return ApiResponse.success("Share link revoked", null);
    }

    @GetMapping("/api/share/{token}")
    public ApiResponse<ShareInfoResponse> viewByToken(@PathVariable String token,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        Long requesterId = principal != null ? principal.getId() : null;
        return ApiResponse.success("OK", shareService.getShareInfo(token, requesterId));
    }

    @GetMapping("/api/share/{token}/photos")
    public ApiResponse<Page<PhotoResponse>> listByToken(@PathVariable String token, Pageable pageable) {
        return ApiResponse.success("OK", shareService.listPhotosByToken(token, pageable));
    }

    @PostMapping("/api/share/{token}/join")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Long> join(@PathVariable String token, @AuthenticationPrincipal UserPrincipal principal) {
        return ApiResponse.success("Joined event", shareService.joinByToken(token, principal.getId()));
    }

    @DeleteMapping("/api/share/{token}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ApiResponse<Void> revoke(@PathVariable String token, @AuthenticationPrincipal UserPrincipal principal) {
        shareService.revoke(token, principal.getId(), principal.isAdmin());
        return ApiResponse.success("Share link revoked", null);
    }
}
