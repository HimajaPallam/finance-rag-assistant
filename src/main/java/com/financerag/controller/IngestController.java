package com.financerag.controller;

import com.financerag.service.CompanyRegistry;
import com.financerag.service.DocumentIngestionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
public class IngestController {

    private final DocumentIngestionService ingestionService;
    private final CompanyRegistry companyRegistry;

    public IngestController(DocumentIngestionService ingestionService, CompanyRegistry companyRegistry) {
        this.ingestionService = ingestionService;
        this.companyRegistry = companyRegistry;
    }

    /**
     * Upload a filing (plain .txt or .html) to be chunked, embedded, and
     * added to the searchable index.
     *
     */
    @PostMapping("/api/documents")
    public ResponseEntity<?> uploadDocument(@RequestParam("file") MultipartFile file,
                                             @RequestParam(value = "company", required = false) String company)
            throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "file is empty"));
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed-document";
        int chunks = ingestionService.ingest(name, company, file.getBytes());
        String storedCompany = (company == null || company.isBlank())
                ? DocumentIngestionService.UNSPECIFIED_COMPANY
                : company.trim();
        return ResponseEntity.ok(Map.of(
                "documentName", name,
                "company", storedCompany,
                "chunksIndexed", chunks
        ));
    }

    /**
     * Lists every distinct company name seen across all ingested documents so
     * far, so a UI can offer a dropdown for the "company" filter instead of
     * requiring free-text that has to match exactly.
     */
    @GetMapping("/api/companies")
    public ResponseEntity<List<String>> listCompanies() {
        return ResponseEntity.ok(companyRegistry.list());
    }
}
