package com.facultyconnect.feculty_backend.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for the REST API.
 *
 * Key decisions:
 *   - Stateless sessions (no cookies/sessions — JWT only)
 *   - CSRF disabled (safe for stateless REST APIs that use Bearer tokens)
 *   - No form login or HTTP basic
 *   - Public: /api/auth/register, /api/auth/login, /api/health
 *   - Protected: everything else under /api/**
 *   - JwtAuthenticationFilter runs before Spring's default auth filter
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // --- Disable CSRF (REST API uses Bearer tokens, not cookies) ---
            .csrf(csrf -> csrf.disable())

            // --- Stateless sessions (no server-side session; JWT is the session) ---
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // --- Endpoint access rules ---
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/health").permitAll()
                .requestMatchers("/ws", "/ws/**").permitAll()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll()
            )

            // --- Return 401 JSON for unauthenticated requests to protected endpoints ---
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"error\":\"Unauthorized — valid JWT required\"}"
                    );
                })
            )

            // --- Insert JWT filter before Spring's default authentication filter ---
            .addFilterBefore(jwtAuthenticationFilter,
                             UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
