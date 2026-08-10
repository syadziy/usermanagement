package com.mac.usermanagement.utils.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mac.usermanagement.controller.AuthController;
import com.mac.usermanagement.entities.model.ErrorAlert;
import com.mac.usermanagement.service.AuditEventPublisher;
import com.mac.usermanagement.service.ErrorAlertNotifier;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class OperationalEventInterceptorTest {

    @Test
    void auditsSuccessfulApiActionWithoutAlerting() throws Exception {
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        ErrorAlertNotifier alertNotifier = mock(ErrorAlertNotifier.class);
        OperationalEventInterceptor interceptor = new OperationalEventInterceptor(auditPublisher, alertNotifier);
        MockHttpServletRequest request = request();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.afterCompletion(request, response, handler(), null);

        verify(auditPublisher).publish(eq("AUTH_LOGIN"), eq("AUTH"), eq("/api/v1/auth/login"),
                eq("SUCCESS"), eq("trace-1"), any(), any(Map.class));
        verify(alertNotifier, never()).send(any(ErrorAlert.class));
    }

    @Test
    void auditsFailureAndAlertsOnlyForServerErrors() throws Exception {
        AuditEventPublisher auditPublisher = mock(AuditEventPublisher.class);
        ErrorAlertNotifier alertNotifier = mock(ErrorAlertNotifier.class);
        OperationalEventInterceptor interceptor = new OperationalEventInterceptor(auditPublisher, alertNotifier);
        MockHttpServletRequest request = request();
        MockHttpServletResponse clientError = new MockHttpServletResponse();
        clientError.setStatus(401);
        interceptor.afterCompletion(request, clientError, handler(), null);
        verify(alertNotifier, never()).send(any(ErrorAlert.class));

        MockHttpServletResponse serverError = new MockHttpServletResponse();
        serverError.setStatus(500);
        interceptor.afterCompletion(request, serverError, handler(), null);
        verify(alertNotifier).send(any(ErrorAlert.class));
        verify(auditPublisher, times(2)).publish(eq("AUTH_LOGIN"), eq("AUTH"), any(), eq("FAILURE"),
                eq("trace-1"), any(), any(Map.class));
    }

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("X-Correlation-Id", "trace-1");
        request.setRemoteAddr("127.0.0.1");
        return request;
    }

    private static HandlerMethod handler() throws NoSuchMethodException {
        AuthController controller = mock(AuthController.class);
        return new HandlerMethod(controller, AuthController.class.getMethod("login",
                com.mac.usermanagement.entities.dto.LoginRequest.class,
                jakarta.servlet.http.HttpServletRequest.class));
    }
}
