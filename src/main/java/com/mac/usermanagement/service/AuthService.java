package com.mac.usermanagement.service;

import com.mac.usermanagement.entities.dto.LoginRequest;
import com.mac.usermanagement.entities.dto.LoginResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);
}
