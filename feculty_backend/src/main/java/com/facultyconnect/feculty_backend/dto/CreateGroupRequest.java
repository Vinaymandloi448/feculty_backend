package com.facultyconnect.feculty_backend.dto;

/**
 * DTO for group creation requests.
 *
 * createdBy and memberIds are NOT accepted from the client —
 * the backend sets them from the JWT/SecurityContext.
 */
public class CreateGroupRequest {

    private String name;
    private String description;
    private String department;
    private String subject;

    // --- Getters and Setters ---

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }
}
