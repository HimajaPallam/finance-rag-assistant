package com.financerag.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Wires up all the RAG building blocks:
 *
 *   chat model      -> Ollama, generates the final answer
 *   embedding model -> Ollama, turns text into vectors
 *   embedding store -> in-memory vector index, persisted to a JSON file on disk
 *                       so ingested documents survive an app restart without
 *                       needing a separate database/service
 *   document splitter -> chunks long documents into overlapping segments
 *
 */
@Configuration
public class RagConfig {

    private static final Logger log = LoggerFactory.getLogger(RagConfig.class);

    @Value("${finance-rag.ollama.base-url}")
    private String ollamaBaseUrl;

    @Value("${finance-rag.ollama.chat-model}")
    private String chatModelName;

    @Value("${finance-rag.ollama.embedding-model}")
    private String embeddingModelName;

    @Value("${finance-rag.ingestion.chunk-size}")
    private int chunkSize;

    @Value("${finance-rag.ingestion.chunk-overlap}")
    private int chunkOverlap;

    @Value("${finance-rag.vector-store.persist-path}")
    private String vectorStorePersistPath;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(chatModelName)
                .temperature(0.1) // low temperature: we want grounded, repeatable answers, not creativity
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel() {
        return OllamaEmbeddingModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(embeddingModelName)
                .build();
    }

    @Bean
    public DocumentSplitter documentSplitter() {
        return DocumentSplitters.recursive(chunkSize, chunkOverlap);
    }

    /**
     * Loads the vector store from disk if a previous run persisted one,
     * otherwise starts empty. Call persistEmbeddingStore()
     * (shutdown hook) to save it back out.
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        File file = new File(vectorStorePersistPath);
        if (file.exists()) {
            log.info("Loading existing vector store from {}", file.getAbsolutePath());
            return InMemoryEmbeddingStore.fromFile(file.toPath());
        }
        log.info("No existing vector store found at {} - starting empty", file.getAbsolutePath());
        return new InMemoryEmbeddingStore<>();
    }

    private EmbeddingStore<TextSegment> storeRef;

    @Bean
    public VectorStorePersister vectorStorePersister(EmbeddingStore<TextSegment> embeddingStore) {
        this.storeRef = embeddingStore;
        return new VectorStorePersister(embeddingStore, Path.of(vectorStorePersistPath));
    }

    /**
     * Small helper bean whose only job is to save the in-memory vector store
     * back to disk, both periodically-triggered (after each ingestion) and on
     * JVM shutdown, so nothing is lost between restarts.
     */
    public static class VectorStorePersister {
        private static final Logger log = LoggerFactory.getLogger(VectorStorePersister.class);
        private final EmbeddingStore<TextSegment> store;
        private final Path path;

        public VectorStorePersister(EmbeddingStore<TextSegment> store, Path path) {
            this.store = store;
            this.path = path;
        }

        public synchronized void persist() {
            try {
                Files.createDirectories(path.getParent());
                if (store instanceof InMemoryEmbeddingStore<TextSegment> inMemory) {
                    inMemory.serializeToFile(path);
                    log.info("Vector store persisted to {}", path.toAbsolutePath());
                }
            } catch (Exception e) {
                log.warn("Failed to persist vector store to {}: {}", path, e.getMessage());
            }
        }

        @PreDestroy
        public void onShutdown() {
            persist();
        }
    }
}
