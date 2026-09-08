package com.windlabs.banking.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.provisioning.UserDetailsManager;

import javax.sql.DataSource;

@Configuration
public class DevUsersConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public UserDetailsManager userDetailsManager(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }

    @Bean
    @Profile("dev")
    public ApplicationRunner seedDemoUsers(
            UserDetailsManager users,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.customer-one-password}") String passwordOne,
            @Value("${app.demo.customer-two-password}") String passwordTwo
    ) {
        return args -> {
            if (!users.userExists("CUST001")) {
                users.createUser(
                        User.withUsername("CUST001")
                                .password(passwordEncoder.encode(passwordOne))
                                .roles("CUSTOMER")
                                .build()
                );
            }

            if (!users.userExists("CUST002")) {
                users.createUser(
                        User.withUsername("CUST002")
                                .password(passwordEncoder.encode(passwordTwo))
                                .roles("CUSTOMER")
                                .build()
                );
            }
        };
    }
}