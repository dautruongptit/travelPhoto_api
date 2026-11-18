package com.travelalbum.dto.response;

import com.travelalbum.entity.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class UserResponse {
    private Long id;
    private String email;
    private boolean emailVerified;
    private String fullName;
    private String avatarUrl;
    private String role;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Long storageUsed;
    private Long storageQuota;

    public static UserResponse from(User u) {
        return UserResponse.builder()
            .id(u.getId())
            .email(u.getEmail())
            .emailVerified(u.isEmailVerified())
            .fullName(u.getFullName())
            .avatarUrl(u.getAvatarUrl())
            .role(u.getRole().name())
            .lastLoginAt(u.getLastLoginAt())
            .lastLoginIp(u.getLastLoginIp())
            .storageUsed(u.getStorageUsed())
            .storageQuota(u.getStorageQuota())
            .build();
    }
}
