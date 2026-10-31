package com.example.enterpriseassistant.service;

import com.example.enterpriseassistant.dto.ChatRequest;
import com.example.enterpriseassistant.entity.ChatInteraction;
import com.example.enterpriseassistant.exception.AiServiceException;
import com.example.enterpriseassistant.repository.ChatInteractionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatServiceTest {
    private final ChatModel model = mock(ChatModel.class);
    private final ChatInteractionRepository repository = mock(ChatInteractionRepository.class);
    private final ChatService service = new ChatService(ChatClient.builder(model).build(), repository);

    @BeforeEach
    void configureModelOptions() {
        when(model.getOptions()).thenReturn(ToolCallingChatOptions.builder().build());
    }

    @Test
    void persistsCompletedInteractionAndUsesSameTimestampInResponse() {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("20 days")))));
        var response = service.chat(new ChatRequest("demo", "Vacation?"));
        var captor = ArgumentCaptor.forClass(ChatInteraction.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getId()).isNotNull();
        assertThat(captor.getValue().getConversationId()).isEqualTo("demo");
        assertThat(captor.getValue().getQuestion()).isEqualTo("Vacation?");
        assertThat(captor.getValue().getAnswer()).isEqualTo(response.answer());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(response.timestamp());
    }

    @Test
    void doesNotPersistFailedAiCalls() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider failure"));
        assertThatThrownBy(() -> service.chat(new ChatRequest("demo", "Hi"))).isInstanceOf(AiServiceException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void doesNotMislabelPersistenceFailuresAsAiFailures() {
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Hello")))));
        when(repository.save(any())).thenThrow(new DataAccessResourceFailureException("DB offline"));
        assertThatThrownBy(() -> service.chat(new ChatRequest("demo", "Hi")))
                .isInstanceOf(DataAccessResourceFailureException.class);
        verify(model, times(1)).call(any(Prompt.class));
    }
}
