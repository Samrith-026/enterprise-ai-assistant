package com.example.enterpriseassistant.config;

import com.example.enterpriseassistant.service.DepartmentDirectoryService;
import com.example.enterpriseassistant.tool.DepartmentContactTool;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConfigurationWiringTest {
    @Test
    void loadsApplicationYamlAndCreatesProductionConfigurationBeansWithoutExternalConnections() {
        try (var context = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE).properties("spring.main.banner-mode=off").run()) {
            assertThat(context.getBean(ChatClient.class)).isNotNull();
            assertThat(context.getBean(ChatMemory.class)).isNotNull();
            var manager = context.getBean(RedisCacheManager.class);
            assertThat(manager.getCache("departmentContacts")).isNotNull();
            assertThat(manager.getCacheConfigurations().get("departmentContacts")
                    .getTtlFunction().getTimeToLive("HR", "hr@company.example"))
                    .isEqualTo(java.time.Duration.ofMinutes(10));
            assertThat(context.getEnvironment().getProperty("spring.ai.vectorstore.pgvector.initialize-schema"))
                    .isEqualTo("true");
        }
    }

    @Configuration
    @Import({AiConfiguration.class, ChatMemoryConfiguration.class, CacheConfiguration.class,
            DepartmentDirectoryService.class, DepartmentContactTool.class})
    static class TestApplication {
        @Bean
        ChatClient.Builder chatClientBuilder() { return ChatClient.builder(mock(ChatModel.class)); }
        @Bean
        VectorStore vectorStore() { return mock(VectorStore.class); }
        @Bean
        SyncMcpToolCallbackProvider mcpToolCallbackProvider() {
            return new SyncMcpToolCallbackProvider(java.util.List.of());
        }
        @Bean
        RedisConnectionFactory redisConnectionFactory() { return mock(RedisConnectionFactory.class); }
    }
}
