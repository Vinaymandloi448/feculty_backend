package com.facultyconnect.feculty_backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FileValidationServiceTest {

    private FileValidationService fileValidationService;

    // Magic bytes samples
    private static final byte[] PDF_BYTES = "%PDF-1.5 test pdf content stream".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] JPEG_BYTES = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 0x4A, 0x46};
    private static final byte[] PNG_BYTES = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D};
    private static final byte[] WEBP_BYTES = new byte[]{
            0x52, 0x49, 0x46, 0x46, // RIFF
            0x24, 0x00, 0x00, 0x00, // file size
            0x57, 0x45, 0x42, 0x50, // WEBP
            0x56, 0x50, 0x38, 0x20  // VP8 chunk
    };

    @BeforeEach
    void setUp() {
        fileValidationService = new FileValidationService();
    }

    @Test
    void validate_ValidPdf_ReturnsValidatedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecture_notes.pdf", "application/pdf", PDF_BYTES);

        FileValidationService.ValidatedFile result = fileValidationService.validate(file);

        assertNotNull(result);
        assertEquals("lecture_notes.pdf", result.originalFilename());
        assertEquals("pdf", result.safeExtension());
        assertEquals("application/pdf", result.canonicalMimeType());
        assertEquals(PDF_BYTES.length, result.size());
    }

    @Test
    void validate_ValidJpeg_ReturnsValidatedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", JPEG_BYTES);

        FileValidationService.ValidatedFile result = fileValidationService.validate(file);

        assertNotNull(result);
        assertEquals("avatar.jpg", result.originalFilename());
        assertEquals("jpg", result.safeExtension());
        assertEquals("image/jpeg", result.canonicalMimeType());
    }

    @Test
    void validate_ValidPng_ReturnsValidatedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "diagram.png", "image/png", PNG_BYTES);

        FileValidationService.ValidatedFile result = fileValidationService.validate(file);

        assertNotNull(result);
        assertEquals("diagram.png", result.originalFilename());
        assertEquals("png", result.safeExtension());
        assertEquals("image/png", result.canonicalMimeType());
    }

    @Test
    void validate_ValidWebp_ReturnsValidatedFile() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.webp", "image/webp", WEBP_BYTES);

        FileValidationService.ValidatedFile result = fileValidationService.validate(file);

        assertNotNull(result);
        assertEquals("photo.webp", result.originalFilename());
        assertEquals("webp", result.safeExtension());
        assertEquals("image/webp", result.canonicalMimeType());
    }

    @Test
    void validate_ThrowsException_WhenFileExceeds5Mb() {
        // Create dummy content of 5MB + 1 byte
        byte[] oversizedBytes = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(PDF_BYTES, 0, oversizedBytes, 0, PDF_BYTES.length);

        MockMultipartFile file = new MockMultipartFile(
                "file", "large.pdf", "application/pdf", oversizedBytes);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidationService.validate(file));
        assertTrue(ex.getMessage().contains("exceeds the 5 MB limit"));
    }

    @Test
    void validate_ThrowsException_WhenFileIsEmptyOrNull() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        IllegalArgumentException ex1 = assertThrows(IllegalArgumentException.class,
                () -> fileValidationService.validate(emptyFile));
        assertTrue(ex1.getMessage().contains("cannot be empty"));

        IllegalArgumentException ex2 = assertThrows(IllegalArgumentException.class,
                () -> fileValidationService.validate(null));
        assertTrue(ex2.getMessage().contains("cannot be empty"));
    }

    @Test
    void validate_ThrowsException_WhenUnsupportedExtension() {
        MockMultipartFile exeFile = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", new byte[]{0x4D, 0x5A, 0x00, 0x00});

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidationService.validate(exeFile));
        assertTrue(ex.getMessage().contains("Unsupported file format"));
    }

    @Test
    void validate_ThrowsException_WhenExecutableDisguisedAsPng() {
        // Filename has .png but content is executable MZ header
        byte[] fakePng = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "evil.png", "image/png", fakePng);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidationService.validate(disguised));
        assertTrue(ex.getMessage().contains("signature does not match allowed types"));
    }

    @Test
    void validate_ThrowsException_WhenExtensionMismatchesDetectedContent() {
        // PNG content named with .pdf extension
        MockMultipartFile mismatched = new MockMultipartFile(
                "file", "report.pdf", "application/pdf", PNG_BYTES);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> fileValidationService.validate(mismatched));
        assertTrue(ex.getMessage().contains("does not match detected PNG content"));
    }

    @Test
    void validate_SanitizesPathTraversalInFilename() {
        MockMultipartFile traversal = new MockMultipartFile(
                "file", "../../../../etc/notes.pdf", "application/pdf", PDF_BYTES);

        FileValidationService.ValidatedFile result = fileValidationService.validate(traversal);
        assertEquals("notes.pdf", result.originalFilename());
    }
}
