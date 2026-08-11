package com.mac.usermanagement.utils.handler;

import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.usermanagement.entities.model.ErrorAlert;
import com.mac.usermanagement.service.ErrorAlertNotifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiBoundaryEventFilter extends OncePerRequestFilter {

    private final ErrorAlertNotifier alertNotifier;

    public ApiBoundaryEventFilter(ErrorAlertNotifier alertNotifier) {
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
            notifyBoundaryFailure(request, response, failure);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    private void notifyBoundaryFailure(HttpServletRequest request, HttpServletResponse response, Exception failure) {
        int status = response.getStatus();
        if (failure != null || status >= 500) {
            alertNotifier.send(ErrorAlert.failure(traceId(request), "http",
                    "HTTP_" + request.getMethod().toUpperCase()));
        }
    }

    private static String traceId(HttpServletRequest request) {
        String traceId = MDC.get(LogFields.TRACE_ID);
        if (traceId == null || traceId.isBlank()) {
            traceId = request.getHeader("X-Correlation-Id");
        }
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }

}
