package com.facultyconnect.feculty_backend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Service to validate file uploads:
 * - Enforces the 5 MB file size limit
 * - Whitelists allowed file extensions (.pdf, .jpg, .jpeg, .png, .webp)
 * - Whitelists allowed MIME types
 * - Performs deep content inspection via magic bytes (file signatures)
 * - Sanitizes filenames and prevents path traversal & executable file uploads
 */
@Service
public class FileValidationService {

    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024; // 5 MB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "webp"
    );

    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "image/jpeg",
            "image/jpg",
            "image/pjpeg",
            "image/png",
            "image/webp"
    );

    // Magic bytes signatures
    private static final byte[] JPEG_MAGIC = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF_MAGIC = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D}; // %PDF-
    private static final byte[] RIFF_MAGIC = new byte[]{0x52, 0x49, 0x46, 0x46}; // RIFF
    private static final byte[] WEBP_MAGIC = new byte[]{0x57, 0x45, 0x42, 0x50}; // WEBP

    public record ValidatedFile(
            String originalFilename,
            String safeExtension,
            String canonicalMimeType,
            long size
    ) {}

    /**
     * Validates an uploaded MultipartFile.
     *
     * @param file the uploaded file
     * @return ValidatedFile with sanitized filename and normalized metadata
     * @throws IllegalArgumentException on validation failure
     */
    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required and cannot be empty");
        }

        // 1. Size check
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "File size exceeds the 5 MB limit (file size: " + file.getSize() + " bytes)");
        }

        // 2. Filename check & path traversal prevention
        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.isBlank()) {
            throw new IllegalArgumentException("File name is missing");
        }

        // Reject null bytes and path traversal patterns
        if (rawFilename.contains("\0") || rawFilename.contains("..") || rawFilename.contains("/") || rawFilename.contains("\\")) {
            // Also normalize via Paths to check
            String basename = Paths.get(rawFilename).getFileName().toString();
            if (basename.contains("..") || basename.contains("\0")) {
                throw new IllegalArgumentException("Filename contains illegal characters or path traversal");
            }
            rawFilename = basename;
        }

        String sanitizedFilename = sanitizeFilename(rawFilename);
        String extension = getExtension(sanitizedFilename);

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException(
                    "Unsupported file format. Only PDF, JPEG, PNG, and WebP files are allowed");
        }

        // 3. MIME type check
        String declaredMimeType = file.getContentType();
        if (declaredMimeType != null) {
            declaredMimeType = declaredMimeType.toLowerCase(Locale.ROOT).trim();
            // Handle content types with parameters like application/pdf; charset=binary
            int semicolonIdx = declaredMimeType.indexOf(';');
            if (semicolonIdx > 0) {
                declaredMimeType = declaredMimeType.substring(0, semicolonIdx).trim();
            }
            if (!ALLOWED_MIME_TYPES.contains(declaredMimeType) && !"application/octet-stream".equals(declaredMimeType)) {
                throw new IllegalArgumentException(
                        "Unsupported MIME type: " + declaredMimeType + ". Only PDF, JPEG, PNG, and WebP are allowed");
            }
        }

        // 4. Magic bytes content inspection
        byte[] headerBytes = readHeaderBytes(file, 1024);
        String detectedMimeType = detectMimeType(headerBytes);

        if (detectedMimeType == null) {
            throw new IllegalArgumentException(
                    "Invalid file content: signature does not match allowed types (PDF, JPEG, PNG, WebP)");
        }

        // 5. Cross-check extension against detected signature
        validateExtensionAgainstMime(extension, detectedMimeType);

        String canonicalMimeType = detectedMimeType;
        String safeExt = normalizeExtension(extension, detectedMimeType);

        return new ValidatedFile(
                sanitizedFilename,
                safeExt,
                canonicalMimeType,
                file.getSize()
        );
    }

    /**
     * Read up to maxBytes from the file input stream.
     */
    private byte[] readHeaderBytes(MultipartFile file, int maxBytes) {
        try (InputStream is = file.getInputStream()) {
            return is.readNBytes(maxBytes);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read file contents for inspection", e);
        }
    }

    /**
     * Detect MIME type strictly from magic bytes signature.
     */
    public String detectMimeType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }

        // Check PNG
        if (bytes.length >= PNG_MAGIC.length && matchPrefix(bytes, PNG_MAGIC)) {
            return "image/png";
        }

        // Check JPEG
        if (bytes.length >= JPEG_MAGIC.length && matchPrefix(bytes, JPEG_MAGIC)) {
            return "image/jpeg";
        }

        // Check WebP: bytes 0..3 == "RIFF", bytes 8..11 == "WEBP"
        if (bytes.length >= 12
                && matchPrefix(bytes, RIFF_MAGIC)
                && matchOffset(bytes, 8, WEBP_MAGIC)) {
            return "image/webp";
        }

        // Check PDF: starts with "%PDF-" or contains "%PDF-" in first 1024 bytes
        if (bytes.length >= PDF_MAGIC.length && matchPrefix(bytes, PDF_MAGIC)) {
            return "application/pdf";
        }
        if (containsPdfHeader(bytes)) {
            return "application/pdf";
        }

        return null;
    }

    private boolean matchPrefix(byte[] data, byte[] prefix) {
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean matchOffset(byte[] data, int offset, byte[] target) {
        if (data.length < offset + target.length) {
            return false;
        }
        for (int i = 0; i < target.length; i++) {
            if (data[offset + i] != target[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean containsPdfHeader(byte[] bytes) {
        int maxSearch = Math.min(bytes.length - PDF_MAGIC.length, 1024);
        for (int i = 0; i <= maxSearch; i++) {
            boolean match = true;
            for (int j = 0; j < PDF_MAGIC.length; j++) {
                if (bytes[i + j] != PDF_MAGIC[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    private void validateExtensionAgainstMime(String extension, String detectedMimeType) {
        switch (detectedMimeType) {
            case "application/pdf" -> {
                if (!"pdf".equals(extension)) {
                    throw new IllegalArgumentException(
                            "File extension '." + extension + "' does not match detected PDF content");
                }
            }
            case "image/jpeg" -> {
                if (!"jpg".equals(extension) && !"jpeg".equals(extension)) {
                    throw new IllegalArgumentException(
                            "File extension '." + extension + "' does not match detected JPEG content");
                }
            }
            case "image/png" -> {
                if (!"png".equals(extension)) {
                    throw new IllegalArgumentException(
                            "File extension '." + extension + "' does not match detected PNG content");
                }
            }
            case "image/webp" -> {
                if (!"webp".equals(extension)) {
                    throw new IllegalArgumentException(
                            "File extension '." + extension + "' does not match detected WebP content");
                }
            }
            default -> throw new IllegalArgumentException("Unsupported file type signature detected");
        }
    }

    private String normalizeExtension(String extension, String mimeType) {
        if ("image/jpeg".equals(mimeType)) {
            return "jpeg".equalsIgnoreCase(extension) ? "jpeg" : "jpg";
        }
        return extension.toLowerCase(Locale.ROOT);
    }

    /**
     * Extract lowercase extension without dot.
     */
    public String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * Sanitize original filename for display and metadata storage.
     */
    public String sanitizeFilename(String filename) {
        // Strip path if still present
        String name = Paths.get(filename).getFileName().toString();
        // Remove control characters, newlines, tabs, null bytes
        name = name.replace("\r", "").replace("\n", "").replace("\t", "").replace("\0", "");
        // Remove dangerous characters
        name = name.replaceAll("[<>:\"/\\\\|?*]", "_");
        // Limit length
        if (name.length() > 255) {
            String ext = getExtension(name);
            String base = name.substring(0, 240);
            name = ext.isEmpty() ? base : base + "." + ext;
        }
        return name.trim();
    }
}
