package com.facultyconnect.feculty_backend.service;

import com.facultyconnect.feculty_backend.dto.AttachmentResponse;
import com.facultyconnect.feculty_backend.dto.MessageResponse;
import com.facultyconnect.feculty_backend.model.Attachment;
import com.facultyconnect.feculty_backend.model.Conversation;
import com.facultyconnect.feculty_backend.model.Faculty;
import com.facultyconnect.feculty_backend.model.Message;
import com.facultyconnect.feculty_backend.repository.AttachmentRepository;
import com.facultyconnect.feculty_backend.repository.ConversationRepository;
import com.facultyconnect.feculty_backend.repository.FacultyRepository;
import com.facultyconnect.feculty_backend.repository.MessageRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Service managing attachment uploads, authorization, safe storage, and retrieval.
 */
@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final ConversationRepository conversationRepository;
    private final FacultyRepository facultyRepository;
    private final MessageRepository messageRepository;
    private final FileValidationService fileValidationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${file.upload-dir:uploads}")
    private String uploadDirProperty;

    private Path uploadRoot;

    public record AttachmentResource(
            Resource resource,
            Attachment attachment
    ) {}

    public AttachmentService(AttachmentRepository attachmentRepository,
                             ConversationRepository conversationRepository,
                             FacultyRepository facultyRepository,
                             MessageRepository messageRepository,
                             FileValidationService fileValidationService,
                             SimpMessagingTemplate messagingTemplate) {
        this.attachmentRepository = attachmentRepository;
        this.conversationRepository = conversationRepository;
        this.facultyRepository = facultyRepository;
        this.messageRepository = messageRepository;
        this.fileValidationService = fileValidationService;
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() {
        this.uploadRoot = Paths.get(uploadDirProperty).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadRoot);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize upload storage directory at " + uploadRoot, e);
        }
    }

    /**
     * Upload an attachment to a conversation.
     * Verifies conversation membership, validates file, stores on disk safely,
     * persists metadata in MongoDB, creates linked chat message, and broadcasts via WebSocket.
     */
    public AttachmentResponse uploadAttachment(String senderEmail,
                                              String conversationId,
                                              MultipartFile file,
                                              String content) {
        // 1. Verify conversation exists and sender is participant
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));

        if (!conversation.getParticipantIds().contains(senderEmail)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        // 2. Validate file
        FileValidationService.ValidatedFile validatedFile = fileValidationService.validate(file);

        // 3. Generate safe unique filename
        String storedFilename = UUID.randomUUID().toString().replace("-", "") + "." + validatedFile.safeExtension();

        // 4. Resolve storage path and prevent path traversal
        Path conversationDir = uploadRoot.resolve("conversations").resolve(conversationId).normalize();
        if (!conversationDir.startsWith(uploadRoot)) {
            throw new SecurityException("Path traversal attempt detected");
        }

        try {
            Files.createDirectories(conversationDir);
            Path targetFilePath = conversationDir.resolve(storedFilename).normalize();
            if (!targetFilePath.startsWith(uploadRoot)) {
                throw new SecurityException("Path traversal attempt detected");
            }
            Files.copy(file.getInputStream(), targetFilePath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store file on disk", e);
        }

        String storageKey = "conversations/" + conversationId + "/" + storedFilename;

        // 5. Look up sender's institutional facultyId
        String uploaderFacultyId = facultyRepository.findByEmail(senderEmail)
                .map(Faculty::getFacultyId)
                .orElse(senderEmail);

        // 6. Persist Attachment metadata in MongoDB
        Attachment attachment = new Attachment();
        attachment.setAttachmentId(generateAttachmentId());
        attachment.setConversationId(conversationId);
        attachment.setUploaderFacultyId(uploaderFacultyId);
        attachment.setUploaderEmail(senderEmail);
        attachment.setOriginalFilename(validatedFile.originalFilename());
        attachment.setStoredFilename(storedFilename);
        attachment.setStorageKey(storageKey);
        attachment.setMimeType(validatedFile.canonicalMimeType());
        attachment.setSize(validatedFile.size());
        attachment.setUploadTimestamp(Instant.now());

        attachment = attachmentRepository.save(attachment);

        // 7. Create linked chat message for the conversation
        String receiverEmail = conversation.getParticipantIds().stream()
                .filter(email -> !email.equals(senderEmail))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not determine receiver"));

        String messageType = validatedFile.canonicalMimeType().startsWith("image/") ? "IMAGE" : "PDF";
        String messageContent = (content != null && !content.isBlank())
                ? content.trim()
                : validatedFile.originalFilename();

        Message message = new Message();
        message.setMessageId(generateMessageId());
        message.setConversationId(conversationId);
        message.setSenderId(senderEmail);
        message.setReceiverId(receiverEmail);
        message.setContent(messageContent);
        message.setMessageType(messageType);
        message.setStatus("SENT");
        message.setAttachmentId(attachment.getAttachmentId());

        Message savedMessage = messageRepository.save(message);

        // 8. Update attachment with message ID
        attachment.setMessageId(savedMessage.getMessageId());
        attachment = attachmentRepository.save(attachment);

        // 9. Build responses
        AttachmentResponse attachmentResponse = toResponse(attachment);
        MessageResponse messageResponse = toMessageResponse(savedMessage, attachmentResponse);
        attachmentResponse.setMessage(messageResponse);

        // 10. Broadcast real-time message over WebSocket
        String destination = "/topic/conversation/" + conversationId;
        messagingTemplate.convertAndSend(destination, messageResponse);

        return attachmentResponse;
    }

    /**
     * Get attachment resource for download or inline viewing after verifying authorization.
     */
    public AttachmentResource getAttachmentResource(String currentEmail,
                                                   String conversationId,
                                                   String attachmentId) {
        Attachment attachment = attachmentRepository.findByAttachmentId(attachmentId)
                .orElseThrow(() -> new NoSuchElementException("Attachment not found"));

        if (conversationId != null && !conversationId.equals(attachment.getConversationId())) {
            throw new NoSuchElementException("Attachment not found in this conversation");
        }

        Conversation conversation = conversationRepository.findByConversationId(attachment.getConversationId())
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));

        if (!conversation.getParticipantIds().contains(currentEmail)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        Path filePath = uploadRoot.resolve(attachment.getStorageKey()).normalize();
        if (!filePath.startsWith(uploadRoot)) {
            throw new SecurityException("Path traversal attempt detected");
        }

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new NoSuchElementException("File not found on storage");
        }

        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new NoSuchElementException("File not found on storage");
            }
            return new AttachmentResource(resource, attachment);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Error resolving file resource", e);
        }
    }

    /**
     * Get all attachments for a conversation.
     */
    public List<AttachmentResponse> getConversationAttachments(String currentEmail, String conversationId) {
        Conversation conversation = conversationRepository.findByConversationId(conversationId)
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));

        if (!conversation.getParticipantIds().contains(currentEmail)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        List<Attachment> attachments = attachmentRepository
                .findByConversationIdOrderByUploadTimestampDesc(conversationId);

        return attachments.stream().map(this::toResponse).toList();
    }

    /**
     * Get metadata of a single attachment.
     */
    public AttachmentResponse getAttachmentMetadata(String currentEmail,
                                                   String conversationId,
                                                   String attachmentId) {
        Attachment attachment = attachmentRepository.findByAttachmentId(attachmentId)
                .orElseThrow(() -> new NoSuchElementException("Attachment not found"));

        if (conversationId != null && !conversationId.equals(attachment.getConversationId())) {
            throw new NoSuchElementException("Attachment not found in this conversation");
        }

        Conversation conversation = conversationRepository.findByConversationId(attachment.getConversationId())
                .orElseThrow(() -> new NoSuchElementException("Conversation not found"));

        if (!conversation.getParticipantIds().contains(currentEmail)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        return toResponse(attachment);
    }

    /**
     * Convert Attachment entity to AttachmentResponse DTO.
     */
    public AttachmentResponse toResponse(Attachment attachment) {
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

        String convId = attachment.getConversationId();
        String attId = attachment.getAttachmentId();
        response.setViewUrl("/api/conversations/" + convId + "/attachments/" + attId);
        response.setDownloadUrl("/api/conversations/" + convId + "/attachments/" + attId + "/download");

        return response;
    }

    private MessageResponse toMessageResponse(Message message, AttachmentResponse attachmentResponse) {
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

    private String generateAttachmentId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "ATT-" + uuid.substring(0, 12).toUpperCase();
    }

    private String generateMessageId() {
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "MSG-" + uuid.substring(0, 12).toUpperCase();
    }

    public void setUploadRootForTesting(Path path) {
        this.uploadRoot = path;
    }
}
