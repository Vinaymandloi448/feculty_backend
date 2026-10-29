package com.facultyconnect.feculty_backend.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Dedicated JWT utility service.
 *
 * Responsibilities:
 *   - Generate signed JWT tokens
 *   - Parse and validate JWT tokens (for future use)
 *
 * JWT secret and expiration are read from application.properties,
 * NEVER hardcoded in Java code.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generate a signed JWT containing faculty claims.
     *
     * @param id       the MongoDB document id
     * @param email    the faculty email (used as the JWT subject)
     * @param role     the faculty role
     * @return a signed JWT string
     */
    public String generateToken(String id, String email, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("id", id)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parse and validate a JWT token, returning the claims.
     * Throws an exception if the token is invalid or expired.
     *
     * @param token the JWT string
     * @return parsed claims
     */
    public io.jsonwebtoken.Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Extract the email (subject) from a valid JWT.
     *
     * @param token the JWT string
     * @return email
     */
    public String getEmailFromToken(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * Check if a token is valid and not expired.
     *
     * @param token the JWT string
     * @return true if valid
     */
    public boolean isTokenValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
