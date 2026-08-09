package com.mac.usermanagement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mac.usermanagement.controller.*;
import com.mac.usermanagement.entities.dto.*;
import com.mac.usermanagement.service.*;
import com.mac.usermanagement.utils.exception.IdentityConflictException;
import com.mac.usermanagement.utils.exception.InvalidCredentialsException;
import com.mac.usermanagement.utils.handler.UserManagementExceptionHandler;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;

class ControllerAndHandlerTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ROLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final Instant NOW = Instant.parse("2026-08-09T15:00:00Z");

    @Test
    void authAndTenantControllersDelegate() {
        AuthService auth = mock(AuthService.class);
        LoginRequest loginRequest = new LoginRequest("acme-id", "owner", "strong-password");
        LoginResponse loginResponse = new LoginResponse("Bearer", "token", NOW, TENANT_ID, USER_ID,
                Set.of("OWNER"), Set.of("tenant:update"));
        when(auth.login(loginRequest)).thenReturn(loginResponse);
        assertSame(loginResponse, new AuthController(auth).login(loginRequest).getBody().getData());

        TenantService tenants = mock(TenantService.class);
        RegisterTenantRequest register = new RegisterTenantRequest(
                "acme-id", "ACME", 1800, "owner", "owner@example.com", "strong-password");
        TenantResponse tenantResponse = new TenantResponse(TENANT_ID, "acme-id", "ACME", 1800, USER_ID, NOW);
        when(tenants.register(register)).thenReturn(tenantResponse);
        TenantController controller = new TenantController(tenants);
        var created = controller.register(register);
        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertEquals("/api/v1/tenants/" + TENANT_ID, created.getHeaders().getLocation().toString());
        assertEquals(3600L, controller.updateTokenPolicy(
                TENANT_ID, new UpdateTokenPolicyRequest(3600)).getBody().getData().get("accessTokenTtlSeconds"));
        verify(tenants).updateTokenPolicy(TENANT_ID, 3600);
    }

    @Test
    void userControllerDelegates() {
        UserService service = mock(UserService.class);
        UserController controller = new UserController(service);
        CreateUserRequest request = new CreateUserRequest("operator", "operator@example.com", "strong-password");
        UserResponse response = user();
        when(service.create(TENANT_ID, request)).thenReturn(response);
        when(service.findAll(TENANT_ID)).thenReturn(List.of(response));
        when(service.assignRoles(TENANT_ID, USER_ID, Set.of(ROLE_ID))).thenReturn(response);
        assertEquals(HttpStatus.CREATED, controller.create(TENANT_ID, request).getStatusCode());
        assertEquals(1, controller.findAll(TENANT_ID).getBody().getData().size());
        assertSame(response, controller.assignRoles(
                TENANT_ID, USER_ID, new AssignRolesRequest(Set.of(ROLE_ID))).getBody().getData());
    }

    @Test
    void roleControllerDelegates() {
        RoleService service = mock(RoleService.class);
        RoleController controller = new RoleController(service);
        CreateRoleRequest roleRequest = new CreateRoleRequest("REPORT_VIEWER", null, Set.of("report:view"));
        RoleResponse role = new RoleResponse(ROLE_ID, TENANT_ID, "REPORT_VIEWER", null,
                false, Set.of("report:view"), NOW);
        when(service.createRole(TENANT_ID, roleRequest)).thenReturn(role);
        when(service.findRoles(TENANT_ID)).thenReturn(List.of(role));
        when(service.replacePermissions(TENANT_ID, ROLE_ID, Set.of("report:download"))).thenReturn(role);
        assertEquals(HttpStatus.CREATED, controller.createRole(TENANT_ID, roleRequest).getStatusCode());
        assertEquals(1, controller.findRoles(TENANT_ID).getBody().getData().size());
        assertSame(role, controller.replacePermissions(TENANT_ID, ROLE_ID,
                new UpdateRolePermissionsRequest(Set.of("report:download"))).getBody().getData());

        CreatePermissionRequest permissionRequest = new CreatePermissionRequest("report", "view", null);
        PermissionResponse permission = new PermissionResponse(UUID.randomUUID(), "report", "view",
                "report:view", null);
        when(service.createPermission(TENANT_ID, permissionRequest)).thenReturn(permission);
        when(service.findPermissions(TENANT_ID)).thenReturn(List.of(permission));
        assertEquals(HttpStatus.CREATED,
                controller.createPermission(TENANT_ID, permissionRequest).getStatusCode());
        assertEquals(1, controller.findPermissions(TENANT_ID).getBody().getData().size());
    }

    @Test
    void domainHandlerMapsConflictUnauthorizedAndForbidden() {
        UserManagementExceptionHandler handler = new UserManagementExceptionHandler();
        var conflict = handler.handleConflict(new IdentityConflictException("duplicate"));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        assertEquals("duplicate", conflict.getBody().getData().get("reason"));
        assertEquals(HttpStatus.UNAUTHORIZED,
                handler.handleInvalidCredentials(new InvalidCredentialsException()).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN,
                handler.handleAccessDenied(new AccessDeniedException("denied")).getStatusCode());
    }

    private static UserResponse user() {
        return new UserResponse(USER_ID, TENANT_ID, "operator", "operator@example.com",
                true, Set.of(), NOW);
    }
}
