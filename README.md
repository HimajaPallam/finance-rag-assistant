# Finance RAG Assistant

A Retrieval-Augmented Generation (RAG) Q&A assistant over financial filings
(10-K / 10-Q annual and quarterly reports), built in Java with Spring Boot and
LangChain4j. Ask a question in plain English, get an answer grounded in the
actual filing text, with citations back to the exact chunk it came from.

The LLM and embedding model both run through [Ollama] on oen machine.
---

## Architecture

```
                 ┌─────────────────────┐
  10-K / 10-Q -> │ DocumentIngestion    │  strip HTML (Jsoup) -> split into
  (.txt/.html)   │ Service              │  overlapping chunks (DocumentSplitters)
                 └──────────┬───────────┘
                            │ embed each chunk (Ollama: nomic-embed-text)
                            v
                 ┌─────────────────────┐
                 │ EmbeddingStore       │  in-memory vector index,
                 │ (persisted to JSON)  │  persisted to data/vector-store.json
                 └──────────┬───────────┘
                            │
  question ->  embed question -> cosine-similarity search (top-K, min-score)
                            │
                            v
                 ┌─────────────────────┐
                 │ RagQueryService      │  build grounded prompt from
                 │                      │  retrieved chunks
                 └──────────┬───────────┘
                            │ generate (Ollama: llama3.2)
                            v
                     answer + cited sources
```

Key classes:
- `config/RagConfig.java` - wires Ollama chat/embedding models, the document
  splitter, and the persisted in-memory vector store.
- `service/DocumentIngestionService.java` - the ingestion pipeline.
- `service/RagQueryService.java` - the retrieval + prompt-construction +
  generation pipeline (the "RAG" part).
- `controller/IngestController.java`, `controller/QueryController.java` - the
  two REST endpoints (`POST /api/documents`, `POST /api/ask`).
- `src/main/resources/static/index.html` - a minimal one-page UI so you have
  something to actually demo, not just curl commands.

---

## Setup

### 1. Install Ollama

### 2. Pull the two models this project uses

```bash
ollama pull llama3.2           # ~2GB - generation model
ollama pull nomic-embed-text   # ~270MB - embedding model
```

Ollama starts its own local server automatically the first time you run a model.

### 3. Build and run the project

```bash
mvn spring-boot:run
```

### 4. Ingest a document

Start with the bundled synthetic sample:

```bash
curl -F "file=@data/sample_filing.txt" http://localhost:8080/api/documents
```

Once that works end-to-end, pull a **real** filing from SEC EDGAR (free,
no API key, just requires identifying yourself per SEC's fair-access policy):

```bash
export SEC_USER_AGENT="Your Name your.email@example.com"
./scripts/fetch_sec_filing.sh 0000320193   # Apple Inc.
```

### 5. Ask questions

Via the UI: open http://localhost:8080 in your browser.

Via curl:

```bash
curl -X POST http://localhost:8080/api/ask \
     -H "Content-Type: application/json" \
     -d '{"question": "What are the main risk factors mentioned?"}'
```

## Project structure

```
finance-rag-assistant/
├── pom.xml
├── README.md
├── data/
│   └── sample_filing.txt        # synthetic filing for offline smoke testing
└── src/main/
    ├── java/com/financerag/
    │   ├── FinanceRagApplication.java
    │   ├── config/RagConfig.java
    │   ├── service/DocumentIngestionService.java
    │   ├── service/RagQueryService.java
    │   ├── controller/IngestController.java
    │   ├── controller/QueryController.java
    │   └── model/ (AskRequest, AskResponse, SourceChunk)
    └── resources/
        ├── application.yml
        └── static/index.html    # minimal demo UI
```
