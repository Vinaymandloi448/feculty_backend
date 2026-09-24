package com.facultyconnect.feculty_backend.service;

import com.facultyconnect.feculty_backend.dto.FacultyResponse;
import com.facultyconnect.feculty_backend.dto.UpdateProfileRequest;
import com.facultyconnect.feculty_backend.model.Faculty;
import com.facultyconnect.feculty_backend.repository.FacultyRepository;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class ProfileService {

    private final FacultyRepository facultyRepository;

    public ProfileService(FacultyRepository facultyRepository) {
        this.facultyRepository = facultyRepository;
    }

    /**
     * Get the profile of the currently authenticated faculty.
     *
     * @param email the email extracted from the JWT (SecurityContext)
     * @return FacultyResponse (password never included)
     * @throws NoSuchElementException if faculty is not found
     */
    public FacultyResponse getProfile(String email) {
        Faculty faculty = facultyRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Faculty not found"));

        return toResponse(faculty);
    }

    /**
     * Update the profile of the currently authenticated faculty.
     *
     * Only these fields can be updated:
     *   fullName, department, designation, subjects, profileImage, bio
     *
     * These fields are protected and CANNOT be changed:
     *   id, facultyId, email, password, role, createdAt, updatedAt
     *
     * @param email   the email extracted from the JWT (SecurityContext)
     * @param request the update request DTO
     * @return updated FacultyResponse (password never included)
     * @throws NoSuchElementException   if faculty is not found
     * @throws IllegalArgumentException if required fields are blank
     */
    public FacultyResponse updateProfile(String email, UpdateProfileRequest request) {
        // --- Validate required fields ---
        validateRequired(request.getFullName(), "fullName");
        validateRequired(request.getDepartment(), "department");
        validateRequired(request.getDesignation(), "designation");

        // --- Find the authenticated faculty ---
        Faculty faculty = facultyRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("Faculty not found"));

        // --- Update only the allowed fields ---
        faculty.setFullName(request.getFullName());
        faculty.setDepartment(request.getDepartment());
        faculty.setDesignation(request.getDesignation());
        faculty.setSubjects(request.getSubjects());
        faculty.setProfileImage(request.getProfileImage());
        faculty.setBio(request.getBio());

        // id, facultyId, email, password, role are NOT touched
        // updatedAt is handled automatically by @LastModifiedDate

        // --- Save and return ---
        Faculty saved = facultyRepository.save(faculty);
        return toResponse(saved);
    }

    // --- Helper: convert Faculty entity to FacultyResponse (password excluded) ---
    private FacultyResponse toResponse(Faculty faculty) {
        FacultyResponse response = new FacultyResponse();
        response.setId(faculty.getId());
        response.setFacultyId(faculty.getFacultyId());
        response.setFullName(faculty.getFullName());
        response.setEmail(faculty.getEmail());
        response.setDepartment(faculty.getDepartment());
        response.setDesignation(faculty.getDesignation());
        response.setSubjects(faculty.getSubjects());
        response.setProfileImage(faculty.getProfileImage());
        response.setBio(faculty.getBio());
        response.setRole(faculty.getRole());
        response.setCreatedAt(faculty.getCreatedAt());
        response.setUpdatedAt(faculty.getUpdatedAt());
        return response;
    }

    // --- Helper: validate that a field is non-null and non-blank ---
    private void validateRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }
}
