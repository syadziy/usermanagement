package com.mac.usermanagement.config.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "usermanagement.jwt")
public record JwtProperties(
        @NotBlank @Pattern(regexp = "https?://\\S+") String issuer,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,128}") String keyId,
        String privateKey,
        String publicKey,
        boolean generateEphemeralKey,
        @NotEmpty List<@NotBlank String> audiences) {

    @AssertTrue(message = "JWT private and public keys are required unless ephemeral key generation is enabled")
    public boolean isKeyConfigurationValid() {
        boolean hasPrivateKey = hasText(privateKey);
        boolean hasPublicKey = hasText(publicKey);
        return hasPrivateKey == hasPublicKey && (generateEphemeralKey || hasPrivateKey);
    }

    public String normalizedIssuer() {
        String value = issuer.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
