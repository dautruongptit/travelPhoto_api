package com.travelalbum.security.oauth2;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OAuth2RedirectTargetTest {

    private static final String PROD = "https://travelphoto.example";

    private OAuth2RedirectTarget target(String allowed) {
        OAuth2RedirectTarget t = new OAuth2RedirectTarget();
        ReflectionTestUtils.setField(t, "frontendUrl", PROD);
        ReflectionTestUtils.setField(t, "allowedDevOrigins", allowed);
        return t;
    }

    @Test
    void allowedOriginIsUsedOnceThenFallsBackToFrontendUrl() {
        OAuth2RedirectTarget t = target("http://localhost:8300, http://localhost:8301");
        MockHttpServletRequest req = new MockHttpServletRequest();

        t.remember(req, "http://localhost:8301");

        assertEquals("http://localhost:8301", t.consume(req));
        assertEquals(PROD, t.consume(req));
    }

    @Test
    void unlistedOriginIsIgnored() {
        OAuth2RedirectTarget t = target("http://localhost:8300");
        MockHttpServletRequest req = new MockHttpServletRequest();

        t.remember(req, "https://evil.example");

        assertEquals(PROD, t.consume(req));
    }

    @Test
    void emptyAllowListDisablesFeature() {
        OAuth2RedirectTarget t = target("");
        MockHttpServletRequest req = new MockHttpServletRequest();

        t.remember(req, "http://localhost:8300");

        assertEquals(PROD, t.consume(req));
        assertTrue(t.allowedDevOrigins().isEmpty());
    }

    @Test
    void noSessionFallsBackToFrontendUrl() {
        assertEquals(PROD, target("http://localhost:8300").consume(new MockHttpServletRequest()));
    }
}
