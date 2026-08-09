package com.mac.usermanagement.repository;

import com.mac.usermanagement.entities.model.UserAccess;
import com.mac.usermanagement.entities.model.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserRepository {

    UserAccount insert(UserAccount user);

    Optional<UserAccount> findByUsername(UUID tenantId, String username);

    Optional<UserAccount> findById(UUID tenantId, UUID userId);

    List<UserAccount> findAll(UUID tenantId);

    UserAccess findAccess(UUID tenantId, UUID userId);

    Map<UUID, UserAccess> findAccess(UUID tenantId, Set<UUID> userIds);

    void replaceRoles(UUID tenantId, UUID userId, Set<UUID> roleIds, Instant assignedAt);
}
