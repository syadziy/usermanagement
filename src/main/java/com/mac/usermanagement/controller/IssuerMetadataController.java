package com.mac.usermanagement.controller;

import com.mac.usermanagement.config.properties.JwtProperties;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IssuerMetadataController {

    private final JwtProperties properties;
    private final RSAKey rsaKey;

    public IssuerMetadataController(JwtProperties properties, RSAKey rsaKey) {
        this.properties = properties;
        this.rsaKey = rsaKey;
    }

    @GetMapping(
            value = {"/.well-known/openid-configuration", "/.well-known/oauth-authorization-server"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> metadata() {
        String issuer = properties.normalizedIssuer();
        return Map.of(
                "issuer", issuer,
                "jwks_uri", issuer + "/oauth2/jwks",
                "subject_types_supported", List.of("public"),
                "id_token_signing_alg_values_supported", List.of("RS256"));
    }

    @GetMapping(value = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> jwks() {
        return new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }
}
