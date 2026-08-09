package com.mac.usermanagement.entities.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterTenantRequest(
        @NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{2,63}") String tenantKey,
        @NotBlank @Size(max = 150) String tenantName,
        @Min(60) @Max(86400) long accessTokenTtlSeconds,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,80}") String ownerUsername,
        @NotBlank @Email @Size(max = 254) String ownerEmail,
        @NotBlank @Size(min = 12, max = 72) String ownerPassword) {}
