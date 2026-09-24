package com.facultyconnect.feculty_backend.dto;

/**
 * DTO for sending a message.
 *
 * Only content comes from the client.
 * senderId, receiverId, conversationId, messageId, status, and messageType
 * are all determined server-side.
 */
public class SendMessageRequest {

    private String content;

    // --- Getters and Setters ---

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
