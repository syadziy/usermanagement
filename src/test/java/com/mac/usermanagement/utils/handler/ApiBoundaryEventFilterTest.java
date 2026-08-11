package com.mac.usermanagement.utils.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mac.usermanagement.entities.model.ErrorAlert;
import com.mac.usermanagement.service.ErrorAlertNotifier;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ApiBoundaryEventFilterTest {

    @Test
    void ignoresClientErrorsBecauseGatewayOwnsRequestAudit() throws Exception {
        ErrorAlertNotifier alertNotifier = mock(ErrorAlertNotifier.class);
        ApiBoundaryEventFilter filter = new ApiBoundaryEventFilter(alertNotifier);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> response.setStatus(401));
        verify(alertNotifier, never()).send(any(ErrorAlert.class));
    }

    @Test
    void alertsForUnhandledBoundaryExceptionAndSkipsNonApi() throws Exception {
        ErrorAlertNotifier alertNotifier = mock(ErrorAlertNotifier.class);
        ApiBoundaryEventFilter filter = new ApiBoundaryEventFilter(alertNotifier);
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
