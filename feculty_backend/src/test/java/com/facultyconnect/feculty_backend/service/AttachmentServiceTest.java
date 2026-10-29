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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

    @Mock
    private AttachmentRepository attachmentRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private FacultyRepository facultyRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private FileValidationService fileValidationService;

    private AttachmentService attachmentService;

    @TempDir
    Path tempDir;

    private static final byte[] PDF_BYTES = "%PDF-1.5 test document stream".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};

    @BeforeEach
    void setUp() {
        fileValidationService = new FileValidationService();
        attachmentService = new AttachmentService(
                attachmentRepository,
                conversationRepository,
                facultyRepository,
                messageRepository,
                fileValidationService,
                messagingTemplate
        );
        attachmentService.setUploadRootForTesting(tempDir);
    }

    @Test
    void uploadAttachment_Success_Pdf() {
        String senderEmail = "prof.smith@univ.edu";
        String receiverEmail = "prof.jones@univ.edu";
        String conversationId = "CONV-12345678";

        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setParticipantIds(List.of(senderEmail, receiverEmail));

        Faculty faculty = new Faculty();
        faculty.setEmail(senderEmail);
        faculty.setFacultyId("FAC001");

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(conversation));
        when(facultyRepository.findByEmail(senderEmail))
                .thenReturn(Optional.of(faculty));

        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> {
            Attachment att = invocation.getArgument(0);
            return att;
        });

        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> {
            Message msg = invocation.getArgument(0);
            return msg;
        });

        MockMultipartFile file = new MockMultipartFile(
                "file", "syllabus.pdf", "application/pdf", PDF_BYTES);

        AttachmentResponse response = attachmentService.uploadAttachment(
                senderEmail, conversationId, file, "Course Syllabus");

        assertNotNull(response);
        assertEquals("syllabus.pdf", response.getOriginalFilename());
        assertEquals("application/pdf", response.getMimeType());
        assertEquals("FAC001", response.getUploaderFacultyId());
        assertEquals(senderEmail, response.getUploaderEmail());
        assertEquals(conversationId, response.getConversationId());
        assertNotNull(response.getDownloadUrl());
        assertNotNull(response.getViewUrl());

        // Verify message was created with type PDF and linked attachmentId
        assertNotNull(response.getMessage());
        assertEquals("PDF", response.getMessage().getMessageType());
        assertEquals("Course Syllabus", response.getMessage().getContent());
        assertEquals(response.getAttachmentId(), response.getMessage().getAttachmentId());

        // Verify broadcast occurred to topic
        verify(messagingTemplate, times(1))
                .convertAndSend(eq("/topic/conversation/" + conversationId), any(MessageResponse.class));
    }

    @Test
    void uploadAttachment_Success_PngImage_SetsMessageTypeImage() {
        String senderEmail = "prof.smith@univ.edu";
        String receiverEmail = "prof.jones@univ.edu";
        String conversationId = "CONV-12345678";

        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setParticipantIds(List.of(senderEmail, receiverEmail));

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(conversation));
        when(facultyRepository.findByEmail(senderEmail))
                .thenReturn(Optional.empty());

        when(attachmentRepository.save(any(Attachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(messageRepository.save(any(Message.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile file = new MockMultipartFile(
                "file", "chart.png", "image/png", PNG_BYTES);

        AttachmentResponse response = attachmentService.uploadAttachment(
                senderEmail, conversationId, file, null);

        assertNotNull(response);
        assertEquals("image/png", response.getMimeType());
        assertNotNull(response.getMessage());
        assertEquals("IMAGE", response.getMessage().getMessageType());
        // Default caption is original filename when caption is null
        assertEquals("chart.png", response.getMessage().getContent());
    }

    @Test
    void uploadAttachment_ThrowsSecurityException_WhenUserNotParticipant() {
        String nonParticipant = "hacker@evil.edu";
        String conversationId = "CONV-12345678";

        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setParticipantIds(List.of("prof.smith@univ.edu", "prof.jones@univ.edu"));

        when(conversationRepository.findByConversationId(conversationId))
                .thenReturn(Optional.of(conversation));

        MockMultipartFile file = new MockMultipartFile(
                "file", "syllabus.pdf", "application/pdf", PDF_BYTES);

        assertThrows(SecurityException.class, () ->
                attachmentService.uploadAttachment(nonParticipant, conversationId, file, null));

        verify(attachmentRepository, never()).save(any());
        verify(messageRepository, never()).save(any());
    }

    @Test
    void uploadAttachment_ThrowsNoSuchElementException_WhenConversationNotFound() {
        when(conversationRepository.findByConversationId("CONV-UNKNOWN"))
                .thenReturn(Optional.empty());

        MockMultipartFile file = new MockMultipartFile(
                "file", "syllabus.pdf", "application/pdf", PDF_BYTES);

        assertThrows(NoSuchElementException.class, () ->
                attachmentService.uploadAttachment("prof@univ.edu", "CONV-UNKNOWN", file, null));
    }

    @Test
    void getAttachmentResource_Success_ForParticipant() throws IOException {
        String currentEmail = "prof.smith@univ.edu";
        String conversationId = "CONV-123";
        String attachmentId = "ATT-456";

        // Create actual file in tempDir
        Path subDir = tempDir.resolve("conversations").resolve(conversationId);
        Files.createDirectories(subDir);
        Path testFile = subDir.resolve("stored_file.pdf");
        Files.write(testFile, PDF_BYTES);

        Attachment attachment = new Attachment();
        attachment.setAttachmentId(attachmentId);
        attachment.setConversationId(conversationId);
        attachment.setOriginalFilename("test.pdf");
        attachment.setMimeType("application/pdf");
        attachment.setSize(PDF_BYTES.length);
        attachment.setStorageKey("conversations/" + conversationId + "/stored_file.pdf");

        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setParticipantIds(List.of(currentEmail, "other@univ.edu"));

        when(attachmentRepository.findByAttachmentId(attachmentId)).thenReturn(Optional.of(attachment));
        when(conversationRepository.findByConversationId(conversationId)).thenReturn(Optional.of(conversation));

        AttachmentService.AttachmentResource res = attachmentService.getAttachmentResource(
                currentEmail, conversationId, attachmentId);

        assertNotNull(res);
        assertNotNull(res.resource());
        assertTrue(res.resource().exists());
        assertEquals("test.pdf", res.attachment().getOriginalFilename());
    }

    @Test
    void getAttachmentResource_ThrowsSecurityException_ForNonParticipant() {
        String currentEmail = "intruder@univ.edu";
        String conversationId = "CONV-123";
        String attachmentId = "ATT-456";

        Attachment attachment = new Attachment();
        attachment.setAttachmentId(attachmentId);
        attachment.setConversationId(conversationId);
        attachment.setStorageKey("conversations/" + conversationId + "/stored.pdf");

        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setParticipantIds(List.of("prof.a@univ.edu", "prof.b@univ.edu"));

        when(attachmentRepository.findByAttachmentId(attachmentId)).thenReturn(Optional.of(attachment));
        when(conversationRepository.findByConversationId(conversationId)).thenReturn(Optional.of(conversation));

        assertThrows(SecurityException.class, () ->
                attachmentService.getAttachmentResource(currentEmail, conversationId, attachmentId));
    }

    @Test
    void getAttachmentResource_ThrowsNoSuchElementException_WhenAttachmentMissing() {
        when(attachmentRepository.findByAttachmentId("ATT-MISSING")).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class, () ->
                attachmentService.getAttachmentResource("user@univ.edu", "CONV-1", "ATT-MISSING"));
    }

    @Test
    void getConversationAttachments_Success_ForParticipant() {
        String currentEmail = "prof.smith@univ.edu";
        String conversationId = "CONV-123";

        Conversation conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setParticipantIds(List.of(currentEmail, "other@univ.edu"));

        Attachment att = new Attachment();
        att.setAttachmentId("ATT-1");
        att.setConversationId(conversationId);
        att.setOriginalFilename("paper.pdf");
        att.setMimeType("application/pdf");
        att.setSize(100);
        att.setUploadTimestamp(Instant.now());

        when(conversationRepository.findByConversationId(conversationId)).thenReturn(Optional.of(conversation));
        when(attachmentRepository.findByConversationIdOrderByUploadTimestampDesc(conversationId))
                .thenReturn(List.of(att));

        List<AttachmentResponse> list = attachmentService.getConversationAttachments(currentEmail, conversationId);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("ATT-1", list.get(0).getAttachmentId());
    }
}
