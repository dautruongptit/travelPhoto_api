package com.travelalbum.dto.response;

import com.travelalbum.entity.PaymentOrder;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrderResponse {

    private Long id;
    private String orderId;
    private String planId;
    private String planName;
    private long storageGb;
    private int durationDays;
    private long amount;
    private String status;
    private String vnpTransactionNo;
    private String vnpBankCode;
    private String vnpPayDate;
    private LocalDateTime createdAt;

    public static PaymentOrderResponse from(PaymentOrder order) {
        return PaymentOrderResponse.builder()
                .id(order.getId())
                .orderId(order.getOrderId())
                .planId(order.getPlanId())
                .planName(order.getPlanName())
                .storageGb(order.getStorageGb())
                .durationDays(order.getDurationDays())
                .amount(order.getAmount())
                .status(order.getStatus().name())
                .vnpTransactionNo(order.getVnpTransactionNo())
                .vnpBankCode(order.getVnpBankCode())
                .vnpPayDate(order.getVnpPayDate())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
