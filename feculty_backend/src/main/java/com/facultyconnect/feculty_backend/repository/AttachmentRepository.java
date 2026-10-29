package com.facultyconnect.feculty_backend.repository;

import com.facultyconnect.feculty_backend.model.Attachment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttachmentRepository extends MongoRepository<Attachment, String> {

    Optional<Attachment> findByAttachmentId(String attachmentId);

    List<Attachment> findByConversationIdOrderByUploadTimestampDesc(String conversationId);

    Optional<Attachment> findByMessageId(String messageId);
}
