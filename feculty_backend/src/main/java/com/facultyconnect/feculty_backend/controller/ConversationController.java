package com.facultyconnect.feculty_backend.controller;

import com.facultyconnect.feculty_backend.dto.ConversationResponse;
import com.facultyconnect.feculty_backend.dto.CreateConversationRequest;
import com.facultyconnect.feculty_backend.service.ConversationService;
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
 * Conversation controller for one-to-one faculty communication.
 *
 * All endpoints require a valid JWT.
 * The current user is always identified from SecurityContext.
 */
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * POST /api/conversations
     *
     * Create a new one-to-one conversation, or return the existing one.
     * Body: { "facultyId": "FAC003" }
     */
    @PostMapping
    public ResponseEntity<?> createOrGetConversation(Authentication authentication,
                                                     @RequestBody CreateConversationRequest request) {
        try {
            String email = authentication.getName();
            ConversationResponse response =
                    conversationService.createOrGetConversation(email, request.getFacultyId());
            return ResponseEntity.status(HttpStatus.OK).body(response);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", ex.getMessage()));
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * GET /api/conversations
     *
     * Return all conversations of the logged-in faculty.
     */
    @GetMapping
    public ResponseEntity<List<ConversationResponse>> getMyConversations(
            Authentication authentication) {
        String email = authentication.getName();
        List<ConversationResponse> conversations =
                conversationService.getMyConversations(email);
        return ResponseEntity.ok(conversations);
    }

    /**
     * GET /api/conversations/{conversationId}
     *
     * Return a conversation only if the logged-in faculty is a participant.
     */
    @GetMapping("/{conversationId}")
    public ResponseEntity<?> getConversationById(Authentication authentication,
                                                 @PathVariable String conversationId) {
        try {
            String email = authentication.getName();
            ConversationResponse response =
                    conversationService.getConversationById(email, conversationId);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
