package com.travelalbum.dto.response;

import com.travelalbum.enums.EventMemberRole;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ShareLinkResponse {
    private Long id;
    private String token;
    private String shareUrl;
    private LocalDateTime expiredAt;
    private boolean active;
    private EventMemberRole role;
    private LocalDateTime createdAt;
}
