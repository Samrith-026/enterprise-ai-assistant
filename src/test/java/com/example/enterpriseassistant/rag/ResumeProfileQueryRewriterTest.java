package com.example.enterpriseassistant.rag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ResumeProfileQueryRewriterTest {
    private final ResumeProfileQueryRewriter rewriter = new ResumeProfileQueryRewriter();

    @Test
    void expandsVagueSelfDescriptionQuestionsWithResumeTopics() {
        String rewritten = rewriter.rewrite("Tell me about myself");

        assertThat(rewritten).startsWith("Tell me about myself")
                .contains("professional summary", "work experience", "technical skills", "projects");
    }

    @Test
    void expandsToolQuestionsForTheNamedResumeProject() {
        String rewritten = rewriter.rewrite("What tools did you use in the Enterprise AI Assistant?");

        assertThat(rewritten).contains("Enterprise AI Assistant", "frameworks", "libraries", "developer tools");
    }

    @Test
    void leavesAlreadySpecificUnrelatedQuestionsUnchanged() {
        String question = "What is a vector database?";

        assertThat(rewriter.rewrite(question)).isEqualTo(question);
    }
}
