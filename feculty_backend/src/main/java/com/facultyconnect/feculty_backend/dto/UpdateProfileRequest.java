package com.facultyconnect.feculty_backend.dto;

import java.util.List;

/**
 * DTO for profile update requests.
 *
 * Only contains fields the faculty is allowed to change.
 * Sensitive fields (id, facultyId, email, password, role, timestamps)
 * are intentionally excluded and cannot be modified by the client.
 */
public class UpdateProfileRequest {

    private String fullName;
    private String department;
    private String designation;
    private List<String> subjects;
    private String profileImage;
    private String bio;

    // --- Getters and Setters ---

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDesignation() {
        return designation;
    }

    public void setDesignation(String designation) {
        this.designation = designation;
    }

    public List<String> getSubjects() {
        return subjects;
    }

    public void setSubjects(List<String> subjects) {
        this.subjects = subjects;
    }

    public String getProfileImage() {
        return profileImage;
    }

    public void setProfileImage(String profileImage) {
        this.profileImage = profileImage;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }
}
