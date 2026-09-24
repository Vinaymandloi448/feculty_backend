package com.facultyconnect.feculty_backend.repository;

import com.facultyconnect.feculty_backend.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    Optional<Conversation> findByConversationId(String conversationId);

    List<Conversation> findByParticipantIdsContaining(String email);

    /**
     * Find a conversation where BOTH emails are in participantIds.
     * Used to detect whether a one-to-one conversation already exists
     * between two faculty members, regardless of order.
     */
    Optional<Conversation> findByParticipantIdsContainingAndParticipantIdsContaining(
            String emailA, String emailB);
}
