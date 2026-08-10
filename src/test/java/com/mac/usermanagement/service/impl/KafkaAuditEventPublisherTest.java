package com.mac.usermanagement.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mac.usermanagement.config.properties.AuditPublisherProperties;
import com.mac.usermanagement.entities.dto.AuditEvent;
import com.mac.usermanagement.entities.model.ErrorAlert;
import com.mac.usermanagement.service.ErrorAlertNotifier;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.security.core.context.SecurityContextHolder;

class KafkaAuditEventPublisherTest {

    private static final Instant NOW = Instant.parse("2026-08-10T00:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesHttpEventWithFallbackActor() {
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        ErrorAlertNotifier notifier = mock(ErrorAlertNotifier.class);
        SendResult<String, Object> result = new SendResult<>(
                new ProducerRecord<>("centralized-audit.requested", "key", new Object()), null);
        when(template.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(result));

        new KafkaAuditEventPublisher(template, properties(true), notifier,
                Clock.fixed(NOW, ZoneOffset.UTC)).publish(
                        "AUTH_LOGIN", "AUTH", "/api/v1/auth/login", "SUCCESS",
                        "trace-1", "127.0.0.1", Map.of("httpStatus", 200));

        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(template).send(eq("centralized-audit.requested"), anyString(), eventCaptor.capture());
        AuditEvent event = (AuditEvent) eventCaptor.getValue();
        assertThat(event.actorId()).isEqualTo("usermanagement-service");
        assertThat(event.action()).isEqualTo("AUTH_LOGIN");
        assertThat(event.outcome()).isEqualTo("SUCCESS");
        assertThat(event.traceId()).isEqualTo("trace-1");
        verify(notifier, never()).send(any(ErrorAlert.class));
    }

    @Test
    void alertsForKafkaFailuresAndCanBeDisabled() {
        KafkaTemplate<String, Object> template = mock(KafkaTemplate.class);
        ErrorAlertNotifier notifier = mock(ErrorAlertNotifier.class);
        CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(template.send(anyString(), anyString(), any()))
                .thenReturn(failed)
                .thenThrow(new IllegalStateException("producer closed"));
        KafkaAuditEventPublisher publisher = new KafkaAuditEventPublisher(
                template, properties(true), notifier, Clock.fixed(NOW, ZoneOffset.UTC));

        publisher.publish("CREATE", "USER", "1", "SUCCESS", "trace", null, Map.of());
        publisher.publish("CREATE", "USER", "2", "SUCCESS", "trace", null, Map.of());
        verify(notifier, times(2)).send(any(ErrorAlert.class));

        new KafkaAuditEventPublisher(template, properties(false), notifier,
                Clock.fixed(NOW, ZoneOffset.UTC))
                .publish("CREATE", "USER", "3", "SUCCESS", "trace", null, Map.of());
        verify(template, times(2)).send(anyString(), anyString(), any());
    }

    private static AuditPublisherProperties properties(boolean enabled) {
        return new AuditPublisherProperties(enabled, "centralized-audit.requested",
                "USERMANAGEMENT-SERVICE", "usermanagement-service");
    }
}
