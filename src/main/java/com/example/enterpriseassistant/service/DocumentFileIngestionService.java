package com.example.enterpriseassistant.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;

import com.example.enterpriseassistant.dto.DocumentResponse;
import com.example.enterpriseassistant.dto.DocumentUploadResponse;
import com.example.enterpriseassistant.exception.InvalidDocumentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentFileIngestionService {
    private static final Logger log = LoggerFactory.getLogger(DocumentFileIngestionService.class);
    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;
    private static final int MAX_TEXT_CHARACTERS = 200_000;
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String TEXT_CONTENT_TYPE = "text/plain";
    private static final String BINARY_CONTENT_TYPE = "application/octet-stream";

    private final DocumentIngestionService ingestionService;

    public DocumentFileIngestionService(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    public DocumentUploadResponse ingest(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("Choose a non-empty PDF or TXT file.");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidDocumentException("The file exceeds the 10 MB upload limit.");
        }

        String filename = safeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        String expectedContentType = switch (extension) {
            case "pdf" -> PDF_CONTENT_TYPE;
            case "txt" -> TEXT_CONTENT_TYPE;
            default -> throw new InvalidDocumentException("Only PDF and TXT files are supported.");
        };
        validateContentType(file.getContentType(), expectedContentType);

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException ex) {
            throw new InvalidDocumentException("The uploaded file could not be read.", ex);
        }
        if (bytes.length > MAX_FILE_BYTES) {
            throw new InvalidDocumentException("The file exceeds the 10 MB upload limit.");
        }

        String text = switch (extension) {
            case "pdf" -> extractPdfText(bytes);
            case "txt" -> decodeUtf8(bytes);
            default -> throw new InvalidDocumentException("Only PDF and TXT files are supported.");
        };
        text = text.trim();
        if (text.isBlank()) {
            throw new InvalidDocumentException("The uploaded document contains no extractable text.");
        }
        if (text.length() > MAX_TEXT_CHARACTERS) {
            throw new InvalidDocumentException("The extracted text exceeds the 200,000 character limit.");
        }

        String title = filename.substring(0, filename.lastIndexOf('.'));
        if (title.length() > 200) {
            throw new InvalidDocumentException("The filename must be 200 characters or fewer before its extension.");
        }

        log.info("File ingestion accepted fileType={} sizeBytes={}", extension, bytes.length);
        DocumentResponse stored = ingestionService.ingestUploaded(title, text, filename, extension);
        return new DocumentUploadResponse(stored.documentId(), filename, extension,
                stored.chunkCount(), stored.timestamp());
    }

    private String extractPdfText(byte[] bytes) {
        if (!hasPdfHeader(bytes)) {
            throw new InvalidDocumentException("The uploaded file is not a valid PDF.");
        }
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            return new PDFTextStripper().getText(pdf);
        } catch (IOException | RuntimeException ex) {
            throw new InvalidDocumentException("The PDF could not be read. It may be damaged or password-protected.", ex);
        }
    }

    private boolean hasPdfHeader(byte[] bytes) {
        int lastStart = Math.min(bytes.length - 5, 1024);
        for (int index = 0; index <= lastStart; index++) {
            if (bytes[index] == '%' && bytes[index + 1] == 'P' && bytes[index + 2] == 'D'
                    && bytes[index + 3] == 'F' && bytes[index + 4] == '-') {
                return true;
            }
        }
        return false;
    }

    private String decodeUtf8(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException ex) {
            throw new InvalidDocumentException("TXT files must use UTF-8 encoding.", ex);
        }
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new InvalidDocumentException("The uploaded file must have a filename.");
        }
        String basename = originalFilename.replace('\\', '/');
        basename = basename.substring(basename.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (basename.isBlank() || basename.length() > 255) {
            throw new InvalidDocumentException("The uploaded filename is invalid or too long.");
        }
        return basename;
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 1 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private void validateContentType(String suppliedContentType, String expectedContentType) {
        if (suppliedContentType == null || suppliedContentType.isBlank()) {
            return;
        }
        String mediaType = suppliedContentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (!mediaType.equals(expectedContentType) && !mediaType.equals(BINARY_CONTENT_TYPE)) {
            throw new InvalidDocumentException("The file type does not match its filename extension.");
        }
    }
}
