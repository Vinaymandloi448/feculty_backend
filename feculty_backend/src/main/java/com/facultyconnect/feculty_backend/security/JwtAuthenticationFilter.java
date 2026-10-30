package com.facultyconnect.feculty_backend.security;

import com.facultyconnect.feculty_backend.service.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter that runs once per request.
 *
 * How it works:
 * 1. Reads the "Authorization: Bearer <token>" header.
 * 2. If no Bearer token is present, skips — Spring Security handles the request
 *    (public endpoints pass through; protected endpoints get 401).
 * 3. If a Bearer token IS present, validates it using the existing JwtService.
 * 4. If valid, creates an authenticated SecurityContext with email + role.
 * 5. If invalid/expired, clears the context — request proceeds as unauthenticated.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // --- Fast exit for CORS preflight OPTIONS requests ---
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // --- Step 1: Extract the Authorization header ---
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No Bearer token → skip, let Spring Security decide
            filterChain.doFilter(request, response);
            return;
        }

        // --- Step 2: Extract the token (everything after "Bearer ") ---
        String token = authHeader.substring(7);

        try {
            // --- Step 3: Validate JWT using existing JwtService ---
            // parseToken() verifies signature + expiration; throws on failure
            Claims claims = jwtService.parseToken(token);

            String email = claims.getSubject();
            String role = claims.get("role", String.class);

            // --- Step 4: Create authenticated SecurityContext ---
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            email,                                          // principal
                            null,                                           // credentials (not needed)
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))  // authorities
                    );

            SecurityContextHolder.getContext().setAuthentication(authentication);

        } catch (Exception ex) {
            // --- Step 5: Invalid/expired token → clear context ---
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
