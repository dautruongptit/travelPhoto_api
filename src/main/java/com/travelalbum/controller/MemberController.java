package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.response.MemberResponse;
import com.travelalbum.entity.EventMember;
import com.travelalbum.entity.User;
import com.travelalbum.enums.EventMemberRole;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.EventMemberRepository;
import com.travelalbum.repository.EventRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events/{eventId}/members")
@RequiredArgsConstructor
public class MemberController {

    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#eventId, authentication)")
    public ApiResponse<List<MemberResponse>> list(@PathVariable Long eventId) {
        List<EventMember> members = eventMemberRepository.findByEventId(eventId);
        List<Long> userIds = members.stream().map(EventMember::getUserId).toList();
        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<MemberResponse> responses = members.stream().map(m -> {
            User user = usersById.get(m.getUserId());
            return MemberResponse.builder()
                    .id(m.getId())
                    .userId(m.getUserId())
                    .userName(user != null ? user.getFullName() : null)
                    .email(user != null ? user.getEmail() : null)
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .role(m.getRole())
                    .joinedAt(m.getJoinedAt())
                    .build();
        }).toList();
        return ApiResponse.success("OK", responses);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#eventId, authentication)")
    public ApiResponse<Void> remove(@PathVariable Long eventId,
                                     @PathVariable Long userId,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        EventMember member = eventMemberRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        eventMemberRepository.delete(member);
        auditLogService.log(principal.getId(), "REMOVE_MEMBER", "EVENT", eventId, null, null, "SUCCESS");
        return ApiResponse.success("Member removed", null);
    }

    @PutMapping("/{userId}/role")
    @PreAuthorize("hasRole('ADMIN') or @eventSecurity.isOwner(#eventId, authentication)")
    public ApiResponse<Void> changeRole(@PathVariable Long eventId,
                                         @PathVariable Long userId,
                                         @RequestParam EventMemberRole role,
                                         @AuthenticationPrincipal UserPrincipal principal) {
        EventMember member = eventMemberRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new NotFoundException("Member not found"));
        member.setRole(role);
        eventMemberRepository.save(member);
        auditLogService.log(principal.getId(), "CHANGE_MEMBER_ROLE", "EVENT", eventId, null, null, "SUCCESS");
        return ApiResponse.success("Role updated", null);
    }
}
