package com.mac.usermanagement.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mac.sdk_util.exception.ResourceNotFoundException;
import com.mac.usermanagement.config.properties.JwtProperties;
import com.mac.usermanagement.config.properties.RegistrationProperties;
import com.mac.usermanagement.entities.dto.*;
import com.mac.usermanagement.entities.model.*;
import com.mac.usermanagement.repository.*;
import com.mac.usermanagement.utils.exception.InvalidCredentialsException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class ServiceCoverageTest {

    private static final Instant NOW = Instant.parse("2026-08-09T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ROLE_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registersTenantOwnerAndUpdatesPolicy() {
        TenantRepository tenants = mock(TenantRepository.class);
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TenantAccessGuard guard = mock(TenantAccessGuard.class);
        when(encoder.encode("strong-password")).thenReturn("hash");
        when(tenants.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roles.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TenantServiceImpl service = new TenantServiceImpl(tenants, users, roles, encoder,
                new RegistrationProperties(true), guard, CLOCK);

        TenantResponse response = service.register(new RegisterTenantRequest(
                " ACME-id ".trim().toLowerCase(), " ACME Indonesia ", 1800,
                " owner.user ", " OWNER@EXAMPLE.COM ", "strong-password"));

        assertEquals("acme-id", response.tenantKey());
        assertEquals("ACME Indonesia", response.tenantName());
        verify(roles).seedPermissions(eq(response.tenantId()), anyList(), eq(NOW));
        verify(roles).assignAllPermissions(eq(response.tenantId()), any(UUID.class), eq(NOW));
        verify(users).replaceRoles(eq(response.tenantId()), eq(response.ownerUserId()), anySet(), eq(NOW));

        service.updateTokenPolicy(TENANT_ID, 3600);
        verify(guard).require(TENANT_ID);
        verify(tenants).updateTokenTtl(TENANT_ID, 3600);
    }

    @Test
    void rejectsRegistrationWhenDisabled() {
        TenantServiceImpl service = new TenantServiceImpl(mock(TenantRepository.class),
                mock(UserRepository.class), mock(RoleRepository.class), mock(PasswordEncoder.class),
                new RegistrationProperties(false), mock(TenantAccessGuard.class), CLOCK);
        RegisterTenantRequest request = new RegisterTenantRequest(
                "acme-id", "ACME", 300, "owner", "owner@example.com", "strong-password");
        assertThrows(AccessDeniedException.class, () -> service.register(request));
    }

    @Test
    void logsInWithTenantSpecificExpiryAndRejectsInvalidCredentials() {
        TenantRepository tenants = mock(TenantRepository.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        JwtEncoder jwtEncoder = mock(JwtEncoder.class);
        Tenant tenant = tenant();
        UserAccount user = user();
        when(tenants.findByKey("acme-id")).thenReturn(Optional.of(tenant));
        when(users.findByUsername(TENANT_ID, "owner")).thenReturn(Optional.of(user));
        when(encoder.matches("strong-password", "hash")).thenReturn(true);
        when(users.findAccess(TENANT_ID, USER_ID))
                .thenReturn(new UserAccess(Set.of("TENANT_OWNER"), Set.of("tenant:update")));
        when(jwtEncoder.encode(any())).thenReturn(Jwt.withTokenValue("signed-token")
                .header("alg", "HS256").subject(USER_ID.toString()).issuedAt(NOW)
                .expiresAt(NOW.plusSeconds(1800)).build());
        AuthServiceImpl service = new AuthServiceImpl(tenants, users, encoder, jwtEncoder,
                new JwtProperties("http://issuer", "12345678901234567890123456789012"), CLOCK);

        LoginResponse response = service.login(new LoginRequest(" ACME-ID ", " owner ", "strong-password"));
        assertEquals("signed-token", response.accessToken());
        assertEquals(NOW.plusSeconds(1800), response.expiresAt());
        assertEquals(Set.of("tenant:update"), response.permissions());

        when(tenants.findByKey("missing")).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("missing", "owner", "strong-password")));
        when(tenants.findByKey("acme-id")).thenReturn(Optional.of(tenant));
        when(users.findByUsername(TENANT_ID, "missing")).thenReturn(Optional.empty());
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("acme-id", "missing", "strong-password")));
        when(encoder.matches("bad-password", "hash")).thenReturn(false);
        assertThrows(InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("acme-id", "owner", "bad-password")));
    }

    @Test
    void createsListsAndAssignsUsers() {
        UserRepository users = mock(UserRepository.class);
        RoleRepository roles = mock(RoleRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        TenantAccessGuard guard = mock(TenantAccessGuard.class);
        when(encoder.encode(anyString())).thenReturn("hash");
        when(users.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        UserServiceImpl service = new UserServiceImpl(users, roles, encoder, guard, CLOCK);

        UserResponse created = service.create(TENANT_ID,
                new CreateUserRequest(" operator ", " OPERATOR@EXAMPLE.COM ", "strong-password"));
        assertEquals("operator", created.username());
        assertEquals("operator@example.com", created.email());

        when(users.findAll(TENANT_ID)).thenReturn(List.of(user()));
        UserAccess viewerAccess = new UserAccess(Set.of("VIEWER"), Set.of("user:view"));
        when(users.findAccess(TENANT_ID, Set.of(USER_ID))).thenReturn(Map.of(USER_ID, viewerAccess));
        when(users.findAccess(TENANT_ID, USER_ID)).thenReturn(viewerAccess);
        assertEquals(Set.of("VIEWER"), service.findAll(TENANT_ID).getFirst().roles());

        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Optional.of(user()));
        when(roles.allExist(TENANT_ID, Set.of(ROLE_ID))).thenReturn(true);
        assertEquals(Set.of("VIEWER"), service.assignRoles(TENANT_ID, USER_ID, Set.of(ROLE_ID)).roles());
        verify(users).replaceRoles(TENANT_ID, USER_ID, Set.of(ROLE_ID), NOW);

        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.assignRoles(TENANT_ID, USER_ID, Set.of(ROLE_ID)));
        when(users.findById(TENANT_ID, USER_ID)).thenReturn(Optional.of(user()));
        when(roles.allExist(TENANT_ID, Set.of(ROLE_ID))).thenReturn(false);
        assertThrows(IllegalArgumentException.class,
                () -> service.assignRoles(TENANT_ID, USER_ID, Set.of(ROLE_ID)));
    }

    @Test
    void managesRolesAndPermissions() {
        RoleRepository roles = mock(RoleRepository.class);
        TenantAccessGuard guard = mock(TenantAccessGuard.class);
        RoleServiceImpl service = new RoleServiceImpl(roles, guard, CLOCK);
        when(roles.insert(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roles.insertPermission(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoleResponse created = service.createRole(TENANT_ID,
                new CreateRoleRequest("REPORT_VIEWER", " reports ", Set.of("report:view")));
        assertEquals("reports", created.description());
        assertEquals(Set.of("report:view"), created.permissions());

        Role role = new Role(ROLE_ID, TENANT_ID, "REPORT_VIEWER", null, false,
                Set.of("report:view"), NOW);
        when(roles.findAll(TENANT_ID)).thenReturn(List.of(role));
        assertEquals("REPORT_VIEWER", service.findRoles(TENANT_ID).getFirst().name());
        when(roles.findById(TENANT_ID, ROLE_ID)).thenReturn(Optional.of(role));
        assertEquals(Set.of("report:download"),
                service.replacePermissions(TENANT_ID, ROLE_ID, Set.of("report:download")).permissions());
        when(roles.findById(TENANT_ID, ROLE_ID)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.replacePermissions(TENANT_ID, ROLE_ID, Set.of()));

        PermissionResponse permission = service.createPermission(TENANT_ID,
                new CreatePermissionRequest(" Report ", " Download ", " download reports "));
        assertEquals("report:download", permission.authority());
        Permission model = new Permission(permission.permissionId(), TENANT_ID,
                "report", "download", null, NOW);
        when(roles.findPermissions(TENANT_ID)).thenReturn(List.of(model));
        assertEquals("report:download", service.findPermissions(TENANT_ID).getFirst().authority());

        RoleResponse withoutPermissions = service.createRole(TENANT_ID,
                new CreateRoleRequest("EMPTY_ROLE", " ", null));
        assertNull(withoutPermissions.description());
        assertTrue(withoutPermissions.permissions().isEmpty());
    }

    @Test
    void tenantGuardAcceptsMatchingJwtAndRejectsInvalidContext() {
        TenantAccessGuard guard = new TenantAccessGuard();
        Jwt matching = jwt(TENANT_ID.toString());
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(matching, null));
        assertDoesNotThrow(() -> guard.require(TENANT_ID));

        SecurityContextHolder.clearContext();
        assertThrows(AccessDeniedException.class, () -> guard.require(TENANT_ID));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(jwt(UUID.randomUUID().toString()), null));
        assertThrows(AccessDeniedException.class, () -> guard.require(TENANT_ID));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt("bad"), null));
        assertThrows(AccessDeniedException.class, () -> guard.require(TENANT_ID));
    }

    private static Tenant tenant() {
        return new Tenant(TENANT_ID, "acme-id", "ACME", 1800, true, NOW);
    }

    private static UserAccount user() {
        return new UserAccount(USER_ID, TENANT_ID, "owner", "owner@example.com", "hash", true, NOW, NOW);
    }

    private static Jwt jwt(String tenantId) {
        return Jwt.withTokenValue("token").header("alg", "HS256").subject(USER_ID.toString())
                .issuedAt(NOW).expiresAt(NOW.plusSeconds(60)).claim("tenant_id", tenantId).build();
    }
}
