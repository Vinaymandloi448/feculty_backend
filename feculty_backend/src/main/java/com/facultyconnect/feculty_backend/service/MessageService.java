package com.facultyconnect.feculty_backend.service;

import com.facultyconnect.feculty_backend.dto.MessageResponse;
import com.facultyconnect.feculty_backend.model.Conversation;
import com.facultyconnect.feculty_backend.model.Message;
import com.facultyconnect.feculty_backend.repository.ConversationRepository;
import com.facultyconnect.feculty_backend.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
    }

    /**
     * Send a text message in a conversation.
     *
     * Flow:
     * 1. Find the conversation by conversationId.
     * 2. Verify the sender is a participant.
     * 3. Determine the receiver (the OTHER participant).
     * 4. Validate content.
     * 5. Create and save the message.
     *
     * @param senderEmail    the logged-in faculty email (from JWT)
     * @param conversationId the conversation to send the message in
     * @param content        the message text
     * @return MessageResponse
     */
    public MessageResponse sendMessage(String senderEmail, String conversationId, String content) {
        // --- Find the conversation ---
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));

        // --- Verify sender is a participant ---
        if (!conversation.getParticipantIds().contains(senderEmail)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        // --- Validate content ---
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Message content is required");
        }
        if (content.length() > 5000) {
            throw new IllegalArgumentException(
                    "Message content must not exceed 5000 characters");
        }

        // --- Determine the receiver (the other participant) ---
        String receiverEmail = conversation.getParticipantIds().stream()
                .filter(email -> !email.equals(senderEmail))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Could not determine receiver"));

        // --- Build message ---
        Message message = new Message();
        message.setMessageId(generateMessageId());
        message.setConversationId(conversationId);
        message.setSenderId(senderEmail);
        message.setReceiverId(receiverEmail);
        message.setContent(content);
        message.setMessageType("TEXT");
        message.setStatus("SENT");

        // --- Save to MongoDB ---
        Message saved = messageRepository.save(message);
        return toResponse(saved);
    }

    /**
     * Get all messages for a conversation, sorted by createdAt ascending.
     *
     * Verifies that the requesting faculty is a participant before returning messages.
     *
     * @param currentEmail   the logged-in faculty email (from JWT)
     * @param conversationId the conversation to retrieve messages from
     * @return list of MessageResponse in chronological order
     */
    public List<MessageResponse> getConversationMessages(String currentEmail,
                                                         String conversationId) {
        // --- Find the conversation ---
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));

        // --- Verify the faculty is a participant ---
        if (!conversation.getParticipantIds().contains(currentEmail)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        // --- Return messages sorted by createdAt ascending (oldest first) ---
        List<Message> messages = messageRepository
                .findByConversationIdOrderByCreatedAtAsc(conversationId);

        return messages.stream().map(this::toResponse).toList();
    }

    // --- Helper: generate a unique, human-readable message ID ---
    private String generateMessageId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "MSG-" + uuid.substring(0, 12).toUpperCase();
    }

    // --- Helper: convert Message entity to MessageResponse ---
    private MessageResponse toResponse(Message message) {
        MessageResponse response = new MessageResponse();
        response.setMessageId(message.getMessageId());
        response.setConversationId(message.getConversationId());
        response.setSenderId(message.getSenderId());
        response.setReceiverId(message.getReceiverId());
        response.setContent(message.getContent());
        response.setMessageType(message.getMessageType());
        response.setStatus(message.getStatus());
        response.setCreatedAt(message.getCreatedAt());
        response.setUpdatedAt(message.getUpdatedAt());
        return response;
    }
}
