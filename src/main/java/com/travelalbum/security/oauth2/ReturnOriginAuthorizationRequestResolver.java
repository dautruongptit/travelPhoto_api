package com.travelalbum.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

/** Giữ nguyên hành vi mặc định, chỉ ghi nhận thêm tham số {@code return_origin} (đã whitelist). */
public class ReturnOriginAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final OAuth2AuthorizationRequestResolver delegate;
    private final OAuth2RedirectTarget redirectTarget;

    public ReturnOriginAuthorizationRequestResolver(ClientRegistrationRepository repo,
                                                    OAuth2RedirectTarget redirectTarget) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(repo, "/oauth2/authorization");
        this.redirectTarget = redirectTarget;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest resolved = delegate.resolve(request);
        if (resolved != null) {
            redirectTarget.remember(request, request.getParameter("return_origin"));
        }
        return resolved;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest resolved = delegate.resolve(request, clientRegistrationId);
        if (resolved != null) {
            redirectTarget.remember(request, request.getParameter("return_origin"));
        }
        return resolved;
    }
}
