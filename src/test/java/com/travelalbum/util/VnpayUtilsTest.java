package com.travelalbum.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnpayUtilsTest {

    private final String secretKey = "DEMOSECRETKEYFORTESTING";

    @Test
    @DisplayName("HMAC-SHA512 produces valid lowercase hex digest")
    void testHmacSha512() {
        String data = "vnp_Amount=1000000&vnp_Command=pay&vnp_TxnRef=PT123456";
        String hash = VnpayUtils.hmacSha512(secretKey, data);

        assertNotNull(hash);
        assertEquals(128, hash.length()); // SHA-512 hex is 128 characters
        assertEquals(hash.toLowerCase(), hash);
    }

    @Test
    @DisplayName("buildPaymentUrl correctly builds query with sorted parameters and secure hash")
    void testBuildPaymentUrl() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Command", "pay");
        params.put("vnp_Amount", "1000000");
        params.put("vnp_TxnRef", "PT99999");
        params.put("vnp_Version", "2.1.0");

        String payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
        String fullUrl = VnpayUtils.buildPaymentUrl(payUrl, secretKey, params);

        assertTrue(fullUrl.startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?"));
        assertTrue(fullUrl.contains("vnp_SecureHash="));
        assertTrue(fullUrl.contains("vnp_Amount=1000000"));
        assertTrue(fullUrl.contains("vnp_TxnRef=PT99999"));
    }

    @Test
    @DisplayName("verifySignature returns true for matching hash and false for tampered data")
    void testVerifySignature() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_Amount", "1000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TxnRef", "PT99999");

        // Calculate valid hash
        String validHash = VnpayUtils.hmacSha512(secretKey, "vnp_Amount=1000000&vnp_ResponseCode=00&vnp_TxnRef=PT99999");
        params.put("vnp_SecureHash", validHash);

        assertTrue(VnpayUtils.verifySignature(params, secretKey));

        // Tamper with an amount
        params.put("vnp_Amount", "2000000");
        assertFalse(VnpayUtils.verifySignature(params, secretKey));
    }

    @Test
    @DisplayName("getResponseMessage returns accurate Vietnamese descriptions")
    void testResponseMessage() {
        assertEquals("Giao dịch thành công", VnpayUtils.getResponseMessage("00"));
        assertEquals("Khách hàng đã hủy giao dịch.", VnpayUtils.getResponseMessage("24"));
        assertEquals("Tài khoản của quý khách không đủ số dư để thực hiện giao dịch.", VnpayUtils.getResponseMessage("51"));
    }
}
