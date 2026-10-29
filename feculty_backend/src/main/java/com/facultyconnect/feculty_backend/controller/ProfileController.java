package com.facultyconnect.feculty_backend.controller;

import com.facultyconnect.feculty_backend.dto.FacultyResponse;
import com.facultyconnect.feculty_backend.dto.UpdateProfileRequest;
import com.facultyconnect.feculty_backend.service.ProfileService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Profile controller for the authenticated faculty.
 *
 * All endpoints require a valid JWT.
 * The logged-in faculty is identified from the JWT/SecurityContext,
 * NOT from the request body — this prevents users from modifying other profiles.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    /**
     * GET /api/profile/me
     *
     * Returns the profile of the currently authenticated faculty.
     * The email is read from the JWT (set as the principal by JwtAuthenticationFilter).
     */
    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile(Authentication authentication) {
        try {
            String email = authentication.getName();
            FacultyResponse response = profileService.getProfile(email);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * PUT /api/profile/me
     *
     * Updates the profile of the currently authenticated faculty.
     * Only allowed fields (fullName, department, designation, subjects, profileImage, bio) are updated.
     * Sensitive fields (id, facultyId, email, password, role) cannot be changed.
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateMyProfile(Authentication authentication,
                                             @RequestBody UpdateProfileRequest request) {
        try {
            String email = authentication.getName();
            FacultyResponse response = profileService.updateProfile(email, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
