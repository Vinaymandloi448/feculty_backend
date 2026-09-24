package com.facultyconnect.feculty_backend.service;

import com.facultyconnect.feculty_backend.dto.ConversationResponse;
import com.facultyconnect.feculty_backend.model.Conversation;
import com.facultyconnect.feculty_backend.model.Faculty;
import com.facultyconnect.feculty_backend.repository.ConversationRepository;
import com.facultyconnect.feculty_backend.repository.FacultyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final FacultyRepository facultyRepository;

    public ConversationService(ConversationRepository conversationRepository,
                               FacultyRepository facultyRepository) {
        this.conversationRepository = conversationRepository;
        this.facultyRepository = facultyRepository;
    }

    /**
     * Create a new one-to-one conversation, or return the existing one.
     *
     * Duplicate prevention:
     *   If A ↔ B already exists, return it instead of creating A ↔ B again.
     *   Works regardless of who initiates (A→B or B→A).
     *
     * @param currentEmail the logged-in faculty email (from JWT)
     * @param targetFacultyId the target faculty's institutional ID (e.g. FAC003)
     * @return ConversationResponse
     * @throws IllegalArgumentException if facultyId is blank or same as current user
     * @throws NoSuchElementException   if target faculty does not exist
     */
    public ConversationResponse createOrGetConversation(String currentEmail, String targetFacultyId) {
        // --- Validate ---
        if (targetFacultyId == null || targetFacultyId.isBlank()) {
            throw new IllegalArgumentException("facultyId is required");
        }

        // --- Look up target faculty ---
        Faculty targetFaculty = facultyRepository.findByFacultyId(targetFacultyId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Faculty not found with ID: " + targetFacultyId));

        String targetEmail = targetFaculty.getEmail();

        // --- Cannot start a conversation with yourself ---
        if (currentEmail.equals(targetEmail)) {
            throw new IllegalArgumentException("Cannot create a conversation with yourself");
        }

        // --- Check if conversation already exists between these two ---
        Optional<Conversation> existing = conversationRepository
                .findByParticipantIdsContainingAndParticipantIdsContaining(
                        currentEmail, targetEmail);

        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        // --- Create new conversation ---
        Conversation conversation = new Conversation();
        conversation.setConversationId(generateConversationId());
        conversation.setParticipantIds(List.of(currentEmail, targetEmail));

        Conversation saved = conversationRepository.save(conversation);
        return toResponse(saved);
    }

    /**
     * Get all conversations where the logged-in faculty is a participant.
     *
     * @param currentEmail the logged-in faculty email (from JWT)
     * @return list of ConversationResponse
     */
    public List<ConversationResponse> getMyConversations(String currentEmail) {
        List<Conversation> conversations =
                conversationRepository.findByParticipantIdsContaining(currentEmail);
        return conversations.stream().map(this::toResponse).toList();
    }

    /**
     * Get a single conversation by conversationId.
     * Only accessible if the logged-in faculty is a participant.
     *
     * @param currentEmail   the logged-in faculty email (from JWT)
     * @param conversationId the conversation's unique identifier
     * @return ConversationResponse
     * @throws NoSuchElementException if conversation not found
     * @throws SecurityException      if faculty is not a participant
     */
    public ConversationResponse getConversationById(String currentEmail, String conversationId) {
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));

        if (!conversation.getParticipantIds().contains(currentEmail)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        return toResponse(conversation);
    }

    // --- Helper: generate a unique, human-readable conversation ID ---
    private String generateConversationId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "CONV-" + uuid.substring(0, 8).toUpperCase();
    }

    // --- Helper: convert Conversation entity to ConversationResponse ---
    private ConversationResponse toResponse(Conversation conversation) {
        ConversationResponse response = new ConversationResponse();
        response.setConversationId(conversation.getConversationId());
        response.setParticipantIds(conversation.getParticipantIds());
        response.setCreatedAt(conversation.getCreatedAt());
        response.setUpdatedAt(conversation.getUpdatedAt());
        return response;
    }
}
