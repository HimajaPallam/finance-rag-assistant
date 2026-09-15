package com.financerag.service;

import com.financerag.model.SourceChunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * The actual "RAG" step: given a question,
 *   1. embed the question with the same embedding model used at ingestion time
 *   2. retrieve the top-K most similar chunks from the vector store
 *   3. stuff those chunks into a prompt that instructs the model to answer
 *      ONLY from the provided context, and to say so if the answer isn't there
 *   4. call the local LLM and return the answer plus the chunks it was based on
 *
 * Grounding the prompt this way (instead of just asking the base model) is
 * what keeps answers tied to the actual filing instead of the model's own
 * (possibly outdated, possibly hallucinated) training knowledge.
 */
@Service
public class RagQueryService {

    private static final Logger log = LoggerFactory.getLogger(RagQueryService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatLanguageModel chatLanguageModel;
    private final int topK;
    private final double minScore;

    public RagQueryService(EmbeddingModel embeddingModel,
                            EmbeddingStore<TextSegment> embeddingStore,
                            ChatLanguageModel chatLanguageModel,
                            @Value("${finance-rag.retrieval.top-k}") int topK,
                            @Value("${finance-rag.retrieval.min-score}") double minScore) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.chatLanguageModel = chatLanguageModel;
        this.topK = topK;
        this.minScore = minScore;
    }

    public record AnswerWithSources(String answer, List<SourceChunk> sources) {
    }

    public AnswerWithSources answer(String question) {
        Response<Embedding> questionEmbedding = embeddingModel.embed(question);

        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(questionEmbedding.content())
                .maxResults(topK)
                .minScore(minScore)
                .build();

        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

        if (matches.isEmpty()) {
            log.info("No chunks above minScore={} for question: {}", minScore, question);
            return new AnswerWithSources(
                    "I couldn't find anything relevant in the ingested documents to answer that. "
                            + "Try rephrasing, or ingest a document that covers this topic.",
                    List.of());
        }

        String context = matches.stream()
                .map(m -> "[" + m.embedded().metadata().getString("documentName")
                        + " #" + m.embedded().metadata().getString("chunkIndex") + "]\n"
                        + m.embedded().text())
                .collect(Collectors.joining("\n\n---\n\n"));

        String prompt = buildPrompt(question, context);
        String rawAnswer = chatLanguageModel.generate(prompt);

        List<SourceChunk> sources = matches.stream()
                .map(m -> new SourceChunk(
                        m.embedded().metadata().getString("documentName"),
                        Integer.parseInt(m.embedded().metadata().getString("chunkIndex")),
                        m.score(),
                        m.embedded().text()))
                .collect(Collectors.toList());

        return new AnswerWithSources(rawAnswer.trim(), sources);
    }

    private String buildPrompt(String question, String context) {
        return """
                You are a financial research assistant. Answer the question using ONLY the
                context below, which is extracted from company financial filings. Do not use
                any outside knowledge. If the context does not contain enough information to
                answer, say so explicitly instead of guessing.

                When you use a fact from the context, mention which source it came from
                (e.g. "according to [document #chunk]").

                Context:
                %s

                Question: %s

                Answer:
                """.formatted(context, question);
    }
}
