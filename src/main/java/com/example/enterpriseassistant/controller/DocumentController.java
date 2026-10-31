package com.example.enterpriseassistant.controller;

import com.example.enterpriseassistant.dto.DocumentRequest;
import com.example.enterpriseassistant.dto.DocumentResponse;
import com.example.enterpriseassistant.service.DocumentIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentIngestionService ingestionService;

    public DocumentController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse ingest(@Valid @RequestBody DocumentRequest request) {
        return ingestionService.ingest(request);
    }
}
