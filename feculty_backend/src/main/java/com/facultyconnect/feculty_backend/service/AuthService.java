package com.facultyconnect.feculty_backend.service;

import com.facultyconnect.feculty_backend.dto.FacultyResponse;
import com.facultyconnect.feculty_backend.dto.LoginRequest;
import com.facultyconnect.feculty_backend.dto.LoginResponse;
import com.facultyconnect.feculty_backend.dto.RegisterRequest;
import com.facultyconnect.feculty_backend.model.Faculty;
import com.facultyconnect.feculty_backend.repository.FacultyRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final FacultyRepository facultyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(FacultyRepository facultyRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.facultyRepository = facultyRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Register a new Faculty member.
     *
     * @param request the registration request DTO
     * @return FacultyResponse with no password exposed
     * @throws IllegalArgumentException if required fields are missing
     * @throws IllegalStateException    if email or facultyId already exists
     */
    public FacultyResponse register(RegisterRequest request) {
        // --- Validate required fields ---
        validateRequired(request.getFacultyId(), "facultyId");
        validateRequired(request.getFullName(), "fullName");
        validateRequired(request.getEmail(), "email");
        validateRequired(request.getPassword(), "password");
        validateRequired(request.getDepartment(), "department");
        validateRequired(request.getDesignation(), "designation");

        // --- Check for duplicate email ---
        if (facultyRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalStateException("Email already exists: " + request.getEmail());
        }

        // --- Check for duplicate facultyId ---
        if (facultyRepository.findByFacultyId(request.getFacultyId()).isPresent()) {
            throw new IllegalStateException("Faculty ID already exists: " + request.getFacultyId());
        }

        // --- Build Faculty entity ---
        Faculty faculty = new Faculty();
        faculty.setFacultyId(request.getFacultyId());
        faculty.setFullName(request.getFullName());
        faculty.setEmail(request.getEmail());
        faculty.setPassword(passwordEncoder.encode(request.getPassword()));
        faculty.setDepartment(request.getDepartment());
        faculty.setDesignation(request.getDesignation());
        faculty.setSubjects(request.getSubjects());
        faculty.setProfileImage(request.getProfileImage());
        faculty.setBio(request.getBio());
        faculty.setRole("FACULTY");

        // --- Save to MongoDB ---
        Faculty saved = facultyRepository.save(faculty);

        // --- Convert to response (password never exposed) ---
        return toResponse(saved);
    }

    /**
     * Authenticate a Faculty member and return a JWT.
     *
     * @param request the login request DTO (email + password)
     * @return LoginResponse with JWT token and faculty info (no password)
     * @throws IllegalArgumentException if required fields are missing
     * @throws SecurityException        if credentials are invalid
     */
    public LoginResponse login(LoginRequest request) {
        // --- Validate required fields ---
        validateRequired(request.getEmail(), "email");
        validateRequired(request.getPassword(), "password");

        // --- Find faculty by email ---
        Faculty faculty = facultyRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new SecurityException("Invalid email or password"));

        // --- Verify password against BCrypt hash ---
        if (!passwordEncoder.matches(request.getPassword(), faculty.getPassword())) {
            throw new SecurityException("Invalid email or password");
        }

        // --- Generate JWT ---
        String token = jwtService.generateToken(
                faculty.getId(),
                faculty.getEmail(),
                faculty.getRole()
        );

        // --- Return token + safe faculty info ---
        return new LoginResponse(token, toResponse(faculty));
    }

    // --- Helper: convert Faculty entity to FacultyResponse ---
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

