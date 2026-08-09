package com.mac.usermanagement.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mac.usermanagement.config.properties.JwtProperties;
import com.mac.usermanagement.controller.IssuerMetadataController;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

class JwtIssuerConfigurationTest {

    @Test
    void generatesEphemeralRsaKeyAndPublishesOnlyPublicMaterial() throws Exception {
        JwtProperties properties = properties(null, null, true);
        RSAKey key = JwtKeyFactory.create(properties);
        SecurityConfig configuration = new SecurityConfig();
        JwtEncoder encoder = configuration.jwtEncoder(key);
        JwtDecoder decoder = configuration.jwtDecoder(key, properties);
        IssuerMetadataController controller = new IssuerMetadataController(properties, key);

        assertNotNull(encoder);
        assertNotNull(decoder);
        Instant now = Instant.now();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).keyId(properties.keyId()).build(),
                JwtClaimsSet.builder().issuer(properties.normalizedIssuer()).subject("user-id")
                        .issuedAt(now).expiresAt(now.plusSeconds(60)).claim("tenant_id", "tenant-id")
                        .audience(properties.audiences()).build())).getTokenValue();
        assertEquals("user-id", decoder.decode(token).getSubject());
        assertEquals("http://issuer.example", controller.metadata().get("issuer"));
        assertEquals("http://issuer.example/oauth2/jwks", controller.metadata().get("jwks_uri"));
        Map<String, Object> jwks = controller.jwks();
        assertTrue(jwks.containsKey("keys"));
        assertFalse(jwks.toString().contains("\"d\""));
    }

    @Test
    void decodesConfiguredMatchingKeysAndRejectsInvalidMaterial() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());

        RSAKey key = JwtKeyFactory.create(properties(privateKey, publicKey, false));
        assertEquals("test-key", key.getKeyID());
        assertThrows(IllegalStateException.class,
                () -> JwtKeyFactory.create(properties("invalid", "invalid", false)));
        assertThrows(IllegalStateException.class,
                () -> JwtKeyFactory.create(properties(privateKey, null, true)));
    }

    @Test
    void validatesKeyConfiguration() {
        assertTrue(properties(null, null, true).isKeyConfigurationValid());
        assertFalse(properties(null, null, false).isKeyConfigurationValid());
        assertTrue(properties("private", "public", false).isKeyConfigurationValid());
        assertFalse(properties("private", null, true).isKeyConfigurationValid());
    }

    private static JwtProperties properties(String privateKey, String publicKey, boolean ephemeral) {
        return new JwtProperties("http://issuer.example/", "test-key", privateKey, publicKey,
                ephemeral, List.of("api-gateway"));
    }
}
