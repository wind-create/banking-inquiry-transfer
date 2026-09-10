package com.windlabs.banking.transaction.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditConfig {

    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> {
            Authentication authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            if (authentication == null || !authentication.isAuthenticated()) {
                return Optional.of("SYSTEM");
            }

            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                String subject = jwtAuth.getToken().getSubject();

                if (subject != null && !subject.isBlank()) {
                    return Optional.of(subject);
                }
            }

            String actor = authentication.getName();

            if (actor == null || actor.isBlank()
                    || "anonymousUser".equals(actor)) {
                return Optional.of("SYSTEM");
            }

            return Optional.of(actor);
        };
    }
}