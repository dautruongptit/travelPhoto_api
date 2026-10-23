package com.travelalbum.dto.response;

import com.travelalbum.enums.EventMemberRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MemberResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String email;
    private String avatarUrl;
    private EventMemberRole role;
    private LocalDateTime joinedAt;
}
