package com.example.enterpriseassistant.rag;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrievalDiagnosticsTest {
    @Test
    void reportsResumeMatchesWithoutReadingOrExposingDocumentText() {
        List<Document> retrieved = List.of(
                new Document("private resume content", Map.of("title", "Candidate Resume.pdf")),
                new Document("handbook content", Map.of("title", "Employee Handbook.pdf")));

        assertThat(RagRetrievalDiagnostics.summarize(retrieved))
                .isEqualTo(new RagRetrievalDiagnostics.Summary(2, 1));
    }

    @Test
    void reportsNoResumeChunksWhenRetrievalIsEmpty() {
        assertThat(RagRetrievalDiagnostics.summarize(List.of()))
                .isEqualTo(new RagRetrievalDiagnostics.Summary(0, 0));
    }
}
