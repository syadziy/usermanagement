package com.mac.usermanagement.entities.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.UUID;

public record AssignRolesRequest(@NotEmpty @Size(max = 20) Set<UUID> roleIds) {}
