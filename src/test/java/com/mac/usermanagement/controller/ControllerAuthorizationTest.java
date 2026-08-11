package com.mac.usermanagement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.mac.usermanagement.service.impl.RoleServiceImpl;
import com.mac.usermanagement.service.impl.TenantServiceImpl;
import com.mac.usermanagement.service.impl.UserServiceImpl;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class ControllerAuthorizationTest {

    @Test
    void protectedEndpointsDeclareTheirPermissionOnTheController() {
        assertPermission(UserController.class, "create", "PERM_user:create");
        assertPermission(UserController.class, "findAll", "PERM_user:view");
        assertPermission(UserController.class, "assignRoles", "PERM_role:assign");
        assertPermission(RoleController.class, "createRole", "PERM_role:create");
        assertPermission(RoleController.class, "findRoles", "PERM_role:view");
        assertPermission(RoleController.class, "replacePermissions", "PERM_role:edit");
        assertPermission(RoleController.class, "createPermission", "PERM_permission:create");
        assertPermission(RoleController.class, "findPermissions", "PERM_permission:view");
        assertPermission(TenantController.class, "findAll", "PERM_tenant:view");
        assertPermission(TenantController.class, "updateTokenPolicy", "PERM_tenant:update");
    }

    @Test
    void serviceImplementationsDoNotDeclareHttpEndpointAuthorization() {
        assertNoPreAuthorize(UserServiceImpl.class);
        assertNoPreAuthorize(RoleServiceImpl.class);
        assertNoPreAuthorize(TenantServiceImpl.class);
    }

    private static void assertPermission(Class<?> controller, String methodName, String permission) {
        PreAuthorize annotation = method(controller, methodName).getAnnotation(PreAuthorize.class);
        assertEquals("hasAuthority('" + permission + "')", annotation.value());
    }

    private static void assertNoPreAuthorize(Class<?> service) {
        Arrays.stream(service.getDeclaredMethods())
                .forEach(method -> assertNull(method.getAnnotation(PreAuthorize.class)));
    }

    private static Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
