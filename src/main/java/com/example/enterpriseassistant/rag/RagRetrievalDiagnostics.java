package com.example.enterpriseassistant.rag;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.ai.document.Document;

/** Summarizes retrieval without exposing document text or personal filenames in logs. */
public final class RagRetrievalDiagnostics {
    private static final Pattern RESUME_MARKER = Pattern.compile("(?<![a-z])(?:resume|résumé|cv|curriculum\\s+vitae)(?![a-z])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private RagRetrievalDiagnostics() {
    }

    public static Summary summarize(List<Document> documents) {
        int resumeChunks = (int) documents.stream().filter(RagRetrievalDiagnostics::isResumeChunk).count();
        return new Summary(documents.size(), resumeChunks);
    }

    private static boolean isResumeChunk(Document document) {
        var metadata = document.getMetadata();
        String identifiers = List.of("title", "sourceFilename").stream()
                .map(metadata::get)
                .filter(Objects::nonNull)
                .map(value -> value.toString().toLowerCase(Locale.ROOT))
                .reduce("", (left, right) -> left + " " + right);
        return RESUME_MARKER.matcher(identifiers).find();
    }

    public record Summary(int retrievedChunks, int resumeChunks) {
    }
}
