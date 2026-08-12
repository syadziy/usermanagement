package com.mac.usermanagement.controller;

import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.helper.ResponseHelper;
import com.mac.usermanagement.entities.dto.LoginRequest;
import com.mac.usermanagement.entities.dto.LoginResponse;
import com.mac.usermanagement.entities.dto.AuthSessionResponse;
import com.mac.usermanagement.service.AuthCookieService;
import com.mac.usermanagement.service.AuthService;
import com.mac.usermanagement.utils.AuditRequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService cookieService;

    public AuthController(AuthService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    public ResponseEntity<ResponseDTO<AuthSessionResponse>> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LoginResponse response = authService.login(request);
        cookieService.issue(httpResponse, response.accessToken(), response.expiresAt());
        httpRequest.setAttribute(AuditRequestAttributes.ACTOR_ID, response.userId().toString());
        httpRequest.setAttribute(AuditRequestAttributes.ACTOR_NAME, request.username().trim());
        httpRequest.setAttribute(AuditRequestAttributes.TENANT_ID, response.tenantId().toString());
        return ResponseHelper.httpOK(AuthSessionResponse.from(response, request.username().trim()));
    }

    @GetMapping("/session")
    public ResponseEntity<ResponseDTO<AuthSessionResponse>> session(@AuthenticationPrincipal Jwt jwt) {
        return ResponseHelper.httpOK(AuthSessionResponse.from(jwt));
    }

    @PostMapping("/logout")
    public ResponseEntity<ResponseDTO<Map<String, Boolean>>> logout(HttpServletResponse response) {
        cookieService.clear(response);
        return ResponseHelper.httpOK(Map.of("loggedOut", true));
    }
}
