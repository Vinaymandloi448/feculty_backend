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
    private String attachmentId;

    // --- Getters and Setters ---

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }
}
