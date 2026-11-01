package com.example.enterpriseassistant.controller;

import java.time.Instant;
import java.util.UUID;

import com.example.enterpriseassistant.dto.DocumentResponse;
import com.example.enterpriseassistant.dto.DocumentUploadResponse;
import com.example.enterpriseassistant.exception.DocumentIngestionException;
import com.example.enterpriseassistant.exception.GlobalExceptionHandler;
import com.example.enterpriseassistant.exception.InvalidDocumentException;
import com.example.enterpriseassistant.service.DocumentFileIngestionService;
import com.example.enterpriseassistant.service.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentControllerTest {
    private final DocumentIngestionService service = mock(DocumentIngestionService.class);
    private final DocumentFileIngestionService fileService = mock(DocumentFileIngestionService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new DocumentController(service, fileService)).setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void returnsCreatedWithChunkCount() throws Exception {
        when(service.ingest(any())).thenReturn(new DocumentResponse(UUID.randomUUID(), "Handbook", 2, Instant.EPOCH));
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Handbook\",\"content\":\"Vacation policy\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.chunkCount").value(2))
                .andExpect(jsonPath("$.documentId").isNotEmpty());
    }

    @Test
    void rejectsBlankContent() throws Exception {
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Handbook\",\"content\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void reportsIngestionFailure() throws Exception {
        when(service.ingest(any())).thenThrow(new DocumentIngestionException(new RuntimeException("private")));
        mvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Handbook\",\"content\":\"Vacation policy\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("DOCUMENT_INGESTION_FAILED"));
    }

    @Test
    void browserGetReturnsMethodNotAllowedInsteadOfInternalError() throws Exception {
        mvc.perform(get("/api/documents"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
        verifyNoInteractions(service);
    }

    @Test
    void missingRouteReturnsNotFoundInsteadOfInternalError() throws Exception {
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        verifyNoInteractions(service);
    }

    @Test
    void uploadsMultipartFileAndReturnsCreatedResponse() throws Exception {
        when(fileService.ingest(any())).thenReturn(new DocumentUploadResponse(UUID.randomUUID(), "policy.txt",
                "txt", 2, Instant.EPOCH));
        var file = new MockMultipartFile("file", "policy.txt", "text/plain", "Vacation policy content".getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents/upload")
                        .file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("policy.txt"))
                .andExpect(jsonPath("$.fileType").value("txt"))
                .andExpect(jsonPath("$.chunkCount").value(2));
    }

    @Test
    void mapsUnsupportedUploadToCleanBadRequest() throws Exception {
        when(fileService.ingest(any())).thenThrow(new InvalidDocumentException("Only PDF and TXT files are supported."));
        var file = new MockMultipartFile("file", "policy.docx", "application/octet-stream", new byte[] {1});
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents/upload")
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"))
                .andExpect(jsonPath("$.message").value("Only PDF and TXT files are supported."));
    }

    @Test
    void rejectsUploadWithoutFilePart() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/documents/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_DOCUMENT"));
        verifyNoInteractions(fileService);
    }
}
