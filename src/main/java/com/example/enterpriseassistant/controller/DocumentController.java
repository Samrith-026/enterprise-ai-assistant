package com.example.enterpriseassistant.controller;

import com.example.enterpriseassistant.dto.DocumentRequest;
import com.example.enterpriseassistant.dto.DocumentResponse;
import com.example.enterpriseassistant.dto.DocumentUploadResponse;
import com.example.enterpriseassistant.service.DocumentFileIngestionService;
import com.example.enterpriseassistant.service.DocumentIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentIngestionService ingestionService;
    private final DocumentFileIngestionService fileIngestionService;

    public DocumentController(DocumentIngestionService ingestionService,
            DocumentFileIngestionService fileIngestionService) {
        this.ingestionService = ingestionService;
        this.fileIngestionService = fileIngestionService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse ingest(@Valid @RequestBody DocumentRequest request) {
        return ingestionService.ingest(request);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentUploadResponse upload(@RequestPart("file") MultipartFile file) {
        return fileIngestionService.ingest(file);
    }
}
