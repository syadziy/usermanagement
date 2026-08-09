package com.mac.usermanagement.service;

import com.mac.usermanagement.entities.dto.CreateUserRequest;
import com.mac.usermanagement.entities.dto.UserResponse;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface UserService {

    UserResponse create(UUID tenantId, CreateUserRequest request);

    List<UserResponse> findAll(UUID tenantId);

    UserResponse assignRoles(UUID tenantId, UUID userId, Set<UUID> roleIds);
}
