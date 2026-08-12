package com.mac.usermanagement.security;

import com.mac.usermanagement.config.properties.AuthCookieProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import org.springframework.http.HttpMethod;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public class CookieBearerTokenResolver implements BearerTokenResolver {

    private final DefaultBearerTokenResolver headerResolver = new DefaultBearerTokenResolver();
    private final String cookieName;

    public CookieBearerTokenResolver(AuthCookieProperties properties) {
        this.cookieName = properties.name();
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (isPublicCredentialEndpoint(request)) {
            return null;
        }
        String headerToken = headerResolver.resolve(request);
        if (headerToken != null) {
            return headerToken;
        }
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static boolean isPublicCredentialEndpoint(HttpServletRequest request) {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return "/api/v1/auth/login".equals(path)
                || "/api/v1/auth/logout".equals(path)
                || "/api/v1/tenants".equals(path);
    }
}
