package com.mac.usermanagement.service.impl;

import static com.mac.usermanagement.entities.constant.AuthorizationCatalog.PLATFORM_SUPERADMIN_TENANT_KEY;

import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TenantAccessGuard {

    public void requirePlatformTenant() {
        Jwt jwt = authenticatedJwt();
        if (!PLATFORM_SUPERADMIN_TENANT_KEY.equals(jwt.getClaimAsString("tenant_key"))) {
            throw new AccessDeniedException("Platform superadmin access is required");
        }
    }

    public void require(UUID requestedTenantId) {
        Jwt jwt = authenticatedJwt();
        if (PLATFORM_SUPERADMIN_TENANT_KEY.equals(jwt.getClaimAsString("tenant_key"))) {
            return;
        }
        UUID tokenTenantId;
        try {
            tokenTenantId = UUID.fromString(jwt.getClaimAsString("tenant_id"));
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Token contains an invalid tenant", exception);
        }
        if (!tokenTenantId.equals(requestedTenantId)) {
            throw new AccessDeniedException("Cross-tenant access is forbidden");
        }
    }

    private static Jwt authenticatedJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new AccessDeniedException("Authenticated tenant is unavailable");
        }
        return jwt;
    }
}
