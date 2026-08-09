package com.mac.usermanagement.entities.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record UpdateRolePermissionsRequest(@NotNull @Size(max = 100) Set<String> permissions) {}
