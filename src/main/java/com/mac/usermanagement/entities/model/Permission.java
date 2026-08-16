package com.mac.usermanagement.entities.model;

import java.time.Instant;
import java.util.UUID;

public record Permission(
        UUID id,
        String resource,
        String action,
        String description,
        Instant createdAt) {

    public String authority() {
        return resource + ":" + action;
    }
}
