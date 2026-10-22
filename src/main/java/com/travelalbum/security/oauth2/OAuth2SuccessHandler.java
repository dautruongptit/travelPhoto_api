package com.travelalbum.security.oauth2;

import com.travelalbum.entity.RefreshToken;
import com.travelalbum.entity.User;
import com.travelalbum.repository.RefreshTokenRepository;
import com.travelalbum.repository.UserRepository;
import com.travelalbum.security.SessionCache;
import com.travelalbum.security.jwt.JwtTokenProvider;
import com.travelalbum.service.AuditLogService;
import com.travelalbum.storage.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;
    private final SessionCache sessionCache;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.cookie-secure:true}")
    private boolean cookieSecure;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String googleId = oAuth2User.getAttribute("sub");
        User user = userRepository.findByGoogleId(googleId)
                .orElseThrow(() -> new IllegalStateException("User not found after OAuth2 login"));

        String ip = ClientIpUtils.resolveIp(request);
        String userAgent = request.getHeader("User-Agent");

        String newSessionId = UUID.randomUUID().toString();
        user.setCurrentSessionId(newSessionId);
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(ip);
        userRepository.save(user);
        sessionCache.put(user.getId(), newSessionId);

        refreshTokenRepository.revokeAllByUserId(user.getId());

        String accessToken = tokenProvider.generateAccessToken(user, newSessionId);
        String refreshToken = tokenProvider.generateRefreshToken();
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .sessionId(newSessionId)
                .tokenHash(DigestUtils.sha256Hex(refreshToken))
                .expiresAt(LocalDateTime.now().plusDays(7))
                .revoked(false)
                .build());

        auditLogService.log(user.getId(), "LOGIN", "USER", user.getId(), ip, userAgent, "SUCCESS");

        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true).secure(cookieSecure).sameSite("Strict").path("/api/auth")
                .maxAge(Duration.ofDays(7)).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Trỏ thẳng tới trang tĩnh oauth2-callback.html (postMessage về cửa sổ chính rồi tự
        // đóng popup). Trước đây là /oauth2/callback -> rơi vào SPA nên popup tự load nguyên
        // app và đăng nhập ngay trong popup thay vì trả token về cửa sổ chính.
        response.sendRedirect(frontendUrl + "/oauth2-callback.html?token=" + accessToken);
    }
}