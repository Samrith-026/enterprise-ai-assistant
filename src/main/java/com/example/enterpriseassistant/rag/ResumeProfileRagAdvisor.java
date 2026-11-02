package com.example.enterpriseassistant.rag;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.core.Ordered;

/** Rewrites vague resume searches before RAG and reports safe retrieval diagnostics afterward. */
public class ResumeProfileRagAdvisor implements BaseAdvisor {
    private static final Logger log = LoggerFactory.getLogger(ResumeProfileRagAdvisor.class);

    private final ResumeProfileQueryRewriter queryRewriter;
    private final boolean logRetrieval;

    public ResumeProfileRagAdvisor(ResumeProfileQueryRewriter queryRewriter, boolean logRetrieval) {
        this.queryRewriter = queryRewriter;
        this.logRetrieval = logRetrieval;
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        String question = request.prompt().getUserMessage().getText();
        String rewritten = queryRewriter.rewrite(question);
        if (question.equals(rewritten)) {
            return request;
        }
        log.debug("Applied focused resume query rewrite");
        Prompt prompt = request.prompt().augmentUserMessage(ignored -> new UserMessage(rewritten));
        return request.mutate().prompt(prompt).build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        if (logRetrieval && log.isDebugEnabled()) {
            List<Document> documents = retrievedDocuments(response.context());
            RagRetrievalDiagnostics.Summary summary = RagRetrievalDiagnostics.summarize(documents);
            log.debug("RAG retrieval complete retrievedChunks={} resumeChunks={}",
                    summary.retrievedChunks(), summary.resumeChunks());
        }
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<Document> retrievedDocuments(Map<String, Object> context) {
        Object value = context.get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (value instanceof List<?> documents) {
            return documents.stream().filter(Document.class::isInstance).map(Document.class::cast).toList();
        }
        return List.of();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }
}
