package com.facultyconnect.feculty_backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Temporary controller to verify that JWT-protected endpoints work correctly.
 *
 * GET /api/protected-test
 *   - Without valid JWT → 401 Unauthorized
 *   - With valid JWT    → 200 OK
 */
@RestController
public class ProtectedTestController {

    @GetMapping("/api/protected-test")
    public ResponseEntity<?> protectedTest() {
        return ResponseEntity.ok(Map.of(
            "message", "Authenticated Faculty API is working!",
            "status", "success"
        ));
    }
}
