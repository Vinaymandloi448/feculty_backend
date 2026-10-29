package com.facultyconnect.feculty_backend.dto;

/**
 * Login response containing the JWT token and safe faculty information.
 * Password is NEVER included in this response.
 */
public class LoginResponse {

    private String token;
    private String tokenType;
    private FacultyResponse faculty;

    public LoginResponse(String token, FacultyResponse faculty) {
        this.token = token;
        this.tokenType = "Bearer";
        this.faculty = faculty;
    }

    // --- Getters and Setters ---

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public FacultyResponse getFaculty() {
        return faculty;
    }

    public void setFaculty(FacultyResponse faculty) {
        this.faculty = faculty;
    }
}
