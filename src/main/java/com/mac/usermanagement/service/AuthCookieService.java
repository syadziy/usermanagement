package com.mac.usermanagement.service;

import com.mac.usermanagement.config.properties.AuthCookieProperties;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
public class AuthCookieService {

    private final AuthCookieProperties properties;
    private final Clock clock;

    public AuthCookieService(AuthCookieProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public void issue(HttpServletResponse response, String token, Instant expiresAt) {
        long seconds = Math.max(0, Duration.between(clock.instant(), expiresAt).toSeconds());
        add(response, token, Duration.ofSeconds(seconds));
    }

    public void clear(HttpServletResponse response) {
        add(response, "", Duration.ZERO);
    }

    private void add(HttpServletResponse response, String value, Duration maxAge) {
        ResponseCookie cookie = ResponseCookie.from(properties.name(), value)
                .httpOnly(true)
                .secure(properties.secure())
                .sameSite(properties.sameSite())
                .path(properties.path())
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
