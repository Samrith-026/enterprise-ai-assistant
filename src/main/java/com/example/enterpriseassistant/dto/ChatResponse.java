package com.example.enterpriseassistant.dto;

import java.time.Instant;

public record ChatResponse(String conversationId, String answer, Instant timestamp) {
}
