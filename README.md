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

## Setup (on your own laptop - free, no purchases needed)

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

### 5. Ask questions

Via the UI: open http://localhost:8080 in your browser.

Via curl:

```bash
curl -X POST http://localhost:8080/api/ask \
     -H "Content-Type: application/json" \
     -d '{"question": "What are the main risk factors mentioned?"}'
```


### 6. Scoping questions to one company

If you ingest filings from more than one company, an unscoped question
searches every chunk from every company - which means a generic question
like "what are the risk factors" can retrieve chunks from more than one
filing at once and blend them into a single answer. To avoid that, tag each
upload with a company and pass the same company when asking:

```bash
curl -F "file=@data/Apple_10K.html" -F "company=Apple" \
     http://localhost:8080/api/documents

curl -X POST http://localhost:8080/api/ask \
     -H "Content-Type: application/json" \
     -d '{"question": "What are the main risk factors?", "company": "Apple"}'
```

`GET /api/companies` lists every company name seen so far (used by the demo
UI to populate an autocomplete list), which helps avoid a scoped question
silently matching nothing because of a typo or case mismatch - the "company"
filter is an exact string match. Omitting "company" on either call preserves
the original, unscoped behavior.

---

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
## Tech stack

| Layer                  | Technology                                      | Purpose                                                          |
|-------------------------|--------------------------------------------------|-------------------------------------------------------------------|
| Language                | Java 17                                          | Core application language                                        |
| Framework                | Spring Boot 3.3.4                                | REST API, dependency injection, embedded Tomcat server            |
| LLM orchestration        | LangChain4j 0.35.0                               | Document splitting, embeddings, vector search, prompt construction |
| LLM runtime              | [Ollama](https://ollama.com)                     | Runs the models locally - no cloud, no API key, no per-token cost |
| Chat / generation model  | `llama3.2` (via Ollama)                          | Generates the grounded natural-language answer                   |
| Embedding model          | `nomic-embed-text` (via Ollama)                  | Turns text chunks and questions into vectors for similarity search |
| Vector storage           | LangChain4j `InMemoryEmbeddingStore`             | Holds chunk embeddings in memory, persisted to a local JSON file  |
| HTML parsing             | Jsoup 1.18.1                                     | Strips HTML/markup noise out of upload documents before chunking |
| Build tool               | Maven                                            | Dependency management and build lifecycle                        |
| Testing                  | JUnit 5 + Spring Boot Test                       | Automated integration tests against a live Ollama instance        |
