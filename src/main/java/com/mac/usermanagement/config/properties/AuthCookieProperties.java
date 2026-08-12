package com.mac.usermanagement.config.properties;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("usermanagement.auth-cookie")
public record AuthCookieProperties(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]+") String name,
        @NotBlank String path,
        boolean secure,
        @NotBlank @Pattern(regexp = "Strict|Lax|None") String sameSite) {

    public AuthCookieProperties {
        name = name == null ? "ACCESS_TOKEN" : name;
        path = path == null ? "/" : path;
        sameSite = sameSite == null ? "Strict" : sameSite;
    }
}
