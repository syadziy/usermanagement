package com.mac.usermanagement.entities.dto;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;

public record AuthSessionResponse(
        Instant expiresAt,
        UUID tenantId,
        UUID userId,
        String username,
        Set<String> roles,
        Set<String> permissions) {

    public static AuthSessionResponse from(LoginResponse response, String username) {
        return new AuthSessionResponse(response.expiresAt(), response.tenantId(), response.userId(),
                username, response.roles(), response.permissions());
    }

    public static AuthSessionResponse from(Jwt jwt) {
        return new AuthSessionResponse(jwt.getExpiresAt(),
                UUID.fromString(jwt.getClaimAsString("tenant_id")), UUID.fromString(jwt.getSubject()),
                jwt.getClaimAsString("username"), claimSet(jwt, "roles"), claimSet(jwt, "permissions"));
    }

    private static Set<String> claimSet(Jwt jwt, String claim) {
        List<String> values = jwt.getClaimAsStringList(claim);
        return values == null ? Set.of() : Set.copyOf(values);
    }
}
