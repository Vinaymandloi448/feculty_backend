package com.facultyconnect.feculty_backend.service;

import com.facultyconnect.feculty_backend.dto.ConversationResponse;
import com.facultyconnect.feculty_backend.model.Conversation;
import com.facultyconnect.feculty_backend.model.Faculty;
import com.facultyconnect.feculty_backend.repository.ConversationRepository;
import com.facultyconnect.feculty_backend.repository.FacultyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private FacultyRepository facultyRepository;

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(conversationRepository, facultyRepository);
    }

    @Test
    void createOrGetConversation_ReturnsExistingConversation_WhenAlreadyExists() {
        String currentEmail = "john@university.edu";
        String targetFacultyId = "FAC003";
        String targetEmail = "jane@university.edu";

        Faculty targetFaculty = new Faculty();
        targetFaculty.setFacultyId(targetFacultyId);
        targetFaculty.setEmail(targetEmail);

        Conversation existingConversation = new Conversation();
        existingConversation.setConversationId("CONV-12345678");
        existingConversation.setParticipantIds(List.of(currentEmail, targetEmail));

        when(facultyRepository.findByFacultyId(targetFacultyId)).thenReturn(Optional.of(targetFaculty));
        when(conversationRepository.findByParticipantIds(currentEmail, targetEmail))
                .thenReturn(Optional.of(existingConversation));

        ConversationResponse response = conversationService.createOrGetConversation(currentEmail, targetFacultyId);

        assertNotNull(response);
        assertEquals("CONV-12345678", response.getConversationId());
        assertTrue(response.getParticipantIds().containsAll(List.of(currentEmail, targetEmail)));
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void createOrGetConversation_CreatesNewConversation_WhenNotExists() {
        String currentEmail = "john@university.edu";
        String targetFacultyId = "FAC003";
        String targetEmail = "jane@university.edu";

        Faculty targetFaculty = new Faculty();
        targetFaculty.setFacultyId(targetFacultyId);
        targetFaculty.setEmail(targetEmail);

        when(facultyRepository.findByFacultyId(targetFacultyId)).thenReturn(Optional.of(targetFaculty));
        when(conversationRepository.findByParticipantIds(currentEmail, targetEmail))
                .thenReturn(Optional.empty());

        when(conversationRepository.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conv = invocation.getArgument(0);
            return conv;
        });

        ConversationResponse response = conversationService.createOrGetConversation(currentEmail, targetFacultyId);

        assertNotNull(response);
        assertNotNull(response.getConversationId());
        assertTrue(response.getParticipantIds().containsAll(List.of(currentEmail, targetEmail)));
        verify(conversationRepository, times(1)).save(any(Conversation.class));
    }

    @Test
    void createOrGetConversation_ThrowsException_WhenSelfConversation() {
        String currentEmail = "john@university.edu";
        String targetFacultyId = "FAC001";

        Faculty targetFaculty = new Faculty();
        targetFaculty.setFacultyId(targetFacultyId);
        targetFaculty.setEmail(currentEmail);

        when(facultyRepository.findByFacultyId(targetFacultyId)).thenReturn(Optional.of(targetFaculty));

        assertThrows(IllegalArgumentException.class, () ->
                conversationService.createOrGetConversation(currentEmail, targetFacultyId)
        );
    }
}
