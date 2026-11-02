package com.example.enterpriseassistant.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.example.enterpriseassistant.tool.DepartmentContactTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.Resource;

@Configuration
public class AiConfiguration {
    @Bean
    ChatClient enterpriseChatClient(ChatClient.Builder builder, ChatMemory memory, VectorStore vectorStore,
            DepartmentContactTool tool, ObjectProvider<SyncMcpToolCallbackProvider> mcpTools,
            @Value("classpath:prompts/enterprise-assistant.st") Resource systemPrompt,
            @Value("classpath:prompts/rag-context.st") Resource ragPrompt,
            @Value("${assistant.rag.top-k}") int topK,
            @Value("${assistant.rag.similarity-threshold}") double threshold) throws IOException {
        var memoryAdvisor = MessageChatMemoryAdvisor.builder(memory).build();
        // Run retrieval once per turn, outside the auto-registered tool loop (+300).
        var ragAdvisor = QuestionAnswerAdvisor.builder(vectorStore)
                .order(Ordered.HIGHEST_PRECEDENCE + 250)
                .searchRequest(SearchRequest.builder().topK(topK).similarityThreshold(threshold).build())
                .promptTemplate(PromptTemplate.builder()
                        .template(ragPrompt.getContentAsString(StandardCharsets.UTF_8)).build())
                .build();
        var chatClientBuilder = builder.defaultSystem(systemPrompt.getContentAsString(StandardCharsets.UTF_8))
                .defaultAdvisors(memoryAdvisor, ragAdvisor);
        SyncMcpToolCallbackProvider provider = mcpTools.getIfAvailable();
        if (provider == null) {
            chatClientBuilder.defaultTools(tool);
        } else {
            chatClientBuilder.defaultTools(tool, provider);
        }
        return chatClientBuilder.build();
    }

    @Bean
    TokenTextSplitter tokenTextSplitter() {
        return TokenTextSplitter.builder().withChunkSize(500).withMinChunkLengthToEmbed(0).build();
    }
}
