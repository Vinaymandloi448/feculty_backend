package com.facultyconnect.feculty_backend.dto;

/**
 * DTO for creating a one-to-one conversation.
 *
 * The client provides only the target faculty's institutional ID.
 * participantIds, conversationId, and timestamps are set server-side.
 */
public class CreateConversationRequest {

    private String facultyId;

    // --- Getters and Setters ---

    public String getFacultyId() {
        return facultyId;
    }

    public void setFacultyId(String facultyId) {
        this.facultyId = facultyId;
    }
}
