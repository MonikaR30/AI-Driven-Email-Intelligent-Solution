package com.example.emailintelligence.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration
 * 
 * This configuration disables default Spring Security behavior
 * so that our REST API endpoints remain accessible.
 * 
 * We're using BCryptPasswordEncoder for password hashing only,
 * not for endpoint security.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF (Cross-Site Request Forgery) protection
            // This is safe for REST APIs that don't use cookies for auth
            .csrf(csrf -> csrf.disable())
            
            // Allow all requests without authentication
            // In production, you might want to protect specific endpoints
            .authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll()
            );
        
        return http.build();
    }
}