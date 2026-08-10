package com.mac.usermanagement.service;

import java.util.Map;

public interface AuditEventPublisher {

    void publish(String action, String resourceType, String resourceId, String outcome,
            String traceId, String clientIp, Map<String, Object> metadata);
}
