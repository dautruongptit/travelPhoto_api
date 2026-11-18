package com.travelalbum.util;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
public final class VnpayUtils {

    private VnpayUtils() {}

    public static String hmacSha512(String key, String data) {
        try {
            if (key == null || data == null) {
                return "";
            }
            Mac hmac512 = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            hmac512.init(secretKey);
            byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(2 * bytes.length);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception ex) {
            log.error("Lỗi tính HMAC-SHA512 VNPay: {}", ex.getMessage());
            return "";
        }
    }

    public static String buildPaymentUrl(String baseUrl, String secretKey, Map<String, String> params) {
        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                try {
                    if (hashData.length() > 0) {
                        hashData.append('&');
                        query.append('&');
                    }
                    hashData.append(fieldName)
                            .append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                    query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()))
                            .append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (Exception e) {
                    log.error("Lỗi mã hóa tham số VNPay {}: {}", fieldName, e.getMessage());
                }
            }
        }

        String secureHash = hmacSha512(secretKey, hashData.toString());
        return baseUrl + "?" + query.toString() + "&vnp_SecureHash=" + secureHash;
    }

    public static boolean verifySignature(Map<String, String> params, String secretKey) {
        if (params == null || !StringUtils.hasText(secretKey)) {
            return false;
        }

        String vnpSecureHash = params.get("vnp_SecureHash");
        if (!StringUtils.hasText(vnpSecureHash)) {
            return false;
        }

        List<String> fieldNames = new ArrayList<>(params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        for (String fieldName : fieldNames) {
            if ("vnp_SecureHash".equals(fieldName) || "vnp_SecureHashType".equals(fieldName)) {
                continue;
            }
            String fieldValue = params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                try {
                    if (hashData.length() > 0) {
                        hashData.append('&');
                    }
                    hashData.append(fieldName)
                            .append('=')
                            .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));
                } catch (Exception e) {
                    log.error("Lỗi mã hóa tham số kiểm tra chữ ký {}: {}", fieldName, e.getMessage());
                }
            }
        }

        String signValue = hmacSha512(secretKey, hashData.toString());
        return signValue.equalsIgnoreCase(vnpSecureHash);
    }

    public static String getIpAddress(HttpServletRequest request) {
        String ip = null;
        if (request != null) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                ip = forwarded.split(",")[0].trim();
            } else {
                ip = request.getRemoteAddr();
            }
        }
        if (!StringUtils.hasText(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return ip;
    }

    public static String getResponseMessage(String responseCode) {
        if (responseCode == null) return "Giao dịch không xác định";
        return switch (responseCode) {
            case "00" -> "Giao dịch thành công";
            case "07" -> "Trừ tiền thành công. Giao dịch bị nghi ngờ (liên quan tới lừa đảo, bất thường).";
            case "09" -> "Thẻ/Tài khoản chưa đăng ký dịch vụ InternetBanking tại ngân hàng.";
            case "10" -> "Xác thực thông tin thẻ/tài khoản không đúng quá 3 lần.";
            case "11" -> "Đã hết hạn chờ thanh toán.";
            case "12" -> "Thẻ/Tài khoản của quý khách đang bị khóa.";
            case "13" -> "Quý khách nhập sai mật khẩu xác thực OTP.";
            case "24" -> "Khách hàng đã hủy giao dịch.";
            case "51" -> "Tài khoản của quý khách không đủ số dư để thực hiện giao dịch.";
            case "65" -> "Tài khoản đã vượt quá hạn mức giao dịch trong ngày.";
            case "75" -> "Ngân hàng thanh toán đang bảo trì.";
            case "79" -> "Nhập sai mật khẩu thanh toán quá số lần quy định.";
            default -> "Giao dịch thất bại (Mã lỗi: " + responseCode + ")";
        };
    }
}
