package com.facultyconnect.feculty_backend.repository;

import com.facultyconnect.feculty_backend.model.Conversation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends MongoRepository<Conversation, String> {

    Optional<Conversation> findByConversationId(String conversationId);

    List<Conversation> findByParticipantIdsContaining(String email);

    /**
     * Find a conversation containing BOTH emails in the participantIds array.
     * Uses explicit MongoDB $all query to prevent duplicate BSON key errors
     * caused by derived query parsing.
     */
    @Query("{ 'participantIds': { '$all': [?0, ?1] } }")
    Optional<Conversation> findByParticipantIds(String emailA, String emailB);
}
