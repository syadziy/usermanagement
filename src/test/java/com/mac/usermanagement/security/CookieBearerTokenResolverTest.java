package com.mac.usermanagement.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mac.usermanagement.config.properties.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieBearerTokenResolverTest {

    private final CookieBearerTokenResolver resolver = new CookieBearerTokenResolver(
            new AuthCookieProperties("ACCESS_TOKEN", "/", false, "Strict"));

    @Test
    void ignoresStaleCredentialsOnLogin() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer stale-header-token");
        request.setCookies(new Cookie("ACCESS_TOKEN", "stale-cookie-token"));

        assertThat(resolver.resolve(request)).isNull();
    }

    @Test
    void stillResolvesCookieForProtectedEndpoints() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/session");
        request.setCookies(new Cookie("ACCESS_TOKEN", "valid-cookie-token"));

        assertThat(resolver.resolve(request)).isEqualTo("valid-cookie-token");
    }
}
