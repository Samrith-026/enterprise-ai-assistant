package com.example.enterpriseassistant.service;

import java.time.Instant;

import com.example.enterpriseassistant.dto.ChatRequest;
import com.example.enterpriseassistant.dto.ChatResponse;
import com.example.enterpriseassistant.entity.ChatInteraction;
import com.example.enterpriseassistant.exception.AiServiceException;
import com.example.enterpriseassistant.repository.ChatInteractionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
public class ChatService {
    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private final ChatClient chatClient;
    private final ChatInteractionRepository repository;

    public ChatService(ChatClient chatClient, ChatInteractionRepository repository) {
        this.chatClient = chatClient;
        this.repository = repository;
    }

    public ChatResponse chat(ChatRequest request) {
        log.info("Chat request received conversationId={}; executing memory and RAG", request.conversationId());
        String answer;
        try {
            answer = chatClient.prompt().user(request.message())
                    .advisors(advisors -> advisors.param(ChatMemory.CONVERSATION_ID, request.conversationId()))
                    .call().content();
            if (answer == null || answer.isBlank()) {
                throw new IllegalStateException("Empty AI response");
            }
        } catch (RuntimeException ex) {
            throw new AiServiceException(ex);
        }
        Instant timestamp = Instant.now();
        // Repository transaction starts after the external call has completed.
        repository.save(new ChatInteraction(request.conversationId(), request.message(), answer, timestamp));
        log.info("Chat interaction saved conversationId={}", request.conversationId());
        return new ChatResponse(request.conversationId(), answer, timestamp);
    }
}
