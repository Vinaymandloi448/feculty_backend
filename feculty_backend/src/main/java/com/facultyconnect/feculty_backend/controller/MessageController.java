package com.facultyconnect.feculty_backend.controller;

import com.facultyconnect.feculty_backend.dto.MessageResponse;
import com.facultyconnect.feculty_backend.dto.SendMessageRequest;
import com.facultyconnect.feculty_backend.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Message controller for one-to-one conversation messaging.
 *
 * All endpoints require a valid JWT.
 * Sender identity always comes from SecurityContext.
 * Conversation membership is verified on every operation.
 */
@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * POST /api/conversations/{conversationId}/messages
     *
     * Send a text message in a conversation.
     * Returns 201 CREATED on success.
     */
    @PostMapping
    public ResponseEntity<?> sendMessage(Authentication authentication,
                                         @PathVariable String conversationId,
                                         @RequestBody SendMessageRequest request) {
        try {
            String email = authentication.getName();
            MessageResponse response = messageService.sendMessage(
                    email, conversationId, request.getContent(), request.getAttachmentId());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * GET /api/conversations/{conversationId}/messages
     *
     * Get complete message history for a conversation.
     * Messages are sorted by createdAt ascending (oldest first).
     */
    @GetMapping
    public ResponseEntity<?> getMessages(Authentication authentication,
                                         @PathVariable String conversationId) {
        try {
            String email = authentication.getName();
            List<MessageResponse> messages = messageService.getConversationMessages(
                    email, conversationId);
            return ResponseEntity.ok(messages);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
