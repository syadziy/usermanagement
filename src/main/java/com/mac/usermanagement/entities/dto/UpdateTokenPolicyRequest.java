package com.mac.usermanagement.entities.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateTokenPolicyRequest(@Min(60) @Max(86400) long accessTokenTtlSeconds) {}
