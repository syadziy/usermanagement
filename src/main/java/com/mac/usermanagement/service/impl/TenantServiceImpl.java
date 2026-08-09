package com.mac.usermanagement.service.impl;

import static com.mac.usermanagement.entities.constant.AuthorizationCatalog.DEFAULT_PERMISSIONS;
import static com.mac.usermanagement.entities.constant.AuthorizationCatalog.OWNER_ROLE;

import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.sdk_util.utils.StructuredLog;
import com.mac.usermanagement.config.properties.RegistrationProperties;
import com.mac.usermanagement.entities.constant.UserManagementLogFields;
import com.mac.usermanagement.entities.dto.RegisterTenantRequest;
import com.mac.usermanagement.entities.dto.TenantResponse;
import com.mac.usermanagement.entities.model.Role;
import com.mac.usermanagement.entities.model.Tenant;
import com.mac.usermanagement.entities.model.UserAccount;
import com.mac.usermanagement.repository.RoleRepository;
import com.mac.usermanagement.repository.TenantRepository;
import com.mac.usermanagement.repository.UserRepository;
import com.mac.usermanagement.service.TenantService;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantServiceImpl implements TenantService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantServiceImpl.class);
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistrationProperties registrationProperties;
    private final TenantAccessGuard tenantAccessGuard;
    private final Clock clock;

    public TenantServiceImpl(TenantRepository tenantRepository, UserRepository userRepository,
            RoleRepository roleRepository, PasswordEncoder passwordEncoder,
            RegistrationProperties registrationProperties, TenantAccessGuard tenantAccessGuard, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.registrationProperties = registrationProperties;
        this.tenantAccessGuard = tenantAccessGuard;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TenantResponse register(RegisterTenantRequest request) {
        if (!registrationProperties.enabled()) {
            throw new AccessDeniedException("Tenant registration is disabled");
        }
        Instant now = clock.instant();
        Tenant tenant = tenantRepository.insert(new Tenant(UUID.randomUUID(),
                request.tenantKey().trim().toLowerCase(), request.tenantName().trim(),
                request.accessTokenTtlSeconds(), true, now));
        roleRepository.seedPermissions(tenant.id(), DEFAULT_PERMISSIONS, now);
        Role ownerRole = roleRepository.insert(new Role(UUID.randomUUID(), tenant.id(), OWNER_ROLE,
                "Full tenant administration access", true, Set.of(), now));
        roleRepository.assignAllPermissions(tenant.id(), ownerRole.id(), now);
        UserAccount owner = userRepository.insert(new UserAccount(UUID.randomUUID(), tenant.id(),
                request.ownerUsername().trim(), request.ownerEmail().trim().toLowerCase(),
                passwordEncoder.encode(request.ownerPassword()), true, now, now));
        userRepository.replaceRoles(tenant.id(), owner.id(), Set.of(ownerRole.id()), now);
        StructuredLog.info(LOG, "Tenant registered", Map.of(
                LogFields.EVENT_ACTION, "registerTenant", LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS,
                LogFields.EVENT_DATASET, "usermanagement.tenant",
                UserManagementLogFields.TENANT_ID, tenant.id(), UserManagementLogFields.USER_ID, owner.id()));
        return new TenantResponse(tenant.id(), tenant.tenantKey(), tenant.name(),
                tenant.accessTokenTtlSeconds(), owner.id(), now);
    }

    @Override
    @Transactional
    public void updateTokenPolicy(UUID tenantId, long ttlSeconds) {
        tenantAccessGuard.require(tenantId);
        tenantRepository.updateTokenTtl(tenantId, ttlSeconds);
        StructuredLog.info(LOG, "Tenant token policy updated", Map.of(
                LogFields.EVENT_ACTION, "updateTokenPolicy", LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS,
                LogFields.EVENT_DATASET, "usermanagement.tenant",
                UserManagementLogFields.TENANT_ID, tenantId, "token.ttl_seconds", ttlSeconds));
    }
}
