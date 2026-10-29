package com.facultyconnect.feculty_backend.service;

import com.facultyconnect.feculty_backend.dto.AttachmentResponse;
import com.facultyconnect.feculty_backend.dto.MessageResponse;
import com.facultyconnect.feculty_backend.model.Attachment;
import com.facultyconnect.feculty_backend.model.Conversation;
import com.facultyconnect.feculty_backend.model.Message;
import com.facultyconnect.feculty_backend.repository.AttachmentRepository;
import com.facultyconnect.feculty_backend.repository.ConversationRepository;
import com.facultyconnect.feculty_backend.repository.MessageRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final AttachmentRepository attachmentRepository;

    public MessageService(MessageRepository messageRepository,
                          ConversationRepository conversationRepository,
                          AttachmentRepository attachmentRepository) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.attachmentRepository = attachmentRepository;
    }

    /**
     * Send a text message in a conversation.
     * Backward-compatible overload.
     */
    public MessageResponse sendMessage(String senderEmail, String conversationId, String content) {
        return sendMessage(senderEmail, conversationId, content, null);
    }

    /**
     * Send a text message or attachment-backed message in a conversation.
     *
     * Flow:
     * 1. Find the conversation by conversationId.
     * 2. Verify the sender is a participant.
     * 3. Determine the receiver (the OTHER participant).
     * 4. Validate content and/or attachment.
     * 5. Create and save the message.
     *
     * @param senderEmail    the logged-in faculty email (from JWT)
     * @param conversationId the conversation to send the message in
     * @param content        the message text or caption
     * @param attachmentId   optional attachment ID if associating an existing attachment
     * @return MessageResponse
     */
    public MessageResponse sendMessage(String senderEmail,
                                       String conversationId,
                                       String content,
                                       String attachmentId) {
        // --- Find the conversation ---
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));

        // --- Verify sender is a participant ---
        if (!conversation.getParticipantIds().contains(senderEmail)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        Attachment attachment = null;
        if (attachmentId != null && !attachmentId.isBlank()) {
            attachment = attachmentRepository.findByAttachmentId(attachmentId)
                    .orElseThrow(() -> new NoSuchElementException("Attachment not found"));

            if (!conversationId.equals(attachment.getConversationId())) {
                throw new IllegalArgumentException("Attachment does not belong to this conversation");
            }
        }

        // --- Validate content ---
        if (attachment == null) {
            if (content == null || content.isBlank()) {
                throw new IllegalArgumentException("Message content is required");
            }
        } else {
            if (content == null || content.isBlank()) {
                content = attachment.getOriginalFilename();
            }
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

        // --- Determine message type ---
        String messageType = "TEXT";
        if (attachment != null) {
            messageType = attachment.getMimeType().startsWith("image/") ? "IMAGE" : "PDF";
        }

        // --- Build message ---
        Message message = new Message();
        message.setMessageId(generateMessageId());
        message.setConversationId(conversationId);
        message.setSenderId(senderEmail);
        message.setReceiverId(receiverEmail);
        message.setContent(content);
        message.setMessageType(messageType);
        message.setStatus("SENT");
        if (attachment != null) {
            message.setAttachmentId(attachment.getAttachmentId());
        }

        // --- Save to MongoDB ---
        Message saved = messageRepository.save(message);

        if (attachment != null) {
            attachment.setMessageId(saved.getMessageId());
            attachmentRepository.save(attachment);
            return toResponse(saved, toAttachmentResponse(attachment));
        }

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

        // Batch load attachments for this conversation to prevent N+1 queries
        List<Attachment> attachments = attachmentRepository
                .findByConversationIdOrderByUploadTimestampDesc(conversationId);
        Map<String, AttachmentResponse> attachmentMap = attachments.stream()
                .collect(Collectors.toMap(
                        Attachment::getAttachmentId,
                        this::toAttachmentResponse,
                        (existing, replace) -> existing
                ));

        return messages.stream().map(msg -> {
            AttachmentResponse att = msg.getAttachmentId() != null ? attachmentMap.get(msg.getAttachmentId()) : null;
            return toResponse(msg, att);
        }).toList();
    }

    /**
     * Update status of a message by messageId.
     *
     * @param messageId the ID of the message
     * @param status    the new status (e.g. DELIVERED)
     */
    public void updateMessageStatus(String messageId, String status) {
        messageRepository.findByMessageId(messageId).ifPresent(msg -> {
            msg.setStatus(status);
            messageRepository.save(msg);
        });
    }

    // --- Helper: generate a unique, human-readable message ID ---
    private String generateMessageId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "MSG-" + uuid.substring(0, 12).toUpperCase();
    }

    // --- Helper: convert Message entity to MessageResponse ---
    public MessageResponse toResponse(Message message) {
        AttachmentResponse attachmentResponse = null;
        if (message.getAttachmentId() != null) {
            Optional<Attachment> attOpt = attachmentRepository.findByAttachmentId(message.getAttachmentId());
            attachmentResponse = attOpt.map(this::toAttachmentResponse).orElse(null);
        }
        return toResponse(message, attachmentResponse);
    }

    public MessageResponse toResponse(Message message, AttachmentResponse attachmentResponse) {
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
        response.setAttachmentId(message.getAttachmentId());
        response.setAttachment(attachmentResponse);
        return response;
    }

    public AttachmentResponse toAttachmentResponse(Attachment attachment) {
        if (attachment == null) {
            return null;
        }
        AttachmentResponse response = new AttachmentResponse();
        response.setAttachmentId(attachment.getAttachmentId());
        response.setConversationId(attachment.getConversationId());
        response.setMessageId(attachment.getMessageId());
        response.setOriginalFilename(attachment.getOriginalFilename());
        response.setStoredFilename(attachment.getStoredFilename());
        response.setMimeType(attachment.getMimeType());
        response.setSize(attachment.getSize());
        response.setUploaderFacultyId(attachment.getUploaderFacultyId());
        response.setUploaderEmail(attachment.getUploaderEmail());
        response.setUploadTimestamp(attachment.getUploadTimestamp());
        response.setViewUrl("/api/conversations/" + attachment.getConversationId() + "/attachments/" + attachment.getAttachmentId());
        response.setDownloadUrl("/api/conversations/" + attachment.getConversationId() + "/attachments/" + attachment.getAttachmentId() + "/download");
        return response;
    }
}
