package com.example.enterpriseassistant.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import com.example.enterpriseassistant.dto.DocumentRequest;
import com.example.enterpriseassistant.dto.DocumentResponse;
import com.example.enterpriseassistant.exception.DocumentIngestionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

@Service
public class DocumentIngestionService {
    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);
    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public DocumentIngestionService(VectorStore vectorStore, TokenTextSplitter splitter) {
        this.vectorStore = vectorStore;
        this.splitter = splitter;
    }

    public DocumentResponse ingest(DocumentRequest request) {
        return ingest(request.title(), request.content(), "json-api", null, null);
    }

    public DocumentResponse ingestUploaded(String title, String content, String filename, String fileType) {
        return ingest(title, content, "file-upload", filename, fileType);
    }

    private DocumentResponse ingest(String title, String content, String source, String filename, String fileType) {
        UUID documentId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        log.info("Document ingestion started documentId={}", documentId);
        try {
            var metadata = new HashMap<String, Object>();
            metadata.put("documentId", documentId.toString());
            metadata.put("title", title);
            metadata.put("source", source);
            metadata.put("ingestedAt", timestamp.toString());
            if (filename != null) {
                metadata.put("sourceFilename", filename);
                metadata.put("fileType", fileType);
            }
            List<Document> split = splitter.apply(List.of(new Document(content, metadata)));
            if (split.isEmpty()) {
                throw new IllegalArgumentException("Document produced no chunks");
            }
            List<Document> chunks = new ArrayList<>();
            for (int index = 0; index < split.size(); index++) {
                var chunkMetadata = new HashMap<>(metadata);
                chunkMetadata.put("chunkIndex", index);
                chunks.add(new Document(split.get(index).getText(), chunkMetadata));
            }
            log.info("Embedding and storing documentId={} chunkCount={}", documentId, chunks.size());
            vectorStore.add(chunks);
            return new DocumentResponse(documentId, title, chunks.size(), timestamp);
        } catch (RuntimeException ex) {
            throw new DocumentIngestionException(ex);
        }
    }
}
