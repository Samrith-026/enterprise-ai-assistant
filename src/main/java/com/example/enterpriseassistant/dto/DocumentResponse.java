package com.example.enterpriseassistant.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(UUID documentId, String title, int chunkCount, Instant timestamp) {
}
