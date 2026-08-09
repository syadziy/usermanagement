package com.mac.usermanagement.service.impl;

import com.mac.sdk_util.exception.ResourceNotFoundException;
import com.mac.usermanagement.entities.dto.CreatePermissionRequest;
import com.mac.usermanagement.entities.dto.CreateRoleRequest;
import com.mac.usermanagement.entities.dto.PermissionResponse;
import com.mac.usermanagement.entities.dto.RoleResponse;
import com.mac.usermanagement.entities.mapper.IdentityMapper;
import com.mac.usermanagement.entities.model.Permission;
import com.mac.usermanagement.entities.model.Role;
import com.mac.usermanagement.repository.RoleRepository;
import com.mac.usermanagement.service.RoleService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final TenantAccessGuard tenantAccessGuard;
    private final Clock clock;

    public RoleServiceImpl(RoleRepository roleRepository, TenantAccessGuard tenantAccessGuard, Clock clock) {
        this.roleRepository = roleRepository;
        this.tenantAccessGuard = tenantAccessGuard;
        this.clock = clock;
    }

    @Override
    @Transactional
    public RoleResponse createRole(UUID tenantId, CreateRoleRequest request) {
        tenantAccessGuard.require(tenantId);
        Instant now = clock.instant();
        Role role = roleRepository.insert(new Role(UUID.randomUUID(), tenantId, request.name().trim(),
                normalize(request.description()), false, Set.of(), now));
        Set<String> permissions = request.permissions() == null ? Set.of() : Set.copyOf(request.permissions());
        roleRepository.replacePermissions(tenantId, role.id(), permissions, now);
        return IdentityMapper.toResponse(new Role(role.id(), role.tenantId(), role.name(), role.description(),
                role.systemRole(), permissions, role.createdAt()));
    }

    @Override
    public List<RoleResponse> findRoles(UUID tenantId) {
        tenantAccessGuard.require(tenantId);
        return roleRepository.findAll(tenantId).stream().map(IdentityMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public RoleResponse replacePermissions(UUID tenantId, UUID roleId, Set<String> permissions) {
        tenantAccessGuard.require(tenantId);
        Role role = roleRepository.findById(tenantId, roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found"));
        roleRepository.replacePermissions(tenantId, roleId, Set.copyOf(permissions), clock.instant());
        return IdentityMapper.toResponse(new Role(role.id(), role.tenantId(), role.name(), role.description(),
                role.systemRole(), Set.copyOf(permissions), role.createdAt()));
    }

    @Override
    public PermissionResponse createPermission(UUID tenantId, CreatePermissionRequest request) {
        tenantAccessGuard.require(tenantId);
        Permission permission = roleRepository.insertPermission(new Permission(UUID.randomUUID(), tenantId,
                request.resource().trim().toLowerCase(), request.action().trim().toLowerCase(),
                normalize(request.description()), clock.instant()));
        return IdentityMapper.toResponse(permission);
    }

    @Override
    public List<PermissionResponse> findPermissions(UUID tenantId) {
        tenantAccessGuard.require(tenantId);
        return roleRepository.findPermissions(tenantId).stream().map(IdentityMapper::toResponse).toList();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
