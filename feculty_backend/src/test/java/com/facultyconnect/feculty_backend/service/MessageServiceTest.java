package com.facultyconnect.feculty_backend.service;

import com.facultyconnect.feculty_backend.dto.MessageResponse;
import com.facultyconnect.feculty_backend.model.Attachment;
import com.facultyconnect.feculty_backend.model.Conversation;
import com.facultyconnect.feculty_backend.model.Message;
import com.facultyconnect.feculty_backend.repository.AttachmentRepository;
import com.facultyconnect.feculty_backend.repository.ConversationRepository;
import com.facultyconnect.feculty_backend.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private AttachmentRepository attachmentRepository;

    @InjectMocks
    private MessageService messageService;

    private final String sender = "alice@univ.edu";
    private final String receiver = "bob@univ.edu";
    private final String conversationId = "CONV-1234";

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = new Conversation();
        conversation.setConversationId(conversationId);
        conversation.setParticipantIds(List.of(sender, receiver));
    }

    @Test
    void sendMessage_TextOnly_PreservesExistingBehavior() {
        when(conversationRepository.findByConversationId(conversationId)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = messageService.sendMessage(sender, conversationId, "Hello Bob!");

        assertNotNull(response);
        assertEquals("TEXT", response.getMessageType());
        assertEquals("Hello Bob!", response.getContent());
        assertNull(response.getAttachmentId());
        assertNull(response.getAttachment());
        verify(attachmentRepository, never()).save(any());
    }

    @Test
    void sendMessage_WithImageAttachment_SetsTypeAndLinksAttachment() {
        String attachmentId = "ATT-5678";
        Attachment attachment = new Attachment();
        attachment.setAttachmentId(attachmentId);
        attachment.setConversationId(conversationId);
        attachment.setOriginalFilename("screenshot.png");
        attachment.setMimeType("image/png");
        attachment.setSize(2048);
        attachment.setUploadTimestamp(Instant.now());

        when(conversationRepository.findByConversationId(conversationId)).thenReturn(Optional.of(conversation));
        when(attachmentRepository.findByAttachmentId(attachmentId)).thenReturn(Optional.of(attachment));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        MessageResponse response = messageService.sendMessage(sender, conversationId, "Look at this screenshot", attachmentId);

        assertNotNull(response);
        assertEquals("IMAGE", response.getMessageType());
        assertEquals("Look at this screenshot", response.getContent());
        assertEquals(attachmentId, response.getAttachmentId());
        assertNotNull(response.getAttachment());
        assertEquals("screenshot.png", response.getAttachment().getOriginalFilename());
        verify(attachmentRepository, times(1)).save(attachment);
    }

    @Test
    void sendMessage_WithPdfAttachment_DefaultCaptionIsFilename() {
        String attachmentId = "ATT-PDF-1";
        Attachment attachment = new Attachment();
        attachment.setAttachmentId(attachmentId);
        attachment.setConversationId(conversationId);
        attachment.setOriginalFilename("paper.pdf");
        attachment.setMimeType("application/pdf");
        attachment.setSize(10240);
        attachment.setUploadTimestamp(Instant.now());

        when(conversationRepository.findByConversationId(conversationId)).thenReturn(Optional.of(conversation));
        when(attachmentRepository.findByAttachmentId(attachmentId)).thenReturn(Optional.of(attachment));
        when(messageRepository.save(any(Message.class))).thenAnswer(inv -> inv.getArgument(0));

        // Content passed is null, should default to paper.pdf
        MessageResponse response = messageService.sendMessage(sender, conversationId, null, attachmentId);

        assertNotNull(response);
        assertEquals("PDF", response.getMessageType());
        assertEquals("paper.pdf", response.getContent());
        assertEquals(attachmentId, response.getAttachmentId());
    }

    @Test
    void sendMessage_ThrowsException_WhenAttachmentBelongsToDifferentConversation() {
        String attachmentId = "ATT-OTHER";
        Attachment attachment = new Attachment();
        attachment.setAttachmentId(attachmentId);
        attachment.setConversationId("CONV-DIFFERENT");

        when(conversationRepository.findByConversationId(conversationId)).thenReturn(Optional.of(conversation));
        when(attachmentRepository.findByAttachmentId(attachmentId)).thenReturn(Optional.of(attachment));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                messageService.sendMessage(sender, conversationId, "Test", attachmentId));
        assertTrue(ex.getMessage().contains("does not belong to this conversation"));
    }

    @Test
    void getConversationMessages_ReturnsMessagesWithEagerLoadedAttachments() {
        when(conversationRepository.findByConversationId(conversationId)).thenReturn(Optional.of(conversation));

        Message textMsg = new Message();
        textMsg.setMessageId("MSG-1");
        textMsg.setConversationId(conversationId);
        textMsg.setSenderId(sender);
        textMsg.setReceiverId(receiver);
        textMsg.setContent("Hello");
        textMsg.setMessageType("TEXT");

        Message attMsg = new Message();
        attMsg.setMessageId("MSG-2");
        attMsg.setConversationId(conversationId);
        attMsg.setSenderId(sender);
        attMsg.setReceiverId(receiver);
        attMsg.setContent("paper.pdf");
        attMsg.setMessageType("PDF");
        attMsg.setAttachmentId("ATT-1");

        when(messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId))
                .thenReturn(List.of(textMsg, attMsg));

        Attachment attachment = new Attachment();
        attachment.setAttachmentId("ATT-1");
        attachment.setConversationId(conversationId);
        attachment.setOriginalFilename("paper.pdf");
        attachment.setMimeType("application/pdf");
        attachment.setSize(1024);
        attachment.setUploadTimestamp(Instant.now());

        when(attachmentRepository.findByConversationIdOrderByUploadTimestampDesc(conversationId))
                .thenReturn(List.of(attachment));

        List<MessageResponse> responses = messageService.getConversationMessages(sender, conversationId);

        assertEquals(2, responses.size());
        assertNull(responses.get(0).getAttachment());
        assertNotNull(responses.get(1).getAttachment());
        assertEquals("paper.pdf", responses.get(1).getAttachment().getOriginalFilename());
    }
}
