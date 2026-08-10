package com.mac.usermanagement.entities.model;

import java.util.UUID;

public record ErrorAlert(
        String idempotencyKey,
        String correlationId,
        String subject,
        String body) {

    public static ErrorAlert failure(String traceId, String source, String action) {
        String correlation = normalize(traceId, UUID.randomUUID().toString(), 150);
        String safeAction = normalize(action, "unknown-action", 50);
        String safeSource = normalize(source, "unknown-boundary", 40);
        String keyTrace = normalize(correlation, "unknown-trace", 50);
        return new ErrorAlert(
                "usermanagement-error-" + safeAction + "-" + keyTrace,
                correlation,
                "User management service error",
                "The user management service failed while performing '%s' at the %s boundary. Trace ID: %s."
                        .formatted(safeAction, safeSource, correlation));
    }

    private static String normalize(String value, String fallback, int maxLength) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        normalized = normalized.replaceAll("[^A-Za-z0-9._:-]", "-");
        return normalized.substring(0, Math.min(normalized.length(), maxLength));
    }
}
