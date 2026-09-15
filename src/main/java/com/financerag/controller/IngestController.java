package com.financerag.controller;

import com.financerag.service.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
public class IngestController {

    private final DocumentIngestionService ingestionService;

    public IngestController(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Upload a filing (plain .txt or .html) to be chunked, embedded, and
     * added to the searchable index.
     *
     */
    @PostMapping("/api/documents")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Input file is empty"));
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed-document";

        int chunks = ingestionService.ingest(name, file.getBytes());

        return ResponseEntity.ok(Map.of(
                "documentName", name,   // can be company name in future
                "chunksIndexed", chunks
        ));
        // whats the use of this response structure?
    }
}
