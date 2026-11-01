package com.example.enterpriseassistant.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentUploadResponse(UUID documentId, String filename, String fileType,
        int chunkCount, Instant timestamp) {
}
