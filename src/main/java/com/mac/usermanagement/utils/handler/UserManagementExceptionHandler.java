package com.mac.usermanagement.utils.handler;

import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.helper.ResponseHelper;
import com.mac.usermanagement.utils.exception.IdentityConflictException;
import com.mac.usermanagement.utils.exception.InvalidCredentialsException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UserManagementExceptionHandler {

    @ExceptionHandler(IdentityConflictException.class)
    public ResponseEntity<ResponseDTO<Map<String, String>>> handleConflict(IdentityConflictException exception) {
        return ResponseHelper.httpConflict(Map.of("reason", exception.getMessage()));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ResponseDTO<Void>> handleInvalidCredentials(InvalidCredentialsException exception) {
        return ResponseHelper.httpUnauthorized();
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ResponseDTO<Void>> handleAccessDenied(AccessDeniedException exception) {
        return ResponseHelper.httpForbidden();
    }
}
