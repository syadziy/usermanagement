package com.mac.usermanagement.controller;

import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.entities.dto.PagingDTO;
import com.mac.sdk_util.entities.constant.Role;
import com.mac.sdk_util.helper.ResponseHelper;
import com.mac.sdk_util.helper.ResponsePagingHelper;
import com.mac.usermanagement.entities.dto.AssignRolesRequest;
import com.mac.usermanagement.entities.dto.CreateUserRequest;
import com.mac.usermanagement.entities.dto.UserResponse;
import com.mac.usermanagement.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @PreAuthorize(Role.USER_CREATE)
    public ResponseEntity<ResponseDTO<UserResponse>> create(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse response = userService.create(tenantId, request);
        return ResponseHelper.httpCreated(response,
                URI.create("/api/v1/tenants/" + tenantId + "/users/" + response.userId()));
    }

    @GetMapping
    @PreAuthorize(Role.USER_VIEW)
    public ResponseEntity<ResponseDTO<List<UserResponse>>> findAll(
            @PathVariable UUID tenantId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(500) int limit,
            @RequestParam(defaultValue = "0") @Min(0) int offset) {
        return ResponsePagingHelper.httpOK(userService.findAll(tenantId, limit, offset),
                new PagingDTO(limit, offset, userService.count(tenantId)));
    }

    @PutMapping("/{userId}/roles")
    @PreAuthorize(Role.ROLE_ASSIGN)
    public ResponseEntity<ResponseDTO<UserResponse>> assignRoles(
            @PathVariable UUID tenantId,
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRolesRequest request) {
        return ResponseHelper.httpOK(userService.assignRoles(tenantId, userId, request.roleIds()));
    }
}
