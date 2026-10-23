package com.travelalbum.dto.response;

import com.travelalbum.enums.EventMemberRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareInfoResponse {
    private EventResponse event;
    private EventMemberRole linkRole;
    private boolean requiresAuth;
}
