package com.mac.usermanagement.service.impl;

import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.sdk_util.utils.StructuredLog;
import com.mac.usermanagement.config.properties.AuditPublisherProperties;
import com.mac.usermanagement.entities.dto.AuditEvent;
import com.mac.usermanagement.entities.model.ErrorAlert;
import com.mac.usermanagement.service.AuditEventPublisher;
import com.mac.usermanagement.service.ErrorAlertNotifier;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class KafkaAuditEventPublisher implements AuditEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAuditEventPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final AuditPublisherProperties properties;
    private final ErrorAlertNotifier alertNotifier;
    private final Clock clock;

    public KafkaAuditEventPublisher(KafkaTemplate<String, Object> kafkaTemplate,
            AuditPublisherProperties properties, ErrorAlertNotifier alertNotifier, Clock clock) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.alertNotifier = alertNotifier;
        this.clock = clock;
    }

    @Override
    public void publish(String action, String resourceType, String resourceId, String outcome,
            String traceId, String clientIp, Map<String, Object> metadata) {
        if (!properties.enabled()) {
            return;
        }
        UUID eventId = UUID.randomUUID();
        String effectiveTraceId = traceId == null || traceId.isBlank() ? eventId.toString() : traceId;
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> effectiveMetadata = new LinkedHashMap<>(metadata);
        String effectiveActorId = removeString(effectiveMetadata, "_auditActorId");
        String effectiveActorName = removeString(effectiveMetadata, "_auditActorName");
        String tenantId = removeString(effectiveMetadata, "_auditTenantId");
        effectiveActorId = effectiveActorId == null ? actorId(authentication) : effectiveActorId;
        effectiveActorName = effectiveActorName == null ? actorName(authentication) : effectiveActorName;
        tenantId = tenantId == null ? tenantId(authentication) : tenantId;
        if (tenantId != null) {
            effectiveMetadata.put("tenantId", tenantId);
        }
        AuditEvent event = new AuditEvent(eventId, properties.sourceSystem(), clock.instant(),
                effectiveActorId, effectiveActorName, action, resourceType, resourceId,
                outcome, effectiveTraceId, clientIp, Map.copyOf(effectiveMetadata));
        try {
            kafkaTemplate.send(properties.topic(), eventId.toString(), event)
                    .whenComplete((result, exception) -> {
                        if (exception == null) {
                            StructuredLog.info(LOG, "User management audit event published", Map.of(
                                    LogFields.EVENT_ACTION, "publishAuditEvent",
                                    LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS,
                                    LogFields.EVENT_DATASET, "usermanagement.audit",
                                    "audit.event.id", eventId));
                        } else {
                            handleFailure(effectiveTraceId, eventId, exception);
                        }
                    });
        } catch (RuntimeException exception) {
            handleFailure(effectiveTraceId, eventId, exception);
        }
    }

    private static String removeString(Map<String, Object> metadata, String key) {
        Object value = metadata.remove(key);
        return value == null || value.toString().isBlank() ? null : value.toString();
    }

    private void handleFailure(String traceId, UUID eventId, Throwable exception) {
        StructuredLog.error(LOG, "User management audit event could not be published", Map.of(
                LogFields.EVENT_ACTION, "publishAuditEvent",
                LogFields.EVENT_OUTCOME, LogFields.OUTCOME_FAILURE,
                LogFields.EVENT_DATASET, "usermanagement.audit",
                "audit.event.id", eventId), exception);
        alertNotifier.send(ErrorAlert.failure(traceId, "kafka", "publishAuditEvent"));
    }

    private String actorId(Authentication authentication) {
        if (authenticated(authentication) && authentication.getPrincipal() instanceof Jwt jwt) {
            String subject = jwt.getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }
        return authenticated(authentication) ? authentication.getName() : properties.fallbackActorId();
    }

    private static String actorName(Authentication authentication) {
        if (authenticated(authentication) && authentication.getPrincipal() instanceof Jwt jwt) {
            String username = jwt.getClaimAsString("username");
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        return authenticated(authentication) ? authentication.getName() : null;
    }

    private static String tenantId(Authentication authentication) {
        if (authenticated(authentication) && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("tenant_id");
        }
        return null;
    }

    private static boolean authenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null && !authentication.getName().isBlank()
                && !"anonymousUser".equals(authentication.getName());
    }
}
