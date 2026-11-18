package com.travelalbum.service;

import com.travelalbum.dto.request.CreatePaymentRequest;
import com.travelalbum.dto.response.PaymentOrderResponse;
import com.travelalbum.dto.response.PaymentUrlResponse;
import com.travelalbum.dto.response.VnpayIpnResponse;
import com.travelalbum.dto.response.VnpayReturnResponse;
import com.travelalbum.security.userdetails.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

public interface PaymentService {

    PaymentUrlResponse createPayment(UserPrincipal principal, CreatePaymentRequest request, HttpServletRequest httpRequest);

    VnpayReturnResponse processReturn(Map<String, String> params);

    VnpayIpnResponse processIpn(Map<String, String> params);

    List<PaymentOrderResponse> getUserPaymentHistory(Long userId);
}
