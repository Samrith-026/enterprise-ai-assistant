package com.example.enterpriseassistant.controller;

import java.time.Instant;

import com.example.enterpriseassistant.dto.ChatRequest;
import com.example.enterpriseassistant.dto.ChatResponse;
import com.example.enterpriseassistant.exception.AiServiceException;
import com.example.enterpriseassistant.exception.GlobalExceptionHandler;
import com.example.enterpriseassistant.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ChatControllerTest {
    private ChatService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ChatService.class);
        mvc = MockMvcBuilders.standaloneSetup(new ChatController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void returnsAnswer() throws Exception {
        when(service.chat(any())).thenReturn(new ChatResponse("demo-001", "20 days", Instant.EPOCH));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content(""" 
                                {"conversationId":"demo-001","message":"Vacation policy?"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("20 days"))
                .andExpect(jsonPath("$.conversationId").value("demo-001"));
        verify(service).chat(new ChatRequest("demo-001", "Vacation policy?"));
    }

    @Test
    void rejectsInvalidInputBeforeCallingService() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"\",\"message\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.message").exists());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void hidesProviderDetails() throws Exception {
        when(service.chat(any())).thenThrow(new AiServiceException(new RuntimeException("sensitive-provider-detail")));
        mvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"demo\",\"message\":\"Hi\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("AI_SERVICE_FAILED"))
                .andExpect(jsonPath("$.message").value("The AI service could not complete the request."))
                .andExpect(jsonPath("$.stackTrace").doesNotExist());
    }
}
