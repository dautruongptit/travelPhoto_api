package com.travelalbum.entity;

import com.travelalbum.enums.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payment_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true, length = 64)
    private String orderId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "plan_id", nullable = false, length = 50)
    private String planId;

    @Column(name = "plan_name", nullable = false, length = 100)
    private String planName;

    @Column(name = "storage_gb", nullable = false)
    private long storageGb;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "vnp_transaction_no", length = 64)
    private String vnpTransactionNo;

    @Column(name = "vnp_bank_code", length = 50)
    private String vnpBankCode;

    @Column(name = "vnp_card_type", length = 50)
    private String vnpCardType;

    @Column(name = "vnp_pay_date", length = 20)
    private String vnpPayDate;

    @Column(name = "vnp_response_code", length = 20)
    private String vnpResponseCode;

    @Column(name = "vnp_transaction_status", length = 20)
    private String vnpTransactionStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
