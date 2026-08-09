package com.mac.usermanagement.entities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePermissionRequest(
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{1,79}") String resource,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_-]{1,79}") String action,
        @Size(max = 255) String description) {}
