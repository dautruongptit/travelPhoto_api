package com.travelalbum.controller;

import com.travelalbum.common.ApiResponse;
import com.travelalbum.dto.request.CreatePaymentRequest;
import com.travelalbum.dto.response.PaymentOrderResponse;
import com.travelalbum.dto.response.PaymentUrlResponse;
import com.travelalbum.dto.response.VnpayIpnResponse;
import com.travelalbum.dto.response.VnpayReturnResponse;
import com.travelalbum.security.userdetails.UserPrincipal;
import com.travelalbum.service.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/vnpay")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create-payment")
    public ApiResponse<PaymentUrlResponse> createPayment(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePaymentRequest request,
            HttpServletRequest httpRequest) {
        PaymentUrlResponse response = paymentService.createPayment(principal, request, httpRequest);
        return ApiResponse.success("Tạo link thanh toán VNPay thành công", response);
    }

    @GetMapping("/return")
    public ApiResponse<VnpayReturnResponse> returnCallback(
            @RequestParam Map<String, String> params) {
        VnpayReturnResponse response = paymentService.processReturn(params);
        return ApiResponse.success("Kết quả thanh toán", response);
    }

    @RequestMapping(value = "/vnpay-ipn", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<VnpayIpnResponse> ipnCallback(
            @RequestParam Map<String, String> params) {
        VnpayIpnResponse response = paymentService.processIpn(params);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ApiResponse<List<PaymentOrderResponse>> getMyPayments(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<PaymentOrderResponse> history = paymentService.getUserPaymentHistory(principal.getId());
        return ApiResponse.success("OK", history);
    }
}
