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
public class VnpayReturnResponse {

    private boolean success;
    private String orderId;
    private String planId;
    private String message;
    private Long amount;
    private String transactionNo;
    private String payDate;
}
