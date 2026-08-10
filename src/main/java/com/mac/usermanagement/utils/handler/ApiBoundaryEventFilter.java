package com.mac.usermanagement.utils.handler;

import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.usermanagement.entities.model.ErrorAlert;
import com.mac.usermanagement.service.AuditEventPublisher;
import com.mac.usermanagement.service.ErrorAlertNotifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiBoundaryEventFilter extends OncePerRequestFilter {

    private final AuditEventPublisher auditPublisher;
    private final ErrorAlertNotifier alertNotifier;

    public ApiBoundaryEventFilter(AuditEventPublisher auditPublisher, ErrorAlertNotifier alertNotifier) {
        this.auditPublisher = auditPublisher;
        this.alertNotifier = alertNotifier;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        Exception failure = null;
        try {
            filterChain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException exception) {
            failure = exception;
            throw exception;
        } finally {
            if (!Boolean.TRUE.equals(request.getAttribute(OperationalEventInterceptor.AUDIT_RECORDED_ATTRIBUTE))) {
                publishBoundaryEvent(request, response, failure);
            }
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    private void publishBoundaryEvent(HttpServletRequest request, HttpServletResponse response, Exception failure) {
        String traceId = traceId(request);
        String path = truncate(request.getRequestURI(), 200);
        int status = response.getStatus();
        String outcome = failure == null && status < 400 ? "SUCCESS" : "FAILURE";
        String action = "HTTP_" + request.getMethod().toUpperCase();
        auditPublisher.publish(action, "HTTP_REQUEST", path, outcome, traceId, clientIp(request), Map.of(
                "httpMethod", request.getMethod(),
                "httpPath", path,
                "httpStatus", status));
        if (failure != null || status >= 500) {
            alertNotifier.send(ErrorAlert.failure(traceId, "http", action));
        }
    }

    private static String traceId(HttpServletRequest request) {
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
