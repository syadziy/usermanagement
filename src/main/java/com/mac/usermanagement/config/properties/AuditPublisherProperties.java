package com.mac.usermanagement.config.properties;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("usermanagement.audit")
public record AuditPublisherProperties(
        boolean enabled,
        @NotBlank String topic,
        @NotBlank String sourceSystem,
        @NotBlank String fallbackActorId) {

    public AuditPublisherProperties {
        topic = topic == null ? "centralized-audit.requested" : topic;
        sourceSystem = sourceSystem == null ? "USERMANAGEMENT-SERVICE" : sourceSystem;
        fallbackActorId = fallbackActorId == null ? "usermanagement-service" : fallbackActorId;
    }
}
