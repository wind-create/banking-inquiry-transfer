package com.windlabs.banking.auth.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.time.Instant;

@Configuration(proxyBeanMethods = false)
public class OAuth2ClientConfig {

    @Bean
    public RegisteredClientRepository registeredClientRepository(
            JdbcOperations jdbcOperations
    ) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    @Profile("dev")
    public ApplicationRunner seedPostmanClient(
            RegisteredClientRepository clients
    ) {
        return args -> {
            if (clients.findByClientId("banking-postman") != null) {
                return;
            }

            RegisteredClient client = RegisteredClient
                    .withId("banking-postman-dev")
                    .clientId("banking-postman")
                    .clientIdIssuedAt(Instant.now())
                    .clientName("Banking Postman Development")
                    .clientAuthenticationMethod(
                            ClientAuthenticationMethod.NONE
                    )
                    .authorizationGrantType(
                            AuthorizationGrantType.AUTHORIZATION_CODE
                    )
                    .redirectUri(
                            "https://oauth.pstmn.io/v1/browser-callback"
                    )
                    .scope("account.read")
                    .scope("transaction.read")
                    .scope("transfer.write")
                    .clientSettings(ClientSettings.builder()
                            .requireProofKey(true)
                            .requireAuthorizationConsent(true)
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofMinutes(10))
                            .build())
                    .build();

            clients.save(client);
        };
    }
}