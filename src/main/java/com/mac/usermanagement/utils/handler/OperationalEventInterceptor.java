package com.mac.usermanagement.utils.handler;

import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.usermanagement.entities.model.ErrorAlert;
import com.mac.usermanagement.service.AuditEventPublisher;
import com.mac.usermanagement.service.ErrorAlertNotifier;
import com.mac.usermanagement.utils.AuditRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class OperationalEventInterceptor implements HandlerInterceptor {

    static final String AUDIT_RECORDED_ATTRIBUTE =
            OperationalEventInterceptor.class.getName() + ".auditRecorded";

    private final AuditEventPublisher auditPublisher;
    private final ErrorAlertNotifier alertNotifier;

    public OperationalEventInterceptor(AuditEventPublisher auditPublisher, ErrorAlertNotifier alertNotifier) {
        this.auditPublisher = auditPublisher;
        this.alertNotifier = alertNotifier;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception exception) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }
        request.setAttribute(AUDIT_RECORDED_ATTRIBUTE, Boolean.TRUE);
        String traceId = resolveTraceId(request);
        String resourceType = handlerMethod.getBeanType().getSimpleName().replace("Controller", "").toUpperCase();
        String action = resourceType + "_" + handlerMethod.getMethod().getName().toUpperCase();
        int status = response.getStatus();
        String outcome = exception == null && status < 400 ? "SUCCESS" : "FAILURE";
        String path = truncate(request.getRequestURI(), 200);
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("httpMethod", request.getMethod());
        metadata.put("httpPath", path);
        metadata.put("httpStatus", status);
        copyAttribute(request, metadata, AuditRequestAttributes.ACTOR_ID, "_auditActorId");
        copyAttribute(request, metadata, AuditRequestAttributes.ACTOR_NAME, "_auditActorName");
        copyAttribute(request, metadata, AuditRequestAttributes.TENANT_ID, "_auditTenantId");
        auditPublisher.publish(action, resourceType, path, outcome, traceId, clientIp(request), metadata);
        if (exception != null || status >= 500) {
            alertNotifier.send(ErrorAlert.failure(traceId, "http", action));
        }
    }

    private static void copyAttribute(HttpServletRequest request, Map<String, Object> metadata,
            String attributeName, String metadataName) {
        Object value = request.getAttribute(attributeName);
        if (value != null) {
            metadata.put(metadataName, value);
        }
    }

    private static String resolveTraceId(HttpServletRequest request) {
        String traceId = MDC.get(LogFields.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Correlation-Id");
        }
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String value = forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr()
                : forwarded.split(",", 2)[0].trim();
        return truncate(value, 64);
    }

    private static String truncate(String value, int maxLength) {
        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
    }
}
