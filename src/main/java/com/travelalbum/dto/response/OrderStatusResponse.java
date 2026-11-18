package com.travelalbum.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderStatusResponse {

    private String orderId;
    private String status;
    private String planId;
    private String planName;
    private long storageGb;
    private long amount;
    private Long userStorageQuota;
    private String message;
}
