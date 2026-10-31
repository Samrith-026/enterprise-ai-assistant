package com.example.enterpriseassistant.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfiguration {
    @Bean
    ChatMemory chatMemory(@Value("${assistant.memory.max-messages}") int maxMessages) {
        return MessageWindowChatMemory.builder().maxMessages(maxMessages).build();
    }
}
