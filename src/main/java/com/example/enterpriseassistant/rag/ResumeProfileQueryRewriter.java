package com.example.enterpriseassistant.rag;

import java.util.Locale;

/** Expands a small set of vague resume questions into focused vector-search phrases. */
public class ResumeProfileQueryRewriter {
    private static final String PROFILE_FOCUS = "Resume profile details: the candidate's professional summary, "
            + "work experience, technical skills, education, certifications, projects, and accomplishments explicitly "
            + "stated in the resume.";
    private static final String PROJECT_TOOLS_FOCUS = "Resume project details for Enterprise AI Assistant: retrieve "
            + "the programming language, frameworks, libraries, AI components, databases, vector store, cache, "
            + "developer tools, and infrastructure explicitly stated as used in this project.";

    public String rewrite(String question) {
        String normalized = normalize(question);
        if (isEnterpriseAssistantToolsQuestion(normalized)) {
            return question + "\n" + PROJECT_TOOLS_FOCUS;
        }
        if (isVagueProfileQuestion(normalized)) {
            return question + "\n" + PROFILE_FOCUS;
        }
        return question;
    }

    private boolean isEnterpriseAssistantToolsQuestion(String question) {
        return question.contains("enterprise ai assistant")
                && (question.contains("tool") || question.contains("technology")
                        || question.contains("tech stack") || question.contains("framework")
                        || question.contains("library"));
    }

    private boolean isVagueProfileQuestion(String question) {
        return question.matches(".*\\b(tell me about myself|tell me about me|describe me|who am i|"
                + "what do you know about me|summarize my background|summarize my profile|"
                + "tell me about my background)\\b.*");
    }

    private String normalize(String question) {
        return question.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
