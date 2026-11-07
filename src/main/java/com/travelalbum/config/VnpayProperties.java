package com.travelalbum.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binding cho prefix "vnpay" trong application.yml — giá trị thật đọc từ
 * biến môi trường VNPAY_TMN_CODE / VNPAY_HASH_SECRET trong .env (KHÔNG commit).
 */
@Configuration
@ConfigurationProperties(prefix = "vnpay")
@Getter
@Setter
public class VnpayProperties {

    private String tmnCode;
    private String hashSecret;
    private String payUrl;
    private String returnUrl;
}
