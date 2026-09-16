package com.financerag.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks the distinct company names that have been tagged onto ingested
 * chunks so far, so a UI can offer a "which company is this about"
 */
@Component
public class CompanyRegistry {

    private static final Logger log = LoggerFactory.getLogger(CompanyRegistry.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path registryPath;
    private final Set<String> companies;

    public CompanyRegistry(@Value("${finance-rag.vector-store.companies-path}") String companiesPath) {
        this.registryPath = Path.of(companiesPath);
        this.companies = Collections.synchronizedSet(new LinkedHashSet<>(load()));
    }

    /**
     * Records a company name as "known" (idempotent - calling this repeatedly
     * with the same name, e.g. once per chunk during ingestion, only persists
     * once). No-op for null/blank input.
     */
    public void register(String companyName) {
        if (companyName == null || companyName.isBlank()) {
            return;
        }
        String trimmed = companyName.trim();
        boolean isNew;
        synchronized (companies) {
            isNew = companies.add(trimmed);
        }
        if (isNew) {
            persist();
        }
    }

    /** All companies seen so far, in first-seen order. */
    public List<String> list() {
        synchronized (companies) {
            return new ArrayList<>(companies);
        }
    }

    private List<String> load() {
        try {
            if (Files.exists(registryPath)) {
                return MAPPER.readValue(registryPath.toFile(), new TypeReference<List<String>>() {
                });
            }
        } catch (IOException e) {
            log.warn("Failed to load company registry from {}: {}", registryPath, e.getMessage());
        }
        return List.of();
    }

    private void persist() {
        try {
            if (registryPath.getParent() != null) {
                Files.createDirectories(registryPath.getParent());
            }
            synchronized (companies) {
                MAPPER.writeValue(registryPath.toFile(), new ArrayList<>(companies));
            }
        } catch (IOException e) {
            log.warn("Failed to persist company registry to {}: {}", registryPath, e.getMessage());
        }
    }
}
