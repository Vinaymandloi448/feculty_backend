package com.facultyconnect.feculty_backend.repository;

import com.facultyconnect.feculty_backend.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    Optional<Message> findByMessageId(String messageId);

    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);

    List<Message> findByConversationIdOrderByCreatedAtDesc(String conversationId);

    List<Message> findByConversationIdAndSenderId(String conversationId, String senderId);
}
