package com.mac.usermanagement.config;

import com.mac.usermanagement.config.properties.JwtProperties;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class JwtKeyFactory {

    private static final int RSA_KEY_SIZE = 3072;

    private JwtKeyFactory() {}

    static RSAKey create(JwtProperties properties) {
        try {
            validateKeyPairPresence(properties);
            KeyPair keyPair = hasConfiguredKeyPair(properties)
                    ? decode(properties.privateKey(), properties.publicKey())
                    : generate(properties);
            return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                    .privateKey((RSAPrivateKey) keyPair.getPrivate())
                    .keyID(properties.keyId())
                    .keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.RS256)
                    .build();
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("JWT RSA key configuration is invalid", exception);
        }
    }

    private static boolean hasConfiguredKeyPair(JwtProperties properties) {
        return properties.privateKey() != null && !properties.privateKey().isBlank()
                && properties.publicKey() != null && !properties.publicKey().isBlank();
    }

    private static void validateKeyPairPresence(JwtProperties properties) {
        boolean hasPrivateKey = properties.privateKey() != null && !properties.privateKey().isBlank();
        boolean hasPublicKey = properties.publicKey() != null && !properties.publicKey().isBlank();
        if (hasPrivateKey != hasPublicKey) {
            throw new IllegalArgumentException("JWT private and public keys must be configured together");
        }
    }

    private static KeyPair decode(String privateKey, String publicKey) throws GeneralSecurityException {
        KeyFactory factory = KeyFactory.getInstance("RSA");
        RSAPrivateKey privateRsaKey = (RSAPrivateKey) factory.generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKey.trim())));
        RSAPublicKey publicRsaKey = (RSAPublicKey) factory.generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKey.trim())));
        if (!privateRsaKey.getModulus().equals(publicRsaKey.getModulus())) {
            throw new IllegalArgumentException("JWT private and public keys do not match");
        }
        return new KeyPair(publicRsaKey, privateRsaKey);
    }

    private static KeyPair generate(JwtProperties properties) throws GeneralSecurityException {
        if (!properties.generateEphemeralKey()) {
            throw new IllegalArgumentException("JWT RSA keys are required");
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        return generator.generateKeyPair();
    }
}
