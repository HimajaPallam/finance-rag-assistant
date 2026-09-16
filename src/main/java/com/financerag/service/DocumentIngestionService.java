package com.financerag.service;

import com.financerag.config.RagConfig;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns a raw filing (plain text or HTML, as downloaded from SEC EDGAR) into
 * searchable vector chunks.
 *
 * Pipeline: raw bytes -> strip HTML/markup (if any) -> split into overlapping
 * chunks -> embed each chunk -> store (vector, chunk text + metadata).
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    public static final String UNSPECIFIED_COMPANY = "Unspecified";
    private final DocumentSplitter splitter;
    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final RagConfig.VectorStorePersister persister;
    private final CompanyRegistry companyRegistry;

    public DocumentIngestionService(DocumentSplitter splitter,
                                     EmbeddingModel embeddingModel,
                                     EmbeddingStore<TextSegment> embeddingStore,
                                     RagConfig.VectorStorePersister persister,
                                    CompanyRegistry companyRegistry) {
        this.splitter = splitter;
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.persister = persister;
        this.companyRegistry = companyRegistry;
    }

    /**
     * Ingests one document's raw bytes.
     *
     * @param documentName human-readable name shown later in citations, e.g. "apple_10k_2024.txt"
     * @param companyName  which company this filing is about, e.g. "Apple". Optional -
     *                     falls back to {@link #UNSPECIFIED_COMPANY} if null/blank, so
     *                     existing callers that don't pass a company keep working exactly
     *                     as before (their chunks are just all tagged the same way, and an
     *                     un-scoped question still searches everything as it always did).
     * @param rawBytes     the file content
     * @return number of chunks stored
     */
    public int ingest(String documentName, String companyName, byte[] rawBytes) {
        String company = normalizeCompany(companyName);
        String rawText = new String(rawBytes, StandardCharsets.UTF_8);
        String cleanText = looksLikeHtml(rawText) ? stripHtml(rawText) : rawText;

        Document document = Document.from(cleanText);
        List<TextSegment> rawSegments = splitter.split(document);

        // Rebuild each segment with explicit metadata (documentName + a stable
        // chunk index) so we can cite "which document, which chunk" in the final result
        List<TextSegment> segments = new ArrayList<>(rawSegments.size());
        for (int i = 0; i < rawSegments.size(); i++) {
            Metadata metadata = Metadata.from(Map.of(
                    "documentName", documentName,       // can be customerId in future
                    "chunkIndex", String.valueOf(i),
                    "companyName", company
            ));
            segments.add(TextSegment.from(rawSegments.get(i).text(), metadata));
        }

        Response<List<Embedding>> embeddings = embeddingModel.embedAll(segments);
        embeddingStore.addAll(embeddings.content(), segments);


        persister.persist();
        companyRegistry.register(company);

        log.info("Ingested '{}' (company='{}') as {} chunks", documentName, company, segments.size());
        return segments.size();
    }

    private String normalizeCompany(String companyName) {
        return (companyName == null || companyName.isBlank()) ? UNSPECIFIED_COMPANY : companyName.trim();
    }

    private boolean looksLikeHtml(String text) {
        String sample = text.stripLeading();
        return sample.length() > 0 && (sample.startsWith("<") || text.toLowerCase().contains("<html"));
    }

    private String stripHtml(String html) {
        return Jsoup.parse(html).text();
    }
}
