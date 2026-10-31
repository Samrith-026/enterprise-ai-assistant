package com.example.enterpriseassistant.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "chat_interaction", indexes = @Index(name = "idx_chat_conversation_created",
        columnList = "conversation_id, created_at"))
public class ChatInteraction {
    @Id
    private UUID id;
    @Column(name = "conversation_id", nullable = false, length = 100)
    private String conversationId;
    @Column(nullable = false, columnDefinition = "text")
    private String question;
    @Column(nullable = false, columnDefinition = "text")
    private String answer;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatInteraction() {
    }

    public ChatInteraction(String conversationId, String question, String answer, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.question = question;
        this.answer = answer;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getConversationId() { return conversationId; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public Instant getCreatedAt() { return createdAt; }
}
