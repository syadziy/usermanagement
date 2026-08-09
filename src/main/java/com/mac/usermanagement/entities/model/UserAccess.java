package com.mac.usermanagement.entities.model;

import java.util.Set;

public record UserAccess(Set<String> roles, Set<String> permissions) {}
