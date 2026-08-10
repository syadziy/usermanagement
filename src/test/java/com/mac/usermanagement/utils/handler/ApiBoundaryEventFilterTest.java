package com.mac.usermanagement.utils.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.mac.usermanagement.entities.model.ErrorAlert;
import com.mac.usermanagement.service.AuditEventPublisher;
import com.mac.usermanagement.service.ErrorAlertNotifier;
import jakarta.servlet.ServletException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiBoundaryEventFilterTest {

    @Test
    void auditsSecurityRejectionWithoutAlertingOrDuplicatingMvcEvent() throws Exception {
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        ErrorAlertNotifier alertNotifier = mock(ErrorAlertNotifier.class);
        ApiBoundaryEventFilter filter = new ApiBoundaryEventFilter(auditPublisher, alertNotifier);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(401));
        verify(auditPublisher).publish(eq("HTTP_GET"), eq("HTTP_REQUEST"), eq("/api/v1/users"),
                eq("FAILURE"), eq("trace-security"), any(), any(Map.class));
        verify(alertNotifier, never()).send(any(ErrorAlert.class));

        MockHttpServletRequest recorded = request();
        recorded.setAttribute(OperationalEventInterceptor.AUDIT_RECORDED_ATTRIBUTE, Boolean.TRUE);
        filter.doFilter(recorded, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {});
        verifyNoMoreInteractions(auditPublisher);
    }

    @Test
    void alertsForUnhandledBoundaryExceptionAndSkipsNonApi() throws Exception {
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        ErrorAlertNotifier alertNotifier = mock(ErrorAlertNotifier.class);
        ApiBoundaryEventFilter filter = new ApiBoundaryEventFilter(auditPublisher, alertNotifier);
        MockHttpServletRequest request = request();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> filter.doFilter(
                request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
                    throw new ServletException("failed");
                })).isInstanceOf(ServletException.class);
        verify(alertNotifier).send(any(ErrorAlert.class));

        filter.doFilter(new MockHttpServletRequest("GET", "/actuator/health"),
                new MockHttpServletResponse(), (servletRequest, servletResponse) -> {});
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users");
        request.addHeader("X-Correlation-Id", "trace-security");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }
}
