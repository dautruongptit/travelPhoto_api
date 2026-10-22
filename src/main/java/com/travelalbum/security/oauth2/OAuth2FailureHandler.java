package com.travelalbum.security.oauth2;

import com.travelalbum.service.AuditLogService;
import com.travelalbum.storage.ClientIpUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private final AuditLogService auditLogService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String ip = ClientIpUtils.resolveIp(request);
        String userAgent = request.getHeader("User-Agent");
        auditLogService.log(null, "LOGIN_FAILED", "USER", null, ip, userAgent, "FAILED");
        response.sendRedirect(frontendUrl + "/oauth2-callback.html?error=login_failed");
    }
}