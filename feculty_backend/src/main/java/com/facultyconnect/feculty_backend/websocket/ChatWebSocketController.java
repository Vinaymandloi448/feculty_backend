package com.facultyconnect.feculty_backend.websocket;

import com.facultyconnect.feculty_backend.dto.MessageResponse;
import com.facultyconnect.feculty_backend.dto.WebSocketMessageRequest;
import com.facultyconnect.feculty_backend.service.MessageService;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Controller handling real-time STOMP messaging over WebSocket.
 *
 * Expected destination: /app/chat.send
 * Broadcast destination: /topic/conversation/{conversationId}
 */
@Controller
public class ChatWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(MessageService messageService,
                                   SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handle incoming real-time chat message from client.
     *
     * Destination: /app/chat.send
     * Payload: { "conversationId": "CONV-...", "content": "Hello!" }
     *
     * @param request   the message payload
     * @param principal the authenticated WebSocket user (Faculty email from JWT)
     */
    @MessageMapping("/chat.send")
    public void sendMessage(@Payload WebSocketMessageRequest request, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new SecurityException("Unauthenticated WebSocket session");
        }

        if (request == null) {
            throw new IllegalArgumentException("Message request cannot be null");
        }

        String senderEmail = principal.getName();

        // 1. Identify sender, validate conversation membership & content, persist message via MessageService
        // Message status is retained as "SENT" until actual delivery/read receipt handling is implemented
        MessageResponse response = messageService.sendMessage(
                senderEmail,
                request.getConversationId(),
                request.getContent(),
                request.getAttachmentId()
        );

        // 2. Broadcast to topic /topic/conversation/{conversationId}
        String destination = "/topic/conversation/" + request.getConversationId();
        messagingTemplate.convertAndSend(destination, response);
    }

    /**
     * Exception handler for WebSocket messages.
     * Catches validation and authorization errors without exposing stack traces.
     */
    @MessageExceptionHandler
    public void handleException(Exception ex) {
        // Log error silently without exposing internal stack traces over WebSocket frames
        System.err.println("WebSocket Error: " + ex.getMessage());
    }
}
