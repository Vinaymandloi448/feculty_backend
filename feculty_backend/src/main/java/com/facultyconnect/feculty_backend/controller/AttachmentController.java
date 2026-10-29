package com.facultyconnect.feculty_backend.controller;

import com.facultyconnect.feculty_backend.dto.AttachmentResponse;
import com.facultyconnect.feculty_backend.dto.MessageResponse;
import com.facultyconnect.feculty_backend.model.Attachment;
import com.facultyconnect.feculty_backend.service.AttachmentService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Controller for secure, authenticated attachment uploads and downloads.
 * Private files are never exposed through unrestricted public static directories.
 */
@RestController
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /**
     * POST /api/conversations/{conversationId}/attachments
     *
     * Upload an attachment (PDF, JPEG, PNG, WebP) up to 5 MB.
     * Automatically creates linked message in conversation and broadcasts to WebSocket subscribers.
     * Returns 201 CREATED with AttachmentResponse.
     */
    @PostMapping(value = "/api/conversations/{conversationId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadAttachment(Authentication authentication,
                                             @PathVariable String conversationId,
                                             @RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "content", required = false) String content) {
        try {
            String email = authentication.getName();
            AttachmentResponse response = attachmentService.uploadAttachment(email, conversationId, file, content);
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
     * POST /api/conversations/{conversationId}/messages/upload
     *
     * Convenience endpoint returning MessageResponse directly for chat clients.
     */
    @PostMapping(value = "/api/conversations/{conversationId}/messages/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadMessageWithAttachment(Authentication authentication,
                                                         @PathVariable String conversationId,
                                                         @RequestParam("file") MultipartFile file,
                                                         @RequestParam(value = "content", required = false) String content) {
        try {
            String email = authentication.getName();
            AttachmentResponse response = attachmentService.uploadAttachment(email, conversationId, file, content);
            MessageResponse messageResponse = response.getMessage();
            return ResponseEntity.status(HttpStatus.CREATED).body(messageResponse != null ? messageResponse : response);
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
     * GET /api/conversations/{conversationId}/attachments/{attachmentId}
     *
     * Authenticated inline view or stream of an attachment.
     */
    @GetMapping("/api/conversations/{conversationId}/attachments/{attachmentId}")
    public ResponseEntity<?> getAttachment(Authentication authentication,
                                           @PathVariable String conversationId,
                                           @PathVariable String attachmentId,
                                           @RequestParam(value = "download", defaultValue = "false") boolean download) {
        return buildFileResponse(authentication, conversationId, attachmentId, download);
    }

    /**
     * GET /api/conversations/{conversationId}/attachments/{attachmentId}/download
     *
     * Authenticated download of an attachment with Content-Disposition: attachment.
     */
    @GetMapping("/api/conversations/{conversationId}/attachments/{attachmentId}/download")
    public ResponseEntity<?> downloadAttachment(Authentication authentication,
                                                @PathVariable String conversationId,
                                                @PathVariable String attachmentId) {
        return buildFileResponse(authentication, conversationId, attachmentId, true);
    }

    /**
     * GET /api/conversations/{conversationId}/attachments/{attachmentId}/metadata
     *
     * Authenticated endpoint to retrieve attachment metadata without downloading file bytes.
     */
    @GetMapping("/api/conversations/{conversationId}/attachments/{attachmentId}/metadata")
    public ResponseEntity<?> getAttachmentMetadata(Authentication authentication,
                                                   @PathVariable String conversationId,
                                                   @PathVariable String attachmentId) {
        try {
            String email = authentication.getName();
            AttachmentResponse response = attachmentService.getAttachmentMetadata(email, conversationId, attachmentId);
            return ResponseEntity.ok(response);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * GET /api/conversations/{conversationId}/attachments
     *
     * List all attachments in a conversation.
     */
    @GetMapping("/api/conversations/{conversationId}/attachments")
    public ResponseEntity<?> getConversationAttachments(Authentication authentication,
                                                        @PathVariable String conversationId) {
        try {
            String email = authentication.getName();
            List<AttachmentResponse> attachments = attachmentService.getConversationAttachments(email, conversationId);
            return ResponseEntity.ok(attachments);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }

    /**
     * GET /api/attachments/{attachmentId}
     * Direct authenticated file access route.
     */
    @GetMapping("/api/attachments/{attachmentId}")
    public ResponseEntity<?> getAttachmentDirect(Authentication authentication,
                                                 @PathVariable String attachmentId,
                                                 @RequestParam(value = "download", defaultValue = "false") boolean download) {
        return buildFileResponse(authentication, null, attachmentId, download);
    }

    /**
     * GET /api/attachments/{attachmentId}/download
     * Direct authenticated file download route.
     */
    @GetMapping("/api/attachments/{attachmentId}/download")
    public ResponseEntity<?> downloadAttachmentDirect(Authentication authentication,
                                                      @PathVariable String attachmentId) {
        return buildFileResponse(authentication, null, attachmentId, true);
    }

    private ResponseEntity<?> buildFileResponse(Authentication authentication,
                                                String conversationId,
                                                String attachmentId,
                                                boolean download) {
        try {
            String email = authentication.getName();
            AttachmentService.AttachmentResource res = attachmentService.getAttachmentResource(email, conversationId, attachmentId);
            Attachment attachment = res.attachment();
            Resource resource = res.resource();

            ContentDisposition disposition = ContentDisposition.builder(download ? "attachment" : "inline")
                    .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8)
                    .build();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                    .header(HttpHeaders.CONTENT_TYPE, attachment.getMimeType())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(attachment.getSize()))
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(resource);
        } catch (NoSuchElementException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", ex.getMessage()));
        } catch (SecurityException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
