package com.windlabs.banking.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.provisioning.UserDetailsManager;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("dev")
public class DevUsersConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsManager userDetailsManager(
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.customer-one-password}") String passwordOne,
            @Value("${app.demo.customer-two-password}") String passwordTwo
    ) {
        return new InMemoryUserDetailsManager(
                User.withUsername("CUST001")
                        .password(passwordEncoder.encode(passwordOne))
                        .roles("CUSTOMER")
                        .build(),

                User.withUsername("CUST002")
                        .password(passwordEncoder.encode(passwordTwo))
                        .roles("CUSTOMER")
                        .build()
        );
    }
}