package com.windlabs.banking.auth.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.interfaces.RSAPublicKey;

@Configuration(proxyBeanMethods = false)
public class JwtKeyConfig {

    @Bean
    public JWKSource<SecurityContext> jwkSource(
            @Value("${app.security.jwt.key-store}") Resource keyStoreResource,
            @Value("${app.security.jwt.key-store-password}") String password,
            @Value("${app.security.jwt.key-alias}") String alias,
            @Value("${app.security.jwt.key-id}") String keyId
    ) throws Exception {

        char[] keyPassword = password.toCharArray();

        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        try (InputStream inputStream = keyStoreResource.getInputStream()) {
            keyStore.load(inputStream, keyPassword);
        }

        Key key = keyStore.getKey(alias, keyPassword);

        if (!(key instanceof PrivateKey privateKey)) {
            throw new IllegalStateException(
                    "JWT private key not found: " + alias
            );
        }

        Certificate certificate = keyStore.getCertificate(alias);

        if (certificate == null
                || !(certificate.getPublicKey() instanceof RSAPublicKey publicKey)) {
            throw new IllegalStateException(
                    "JWT RSA public key not found: " + alias
            );
        }

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .keyUse(KeyUse.SIGNATURE)
                .algorithm(JWSAlgorithm.RS256)
                .build();

        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(
            JWKSource<SecurityContext> jwkSource
    ) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }
}