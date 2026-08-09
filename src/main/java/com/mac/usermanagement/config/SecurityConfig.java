package com.mac.usermanagement.config;

import com.mac.sdk_util.helper.ResponseHelper;
import com.mac.usermanagement.config.properties.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    RSAKey jwtRsaKey(JwtProperties properties) {
        return JwtKeyFactory.create(properties);
    }

    @Bean
    JwtEncoder jwtEncoder(RSAKey rsaKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsaKey)));
    }

    @Bean
    JwtDecoder jwtDecoder(RSAKey rsaKey, JwtProperties properties) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(properties.normalizedIssuer()),
                new JwtClaimValidator<String>("tenant_id", value -> value != null && !value.isBlank()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/internal/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/tenants").permitAll()
                        .requestMatchers("/.well-known/openid-configuration",
                                "/.well-known/oauth-authorization-server", "/oauth2/jwks").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info/**", "/v3/api-docs/**",
                                "/swagger-ui.html", "/swagger-ui/**", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint((request, response, exception) -> {
                            response.setStatus(401);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), ResponseHelper.unauthorizedBody());
                        })
                        .accessDeniedHandler((request, response, exception) -> {
                            response.setStatus(403);
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            objectMapper.writeValue(response.getOutputStream(), ResponseHelper.forbiddenBody());
                        }));
        return http.build();
    }

    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            List<String> roles = jwt.getClaimAsStringList("roles");
            List<String> permissions = jwt.getClaimAsStringList("permissions");
            if (roles != null) {
                roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).forEach(authorities::add);
            }
            if (permissions != null) {
                permissions.stream()
                        .map(permission -> new SimpleGrantedAuthority("PERM_" + permission))
                        .forEach(authorities::add);
            }
            return authorities;
        });
        converter.setPrincipalClaimName("username");
        return converter;
    }
}
