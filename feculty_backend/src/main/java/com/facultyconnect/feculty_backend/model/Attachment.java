package com.facultyconnect.feculty_backend.model;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB document storing metadata for uploaded attachments.
 * Private files are saved safely on disk and never exposed through public static directories.
 */
@Document(collection = "attachments")
public class Attachment {

    @Id
    private String id;

    @Indexed(unique = true)
    private String attachmentId;

    @Indexed
    private String conversationId;

    private String messageId;

    private String uploaderFacultyId;

    private String uploaderEmail;

    private String originalFilename;

    private String storedFilename;

    private String storageKey;

    private String mimeType;

    private long size;

    @CreatedDate
    private Instant uploadTimestamp;

    @LastModifiedDate
    private Instant updatedAt;

    public Attachment() {
    }

    // --- Getters and Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAttachmentId() {
        return attachmentId;
    }

    public void setAttachmentId(String attachmentId) {
        this.attachmentId = attachmentId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getUploaderFacultyId() {
        return uploaderFacultyId;
    }

    public void setUploaderFacultyId(String uploaderFacultyId) {
        this.uploaderFacultyId = uploaderFacultyId;
    }

    public String getUploaderEmail() {
        return uploaderEmail;
    }

    public void setUploaderEmail(String uploaderEmail) {
        this.uploaderEmail = uploaderEmail;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilename() {
        return storedFilename;
    }

    public void setStoredFilename(String storedFilename) {
        this.storedFilename = storedFilename;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Instant getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(Instant uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
