package com.facultyconnect.feculty_backend.controller;

import com.facultyconnect.feculty_backend.dto.FacultyResponse;
import com.facultyconnect.feculty_backend.dto.LoginRequest;
import com.facultyconnect.feculty_backend.dto.LoginResponse;
import com.facultyconnect.feculty_backend.dto.RegisterRequest;
import com.facultyconnect.feculty_backend.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/register
     *
     * Register a new faculty member.
     * Returns 201 CREATED on success, 400 for validation errors, 409 for duplicates.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        try {
            FacultyResponse response = authService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            // Missing or blank required field
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (IllegalStateException ex) {
            // Duplicate email or facultyId
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * POST /api/auth/login
     *
     * Authenticate a faculty member.
     * Returns 200 OK with JWT on success, 400 for missing fields, 401 for bad credentials.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            // Missing or blank required field
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            // Invalid email or password
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}

