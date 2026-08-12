package com.mac.usermanagement.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mac.usermanagement.config.properties.AuthCookieProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void issuesAndClearsHttpOnlyCookie() {
        AuthCookieService service = new AuthCookieService(
                new AuthCookieProperties("ACCESS_TOKEN", "/", true, "Strict"),
                Clock.fixed(NOW, ZoneOffset.UTC));
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.issue(response, "signed-token", NOW.plusSeconds(3600));
        service.clear(response);

        assertThat(response.getHeaders("Set-Cookie")).hasSize(2);
        assertThat(response.getHeaders("Set-Cookie").get(0))
                .contains("ACCESS_TOKEN=signed-token", "Path=/", "Max-Age=3600", "Secure", "HttpOnly",
                        "SameSite=Strict");
        assertThat(response.getHeaders("Set-Cookie").get(1))
                .contains("ACCESS_TOKEN=", "Path=/", "Max-Age=0", "Secure", "HttpOnly", "SameSite=Strict");
    }
}
