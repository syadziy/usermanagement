package com.mac.usermanagement.controller;

import com.mac.sdk_util.entities.dto.ResponseDTO;
import com.mac.sdk_util.entities.constant.Role;
import com.mac.sdk_util.helper.ResponseHelper;
import com.mac.usermanagement.entities.dto.CreatePermissionRequest;
import com.mac.usermanagement.entities.dto.CreateRoleRequest;
import com.mac.usermanagement.entities.dto.PermissionResponse;
import com.mac.usermanagement.entities.dto.RoleResponse;
import com.mac.usermanagement.entities.dto.UpdateRolePermissionsRequest;
import com.mac.usermanagement.service.RoleService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @PostMapping("/roles")
    @PreAuthorize(Role.ROLE_CREATE)
    public ResponseEntity<ResponseDTO<RoleResponse>> createRole(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreateRoleRequest request) {
        RoleResponse response = roleService.createRole(tenantId, request);
        return ResponseHelper.httpCreated(response,
                URI.create("/api/v1/tenants/" + tenantId + "/roles/" + response.roleId()));
    }

    @GetMapping("/roles")
    @PreAuthorize(Role.ROLE_VIEW)
    public ResponseEntity<ResponseDTO<List<RoleResponse>>> findRoles(@PathVariable UUID tenantId) {
        return ResponseHelper.httpOK(roleService.findRoles(tenantId));
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize(Role.ROLE_EDIT)
    public ResponseEntity<ResponseDTO<RoleResponse>> replacePermissions(
            @PathVariable UUID tenantId,
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        return ResponseHelper.httpOK(roleService.replacePermissions(tenantId, roleId, request.permissions()));
    }

    @PostMapping("/permissions")
    @PreAuthorize(Role.PERMISSION_CREATE)
    public ResponseEntity<ResponseDTO<PermissionResponse>> createPermission(
            @PathVariable UUID tenantId,
            @Valid @RequestBody CreatePermissionRequest request) {
        PermissionResponse response = roleService.createPermission(tenantId, request);
        return ResponseHelper.httpCreated(response,
                URI.create("/api/v1/tenants/" + tenantId + "/permissions/" + response.permissionId()));
    }

    @GetMapping("/permissions")
    @PreAuthorize(Role.PERMISSION_VIEW)
    public ResponseEntity<ResponseDTO<List<PermissionResponse>>> findPermissions(
            @PathVariable UUID tenantId) {
        return ResponseHelper.httpOK(roleService.findPermissions(tenantId));
    }
}
