package com.travelalbum.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Quyết định origin frontend nhận kết quả đăng nhập Google (token / lỗi).
 *
 * Mặc định luôn là {@code app.frontend-url}. Riêng môi trường dev có thể chạy frontend ở
 * localhost nhưng gọi API thật: frontend truyền {@code ?return_origin=http://localhost:8301}
 * khi mở popup, giá trị được lưu vào session và chỉ được dùng nếu KHỚP CHÍNH XÁC một origin
 * trong {@code app.allowed-dev-origins}. Danh sách rỗng (mặc định) = tính năng tắt hoàn toàn.
 */
@Component
public class OAuth2RedirectTarget {

    static final String SESSION_ATTR = "OAUTH2_RETURN_ORIGIN";

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.allowed-dev-origins:}")
    private String allowedDevOrigins;

    public List<String> allowedDevOrigins() {
        return Arrays.stream(allowedDevOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    public boolean isAllowedDevOrigin(String origin) {
        return origin != null && allowedDevOrigins().contains(origin);
    }

    /** Lưu return_origin hợp lệ vào session lúc bắt đầu luồng OAuth2. */
    public void remember(HttpServletRequest request, String origin) {
        if (isAllowedDevOrigin(origin)) {
            request.getSession(true).setAttribute(SESSION_ATTR, origin);
        }
    }

    /** Lấy (và xóa) origin đích; không hợp lệ/không có thì dùng frontend-url. */
    public String consume(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return frontendUrl;
        }
        Object stored = session.getAttribute(SESSION_ATTR);
        session.removeAttribute(SESSION_ATTR);
        return stored instanceof String s && isAllowedDevOrigin(s) ? s : frontendUrl;
    }
}
