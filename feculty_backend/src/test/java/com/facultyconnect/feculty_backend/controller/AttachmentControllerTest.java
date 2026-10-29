package com.facultyconnect.feculty_backend.controller;

import com.facultyconnect.feculty_backend.dto.AttachmentResponse;
import com.facultyconnect.feculty_backend.dto.MessageResponse;
import com.facultyconnect.feculty_backend.model.Attachment;
import com.facultyconnect.feculty_backend.service.AttachmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

    @Mock
    private AttachmentService attachmentService;

    @InjectMocks
    private AttachmentController attachmentController;

    private MockMvc mockMvc;

    private Authentication mockAuth;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(attachmentController)
                .setControllerAdvice(new FileUploadExceptionHandler())
                .build();
        mockAuth = new UsernamePasswordAuthenticationToken("prof@univ.edu", null, List.of());
    }

    @Test
    void uploadAttachment_Returns201Created_OnSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.5 test".getBytes(StandardCharsets.UTF_8));

        AttachmentResponse response = new AttachmentResponse();
        response.setAttachmentId("ATT-12345");
        response.setConversationId("CONV-1");
        response.setOriginalFilename("doc.pdf");
        response.setMimeType("application/pdf");
        response.setSize(100);

        when(attachmentService.uploadAttachment(eq("prof@univ.edu"), eq("CONV-1"), any(), eq("Notes")))
                .thenReturn(response);

        mockMvc.perform(multipart("/api/conversations/CONV-1/attachments")
                        .file(file)
                        .param("content", "Notes")
                        .principal(mockAuth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.attachmentId").value("ATT-12345"))
                .andExpect(jsonPath("$.originalFilename").value("doc.pdf"))
                .andExpect(jsonPath("$.mimeType").value("application/pdf"));
    }

    @Test
    void uploadAttachment_Returns400_WhenFileSizeExceeded() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "huge.pdf", "application/pdf", new byte[100]);

        when(attachmentService.uploadAttachment(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("File size exceeds the 5 MB limit"));

        mockMvc.perform(multipart("/api/conversations/CONV-1/attachments")
                        .file(file)
                        .principal(mockAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File size exceeds the 5 MB limit"));
    }

    @Test
    void uploadAttachment_Returns400_WhenUnsupportedFormat() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[]{0x4D, 0x5A});

        when(attachmentService.uploadAttachment(any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported file format. Only PDF, JPEG, PNG, and WebP files are allowed"));

        mockMvc.perform(multipart("/api/conversations/CONV-1/attachments")
                        .file(file)
                        .principal(mockAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Unsupported file format. Only PDF, JPEG, PNG, and WebP files are allowed"));
    }

    @Test
    void uploadAttachment_Returns403_WhenUserUnauthorized() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.5 test".getBytes(StandardCharsets.UTF_8));

        when(attachmentService.uploadAttachment(any(), any(), any(), any()))
                .thenThrow(new SecurityException("You are not a participant in this conversation"));

        mockMvc.perform(multipart("/api/conversations/CONV-1/attachments")
                        .file(file)
                        .principal(mockAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You are not a participant in this conversation"));
    }

    @Test
    void uploadAttachment_Returns404_WhenConversationNotFound() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.5 test".getBytes(StandardCharsets.UTF_8));

        when(attachmentService.uploadAttachment(any(), any(), any(), any()))
                .thenThrow(new NoSuchElementException("Conversation not found"));

        mockMvc.perform(multipart("/api/conversations/CONV-1/attachments")
                        .file(file)
                        .principal(mockAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Conversation not found"));
    }

    @Test
    void getAttachment_ReturnsFileContent_WhenAuthorized() throws Exception {
        byte[] content = "Sample PDF byte stream".getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(content);

        Attachment att = new Attachment();
        att.setAttachmentId("ATT-12345");
        att.setConversationId("CONV-1");
        att.setOriginalFilename("test.pdf");
        att.setMimeType("application/pdf");
        att.setSize(content.length);

        AttachmentService.AttachmentResource res = new AttachmentService.AttachmentResource(resource, att);

        when(attachmentService.getAttachmentResource("prof@univ.edu", "CONV-1", "ATT-12345"))
                .thenReturn(res);

        mockMvc.perform(get("/api/conversations/CONV-1/attachments/ATT-12345")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Length", String.valueOf(content.length)))
                .andExpect(header().string("Cache-Control", "private, max-age=3600"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(content));
    }

    @Test
    void downloadAttachment_SetsAttachmentDisposition() throws Exception {
        byte[] content = "Image bytes".getBytes(StandardCharsets.UTF_8);
        Resource resource = new ByteArrayResource(content);

        Attachment att = new Attachment();
        att.setAttachmentId("ATT-999");
        att.setConversationId("CONV-1");
        att.setOriginalFilename("photo.png");
        att.setMimeType("image/png");
        att.setSize(content.length);

        AttachmentService.AttachmentResource res = new AttachmentService.AttachmentResource(resource, att);

        when(attachmentService.getAttachmentResource("prof@univ.edu", "CONV-1", "ATT-999"))
                .thenReturn(res);

        mockMvc.perform(get("/api/conversations/CONV-1/attachments/ATT-999/download")
                        .principal(mockAuth))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment;")));
    }

    @Test
    void getAttachment_Returns403_WhenUnauthorized() throws Exception {
        when(attachmentService.getAttachmentResource(any(), any(), any()))
                .thenThrow(new SecurityException("You are not a participant in this conversation"));

        mockMvc.perform(get("/api/conversations/CONV-1/attachments/ATT-123")
                        .principal(mockAuth))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("You are not a participant in this conversation"));
    }

    @Test
    void getAttachment_Returns404_WhenNotFound() throws Exception {
        when(attachmentService.getAttachmentResource(any(), any(), any()))
                .thenThrow(new NoSuchElementException("Attachment not found"));

        mockMvc.perform(get("/api/conversations/CONV-1/attachments/ATT-MISSING")
                        .principal(mockAuth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Attachment not found"));
    }

    @Test
    void uploadAttachment_Returns415_WhenApplicationJsonSent() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/conversations/CONV-1/attachments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"hello\"}")
                        .principal(mockAuth))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Content-Type 'application/json' is not supported")));
    }

    @Test
    void uploadAttachment_Returns400_WhenFilePartMissing() throws Exception {
        mockMvc.perform(multipart("/api/conversations/CONV-1/attachments")
                        .param("content", "no file attached")
                        .principal(mockAuth))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("file")));
    }

    @Test
    void uploadMessageWithAttachment_Returns201Created_OnSuccess() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.5 test".getBytes(StandardCharsets.UTF_8));

        AttachmentResponse attResponse = new AttachmentResponse();
        attResponse.setAttachmentId("ATT-12345");
        attResponse.setConversationId("CONV-1");
        attResponse.setOriginalFilename("doc.pdf");
        attResponse.setMimeType("application/pdf");
        attResponse.setSize(100);

        MessageResponse msgResponse = new MessageResponse();
        msgResponse.setMessageId("MSG-789");
        msgResponse.setConversationId("CONV-1");
        msgResponse.setSenderId("prof@univ.edu");
        msgResponse.setContent("doc.pdf");
        msgResponse.setMessageType("PDF");
        msgResponse.setAttachmentId("ATT-12345");
        msgResponse.setAttachment(attResponse);

        when(attachmentService.uploadMessageAttachment(eq("prof@univ.edu"), eq("CONV-1"), any(), eq("doc.pdf")))
                .thenReturn(msgResponse);

        mockMvc.perform(multipart("/api/conversations/CONV-1/messages/upload")
                        .file(file)
                        .param("content", "doc.pdf")
                        .principal(mockAuth))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.messageId").value("MSG-789"))
                .andExpect(jsonPath("$.messageType").value("PDF"))
                .andExpect(jsonPath("$.attachment.attachmentId").value("ATT-12345"));
    }

    @Test
    void serialization_NoCircularReference_SerializesCleanly() throws Exception {
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();

        AttachmentResponse att = new AttachmentResponse();
        att.setAttachmentId("ATT-1");
        att.setConversationId("CONV-1");
        att.setMessageId("MSG-1");
        att.setOriginalFilename("image.png");
        att.setMimeType("image/png");
        att.setSize(512);

        MessageResponse msg = new MessageResponse();
        msg.setMessageId("MSG-1");
        msg.setConversationId("CONV-1");
        msg.setContent("image.png");
        msg.setMessageType("IMAGE");
        msg.setAttachmentId("ATT-1");
        msg.setAttachment(att);

        // Neither serialization should throw StackOverflowError
        String attJson = mapper.writeValueAsString(att);
        String msgJson = mapper.writeValueAsString(msg);

        org.junit.jupiter.api.Assertions.assertNotNull(attJson);
        org.junit.jupiter.api.Assertions.assertNotNull(msgJson);
        org.junit.jupiter.api.Assertions.assertTrue(msgJson.contains("\"attachmentId\":\"ATT-1\""));
    }
}
