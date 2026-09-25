package com.facultyconnect.feculty_backend.dto;

/**
 * WebSocket message request payload for real-time one-to-one chat.
 *
 * The client provides only conversationId and content.
 * Sender, receiver, messageId, status, timestamps are resolved server-side.
 */
public class WebSocketMessageRequest {

    private String conversationId;
    private String content;

    public WebSocketMessageRequest() {
    }

    public WebSocketMessageRequest(String conversationId, String content) {
        this.conversationId = conversationId;
        this.content = content;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
