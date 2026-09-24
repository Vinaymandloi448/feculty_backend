package com.facultyconnect.feculty_backend.dto;

import java.time.Instant;
import java.util.List;

/**
 * Conversation response DTO.
 * Does not expose the internal MongoDB _id field.
 */
public class ConversationResponse {

    private String conversationId;
    private List<String> participantIds;
    private Instant createdAt;
    private Instant updatedAt;

    // --- Getters and Setters ---

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public List<String> getParticipantIds() {
        return participantIds;
    }

    public void setParticipantIds(List<String> participantIds) {
        this.participantIds = participantIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
