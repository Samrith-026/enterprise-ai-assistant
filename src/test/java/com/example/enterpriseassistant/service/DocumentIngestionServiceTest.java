package com.example.enterpriseassistant.service;

import java.util.List;

import com.example.enterpriseassistant.dto.DocumentRequest;
import com.example.enterpriseassistant.exception.DocumentIngestionException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class DocumentIngestionServiceTest {
    private final VectorStore store = mock(VectorStore.class);
    private final TokenTextSplitter splitter = TokenTextSplitter.builder().withChunkSize(100)
            .withMinChunkSizeChars(20).withMinChunkLengthToEmbed(0).build();
    private final DocumentIngestionService service = new DocumentIngestionService(store, splitter);

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void splitsLongDocumentsAndPreservesMetadata() {
        var response = service.ingest(new DocumentRequest("Handbook", "Employees receive twenty vacation days annually. ".repeat(100)));
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass((Class) List.class);
        verify(store).add(captor.capture());
        assertThat(response.chunkCount()).isGreaterThan(1).isEqualTo(captor.getValue().size());
        for (int i = 0; i < captor.getValue().size(); i++) {
            var chunk = captor.getValue().get(i);
            assertThat(chunk.getText()).isNotBlank();
            assertThat(chunk.getMetadata()).containsEntry("title", "Handbook")
                    .containsEntry("documentId", response.documentId().toString())
                    .containsEntry("source", "json-api").containsEntry("chunkIndex", i);
        }
        assertThat(captor.getValue().stream().map(Document::getId).distinct().count()).isEqualTo(response.chunkCount());
    }

    @Test
    void wrapsVectorStoreFailures() {
        doThrow(new IllegalStateException("storage failed")).when(store).add(anyList());
        assertThatThrownBy(() -> service.ingest(new DocumentRequest("Handbook", "Some meaningful content.")))
                .isInstanceOf(DocumentIngestionException.class).hasCauseInstanceOf(IllegalStateException.class);
    }
}
