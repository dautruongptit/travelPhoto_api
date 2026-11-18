package com.travelalbum.service.impl;

import com.travelalbum.config.VnpayProperties;
import com.travelalbum.dto.request.CreatePaymentRequest;
import com.travelalbum.dto.response.OrderStatusResponse;
import com.travelalbum.dto.response.PaymentOrderResponse;
import com.travelalbum.dto.response.PaymentUrlResponse;
import com.travelalbum.dto.response.VnpayIpnResponse;
import com.travelalbum.dto.response.VnpayReturnResponse;
import com.travelalbum.entity.PaymentOrder;
import com.travelalbum.entity.User;
import com.travelalbum.enums.PaymentStatus;
import com.travelalbum.enums.StoragePlanDefinition;
import com.travelalbum.exception.BusinessException;
import com.travelalbum.exception.NotFoundException;
import com.travelalbum.repository.PaymentOrderRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.service.PaymentService;
import com.travelalbum.util.VnpayUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final UserRepository userRepository;
    private final VnpayProperties vnpayProperties;
    private final AuditLogService auditLogService;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    @Transactional
    public PaymentUrlResponse createPayment(UserPrincipal principal, CreatePaymentRequest request, HttpServletRequest httpRequest) {
        if (!StringUtils.hasText(vnpayProperties.getTmnCode()) || !StringUtils.hasText(vnpayProperties.getHashSecret())) {
            throw new BusinessException("Cấu hình VNPay chưa hoàn tất. Vui lòng thiết lập VNPAY_TMN_CODE và VNPAY_HASH_SECRET.", "VNPAY_CONFIG_MISSING");
        }

        StoragePlanDefinition plan = StoragePlanDefinition.fromId(request.getPlanId())
                .orElseThrow(() -> new BusinessException("Gói dung lượng không hợp lệ: " + request.getPlanId(), "INVALID_PLAN_ID"));

        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new NotFoundException("Người dùng không tồn tại"));

        String orderId = "PT" + System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(1000, 10000);

        PaymentOrder order = PaymentOrder.builder()
                .orderId(orderId)
                .user(user)
                .planId(plan.getId())
                .planName(plan.getLabel())
                .storageGb(plan.getStorageGb())
                .durationDays(plan.getDurationDays())
                .amount(plan.getPriceVnd())
                .status(PaymentStatus.PENDING)
                .build();
        paymentOrderRepository.save(order);

        LocalDateTime now = LocalDateTime.now(VN_ZONE);
        String createDate = now.format(DATE_FORMATTER);
        String expireDate = now.plusMinutes(15).format(DATE_FORMATTER);

        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnpayProperties.getTmnCode().trim());
        vnpParams.put("vnp_Amount", String.valueOf(plan.getPriceVnd() * 100L));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", orderId);
        vnpParams.put("vnp_OrderInfo", "Thanh toan don hang " + orderId + " - Goi " + plan.getId());
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnpayProperties.getReturnUrl().trim());
        vnpParams.put("vnp_IpAddr", VnpayUtils.getIpAddress(httpRequest));
        vnpParams.put("vnp_CreateDate", createDate);
        vnpParams.put("vnp_ExpireDate", expireDate);

        String paymentUrl = VnpayUtils.buildPaymentUrl(vnpayProperties.getPayUrl(), vnpayProperties.getHashSecret().trim(), vnpParams);

        log.info("Tạo giao dịch VNPay orderId={} cho user={} số tiền={}", orderId, user.getEmail(), plan.getPriceVnd());
        return PaymentUrlResponse.builder()
                .paymentUrl(paymentUrl)
                .orderId(orderId)
                .build();
    }

    @Override
    @Transactional
    public VnpayReturnResponse processReturn(Map<String, String> params) {
        if (!VnpayUtils.verifySignature(params, vnpayProperties.getHashSecret())) {
            log.warn("VNPay return: Chữ ký không hợp lệ. Params: {}", params);
            return VnpayReturnResponse.builder()
                    .success(false)
                    .orderId(params.get("vnp_TxnRef"))
                    .message("Chữ ký không hợp lệ")
                    .build();
        }

        String orderId = params.get("vnp_TxnRef");
        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId).orElse(null);
        if (order == null) {
            log.warn("VNPay return: Không tìm thấy đơn hàng: {}", orderId);
            return VnpayReturnResponse.builder()
                    .success(false)
                    .orderId(orderId)
                    .message("Không tìm thấy thông tin đơn hàng")
                    .build();
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        String cardType = params.get("vnp_CardType");
        String payDate = params.get("vnp_PayDate");

        boolean isSuccess = "00".equals(responseCode) && ("00".equals(transactionStatus) || transactionStatus == null);

        if (isSuccess) {
            if (order.getStatus() == PaymentStatus.PENDING) {
                order.setStatus(PaymentStatus.SUCCESS);
                order.setVnpTransactionNo(transactionNo);
                order.setVnpBankCode(bankCode);
                order.setVnpCardType(cardType);
                order.setVnpPayDate(payDate);
                order.setVnpResponseCode(responseCode);
                order.setVnpTransactionStatus(transactionStatus);
                paymentOrderRepository.save(order);

                applyStorageUpgrade(order);
            }

            return VnpayReturnResponse.builder()
                    .success(true)
                    .orderId(order.getOrderId())
                    .planId(order.getPlanId())
                    .message("Giao dịch thành công")
                    .amount(order.getAmount())
                    .transactionNo(transactionNo)
                    .payDate(payDate)
                    .build();
        } else {
            if (order.getStatus() == PaymentStatus.PENDING) {
                order.setStatus(PaymentStatus.FAILED);
                order.setVnpResponseCode(responseCode);
                order.setVnpTransactionStatus(transactionStatus);
                order.setVnpTransactionNo(transactionNo);
                paymentOrderRepository.save(order);
            }

            return VnpayReturnResponse.builder()
                    .success(false)
                    .orderId(order.getOrderId())
                    .planId(order.getPlanId())
                    .message(VnpayUtils.getResponseMessage(responseCode))
                    .amount(order.getAmount())
                    .transactionNo(transactionNo)
                    .payDate(payDate)
                    .build();
        }
    }

    @Override
    @Transactional
    public VnpayIpnResponse processIpn(Map<String, String> params) {
        if (!VnpayUtils.verifySignature(params, vnpayProperties.getHashSecret())) {
            log.warn("VNPay IPN: Chữ ký không hợp lệ");
            return VnpayIpnResponse.invalidChecksum();
        }

        String orderId = params.get("vnp_TxnRef");
        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId).orElse(null);
        if (order == null) {
            log.warn("VNPay IPN: Không tìm thấy đơn hàng: {}", orderId);
            return VnpayIpnResponse.orderNotFound();
        }

        try {
            long vnpAmount = Long.parseLong(params.get("vnp_Amount"));
            if (vnpAmount != order.getAmount() * 100L) {
                log.warn("VNPay IPN: Sai số tiền đơn {}. Kỳ vọng: {}, Nhận: {}", orderId, order.getAmount() * 100L, vnpAmount);
                return VnpayIpnResponse.invalidAmount();
            }
        } catch (Exception e) {
            return VnpayIpnResponse.invalidAmount();
        }

        if (order.getStatus() != PaymentStatus.PENDING) {
            log.info("VNPay IPN: Đơn hàng {} đã được xử lý trước đó với trạng thái {}", orderId, order.getStatus());
            return VnpayIpnResponse.alreadyConfirmed();
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String transactionNo = params.get("vnp_TransactionNo");
        String bankCode = params.get("vnp_BankCode");
        String cardType = params.get("vnp_CardType");
        String payDate = params.get("vnp_PayDate");

        boolean isSuccess = "00".equals(responseCode) && ("00".equals(transactionStatus) || transactionStatus == null);

        if (isSuccess) {
            order.setStatus(PaymentStatus.SUCCESS);
            order.setVnpTransactionNo(transactionNo);
            order.setVnpBankCode(bankCode);
            order.setVnpCardType(cardType);
            order.setVnpPayDate(payDate);
            order.setVnpResponseCode(responseCode);
            order.setVnpTransactionStatus(transactionStatus);
            paymentOrderRepository.save(order);

            applyStorageUpgrade(order);
            log.info("VNPay IPN: Nâng cấp dung lượng thành công cho user {} với đơn hàng {}", order.getUser().getId(), orderId);
        } else {
            order.setStatus(PaymentStatus.FAILED);
            order.setVnpResponseCode(responseCode);
            order.setVnpTransactionStatus(transactionStatus);
            order.setVnpTransactionNo(transactionNo);
            paymentOrderRepository.save(order);
            log.info("VNPay IPN: Ghi nhận đơn hàng {} thất bại với mã lỗi {}", orderId, responseCode);
        }

        return VnpayIpnResponse.success();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentOrderResponse> getUserPaymentHistory(Long userId) {
        return paymentOrderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(PaymentOrderResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderStatusResponse getOrderStatus(String orderId, Long userId) {
        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy đơn hàng: " + orderId));

        return OrderStatusResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus().name())
                .planId(order.getPlanId())
                .planName(order.getPlanName())
                .storageGb(order.getStorageGb())
                .amount(order.getAmount())
                .userStorageQuota(order.getUser() != null ? order.getUser().getStorageQuota() : null)
                .message(order.getStatus() == PaymentStatus.SUCCESS ? "Giao dịch thành công" :
                        (order.getStatus() == PaymentStatus.FAILED ? "Giao dịch không thành công" : "Đang chờ thanh toán"))
                .build();
    }

    private void applyStorageUpgrade(PaymentOrder order) {
        User user = order.getUser();
        long targetQuota = order.getStorageGb() * 1024L * 1024L * 1024L;
        if (targetQuota > user.getStorageQuota()) {
            user.setStorageQuota(targetQuota);
        } else {
            user.setStorageQuota(user.getStorageQuota() + targetQuota);
        }
        userRepository.save(user);

        try {
            auditLogService.log(
                    user.getId(),
                    "UPGRADE_STORAGE",
                    "PAYMENT_ORDER",
                    order.getId(),
                    "VNPay",
                    null,
                    "Nâng cấp dung lượng gói " + order.getPlanName() + " (" + order.getStorageGb() + "GB)"
            );
        } catch (Exception ex) {
            log.warn("Không thể ghi log kiểm toán nâng cấp dung lượng: {}", ex.getMessage());
        }
    }
}
