package com.mac.usermanagement.service.impl;

import com.mac.sdk_util.exception.ResourceNotFoundException;
import com.mac.usermanagement.entities.dto.CreateUserRequest;
import com.mac.usermanagement.entities.dto.UserResponse;
import com.mac.usermanagement.entities.mapper.IdentityMapper;
import com.mac.usermanagement.entities.model.UserAccess;
import com.mac.usermanagement.entities.model.UserAccount;
import com.mac.usermanagement.repository.RoleRepository;
import com.mac.usermanagement.repository.UserRepository;
import com.mac.usermanagement.service.UserService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantAccessGuard tenantAccessGuard;
    private final Clock clock;

    public UserServiceImpl(UserRepository userRepository, RoleRepository roleRepository,
            PasswordEncoder passwordEncoder, TenantAccessGuard tenantAccessGuard, Clock clock) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantAccessGuard = tenantAccessGuard;
        this.clock = clock;
    }

    @Override
    @Transactional
    public UserResponse create(UUID tenantId, CreateUserRequest request) {
        tenantAccessGuard.require(tenantId);
        Instant now = clock.instant();
        UserAccount user = userRepository.insert(new UserAccount(UUID.randomUUID(), tenantId,
                request.username().trim(), request.email().trim().toLowerCase(),
                passwordEncoder.encode(request.password()), true, now, now));
        return IdentityMapper.toResponse(user, Set.of());
    }

    @Override
    public List<UserResponse> findAll(UUID tenantId, int limit, int offset) {
        tenantAccessGuard.require(tenantId);
        List<UserAccount> users = userRepository.findAll(tenantId, limit, offset);
        Set<UUID> userIds = users.stream().map(UserAccount::id).collect(java.util.stream.Collectors.toSet());
        Map<UUID, UserAccess> accessByUser = userRepository.findAccess(tenantId, userIds);
        return users.stream().map(user -> {
            UserAccess access = accessByUser.getOrDefault(user.id(), new UserAccess(Set.of(), Set.of()));
            return IdentityMapper.toResponse(user, access.roles());
        }).toList();
    }

    @Override
    public long count(UUID tenantId) {
        tenantAccessGuard.require(tenantId);
        return userRepository.count(tenantId);
    }

    @Override
    @Transactional
    public UserResponse assignRoles(UUID tenantId, UUID userId, Set<UUID> roleIds) {
        tenantAccessGuard.require(tenantId);
        UserAccount user = userRepository.findById(tenantId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!roleRepository.allExist(tenantId, roleIds)) {
            throw new IllegalArgumentException("One or more roles do not belong to this tenant");
        }
        userRepository.replaceRoles(tenantId, userId, Set.copyOf(roleIds), clock.instant());
        return IdentityMapper.toResponse(user, userRepository.findAccess(tenantId, userId).roles());
    }
}
