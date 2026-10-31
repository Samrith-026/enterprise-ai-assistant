package com.example.enterpriseassistant.controller;

import java.time.Instant;
import java.util.UUID;

import com.example.enterpriseassistant.dto.DocumentResponse;
import com.example.enterpriseassistant.exception.DocumentIngestionException;
import com.example.enterpriseassistant.exception.GlobalExceptionHandler;
import com.example.enterpriseassistant.service.DocumentIngestionService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class DocumentControllerTest {
    private final DocumentIngestionService service = mock(DocumentIngestionService.class);
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new DocumentController(service)).setControllerAdvice(new GlobalExceptionHandler()).build();

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
}
