package com.mac.usermanagement.controller;

import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.helper.ResponseHelper;
import com.mac.usermanagement.entities.dto.LoginRequest;
import com.mac.usermanagement.entities.dto.LoginResponse;
import com.mac.usermanagement.service.AuthService;
import com.mac.usermanagement.utils.AuditRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseDTO<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        LoginResponse response = authService.login(request);
        httpRequest.setAttribute(AuditRequestAttributes.ACTOR_ID, response.userId().toString());
        httpRequest.setAttribute(AuditRequestAttributes.ACTOR_NAME, request.username().trim());
        httpRequest.setAttribute(AuditRequestAttributes.TENANT_ID, response.tenantId().toString());
        return ResponseHelper.httpOK(response);
    }
}
