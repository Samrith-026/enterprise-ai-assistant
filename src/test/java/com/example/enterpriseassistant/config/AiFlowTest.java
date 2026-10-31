package com.example.enterpriseassistant.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.enterpriseassistant.dto.ChatRequest;
import com.example.enterpriseassistant.repository.ChatInteractionRepository;
import com.example.enterpriseassistant.service.ChatService;
import com.example.enterpriseassistant.service.DepartmentDirectoryService;
import com.example.enterpriseassistant.tool.DepartmentContactTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AiFlowTest {
    private final ChatModel model = mock(ChatModel.class);
    private final VectorStore store = mock(VectorStore.class);
    private final ChatMemory memory = new ChatMemoryConfiguration().chatMemory(20);
    private final DepartmentDirectoryService directory = spy(new DepartmentDirectoryService());

    private ChatService createService() throws Exception {
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
        ChatClient client = new AiConfiguration().enterpriseChatClient(ChatClient.builder(model), memory, store,
                new DepartmentContactTool(directory), new ClassPathResource("prompts/enterprise-assistant.st"),
                new ClassPathResource("prompts/rag-context.st"), 4, 0.5);
        return new ChatService(client, mock(ChatInteractionRepository.class));
    }

    @Test
    void actualAdvisorChainAddsRagContextAndIsolatesConversationMemory() throws Exception {
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(
                List.of(new Document("Employees receive 20 vacation days.", Map.of("title", "Handbook"))));
        List<Prompt> prompts = new ArrayList<>();
        when(model.call(any(Prompt.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0));
            return reply("Acknowledged");
        });
        ChatService service = createService();
        service.chat(new ChatRequest("alice", "My project codename is Lantern."));
        service.chat(new ChatRequest("alice", "What is my project codename?"));
        service.chat(new ChatRequest("bob", "What is my project codename?"));
        assertThat(prompts.get(0).getContents()).contains("20 vacation days", "enterprise knowledge assistant");
        assertThat(prompts.get(1).getContents()).contains("Lantern");
        assertThat(prompts.get(2).getContents()).doesNotContain("Lantern");
        assertThat(memory.get("alice")).isNotEmpty();
        assertThat(memory.get("bob")).isNotEmpty();
        verify(store, times(3)).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void registeredToolExecutesAndResultReturnsToModelEvenWithEmptyRetrieval() throws Exception {
        when(store.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        AssistantMessage toolRequest = AssistantMessage.builder().content("")
                .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "getDepartmentContact",
                        "{\"department\":\"hr\"}"))).build();
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(toolRequest))))
                .thenAnswer(invocation -> {
                    Prompt prompt = invocation.getArgument(0);
                    assertThat(prompt.getInstructions().stream()
                            .filter(ToolResponseMessage.class::isInstance)
                            .map(ToolResponseMessage.class::cast)
                            .flatMap(message -> message.getResponses().stream())
                            .map(ToolResponseMessage.ToolResponse::responseData))
                            .anySatisfy(data -> assertThat(data).contains("hr@company.example"));
                    return reply("Contact hr@company.example");
                });
        assertThat(createService().chat(new ChatRequest("tool-demo", "What is the HR contact email?" )).answer())
                .contains("hr@company.example");
        verify(directory).findContact("HR");
        verify(model, times(2)).call(any(Prompt.class));
        verify(store, times(1)).similaritySearch(any(SearchRequest.class));
    }

    private ChatResponse reply(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
}
