package com.mac.usermanagement.service.impl;

import com.mac.sdk_util.entities.constant.LogFields;
import com.mac.sdk_util.utils.StructuredLog;
import com.mac.usermanagement.config.properties.JwtProperties;
import com.mac.usermanagement.entities.constant.UserManagementLogFields;
import com.mac.usermanagement.entities.dto.LoginRequest;
import com.mac.usermanagement.entities.dto.LoginResponse;
import com.mac.usermanagement.entities.model.Tenant;
import com.mac.usermanagement.entities.model.UserAccess;
import com.mac.usermanagement.entities.model.UserAccount;
import com.mac.usermanagement.repository.TenantRepository;
import com.mac.usermanagement.repository.UserRepository;
import com.mac.usermanagement.service.AuthService;
import com.mac.usermanagement.utils.exception.InvalidCredentialsException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthServiceImpl.class);
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;
    private final Clock clock;

    public AuthServiceImpl(TenantRepository tenantRepository, UserRepository userRepository,
            PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder, JwtProperties jwtProperties, Clock clock) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
        this.clock = clock;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        Tenant tenant = tenantRepository.findByKey(request.tenantKey().trim().toLowerCase())
                .filter(Tenant::enabled).orElseThrow(InvalidCredentialsException::new);
        UserAccount user = userRepository.findByUsername(tenant.id(), request.username().trim())
                .filter(UserAccount::enabled).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        UserAccess access = userRepository.findAccess(tenant.id(), user.id());
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(tenant.accessTokenTtlSeconds());
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(jwtProperties.normalizedIssuer()).issuedAt(issuedAt)
                .notBefore(issuedAt).expiresAt(expiresAt).subject(user.id().toString())
                .claim("username", user.username())
                .claim("tenant_id", tenant.id().toString()).claim("tenant_key", tenant.tenantKey())
                .audience(jwtProperties.audiences())
                .claim("roles", access.roles()).claim("permissions", access.permissions())
                .claim("scope", access.permissions().stream()
                        .map(permission -> permission.replace(':', '.'))
                        .sorted()
                        .collect(Collectors.joining(" ")))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(jwtProperties.keyId()).build(), claims))
                .getTokenValue();
        StructuredLog.info(LOG, "User authenticated", Map.of(
                LogFields.EVENT_ACTION, "login", LogFields.EVENT_OUTCOME, LogFields.OUTCOME_SUCCESS,
                LogFields.EVENT_DATASET, "usermanagement.authentication",
                UserManagementLogFields.TENANT_ID, tenant.id(), UserManagementLogFields.USER_ID, user.id()));
        return new LoginResponse("Bearer", token, expiresAt, tenant.id(), user.id(),
                access.roles(), access.permissions());
    }
}
