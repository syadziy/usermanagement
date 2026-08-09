package com.mac.usermanagement.entities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateRoleRequest(
        @NotBlank @Pattern(regexp = "[A-Z][A-Z0-9_]{2,79}") String name,
        @Size(max = 255) String description,
        @Size(max = 100) Set<String> permissions) {}
