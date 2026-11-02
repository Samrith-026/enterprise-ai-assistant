# Enterprise AI Assistant — Interview Guide

## A. 60-second project explanation

“I built an enterprise knowledge assistant as one Java 21 Spring Boot application. Users can paste approved company text or upload PDF/TXT files. Documents are split, embedded, and stored in PostgreSQL with pgvector for retrieval. The ChatClient uses retrieval, conversation memory, and tools. It can call a Java department-directory tool, plus an MCP tool discovered from a separate local demo server over Streamable HTTP. The MCP server returns synthetic product-status data, showing how an assistant can connect to another product without making a real vendor call. Directory results are cached in Redis, and completed exchanges are saved through Spring Data JPA. Tests verify the flow without real AI calls, and the documented production limits are clear.”

## B. Three-minute architecture walkthrough

**First minute — boundaries.** Start at `ChatController`: request records use Bean Validation. The controller delegates to `ChatService`, which owns the use case. It invokes one configured `ChatClient`, then saves a completed exchange through `ChatInteractionRepository`. The JPA entity is separate from the API response. `GlobalExceptionHandler` converts failures to consistent, sanitized errors.

**Second minute — AI engineering.** `AiConfiguration` loads a system prompt, attaches memory and RAG advisors, and registers the local directory tool plus `SyncMcpToolCallbackProvider`. The MCP client discovers `get_product_status` from a separate Node server using Streamable HTTP. `MessageWindowChatMemory` retains twenty messages per conversation in this JVM. `QuestionAnswerAdvisor` searches pgvector for relevant content. Spring AI 2.0 automatically registers the tool-calling advisor. The model can request either tool, receive the result, and generate a final response.

**Third minute — data and tradeoffs.** Document ingestion targets 500-token chunks with metadata, then `VectorStore.add` handles embeddings and storage. PostgreSQL holds vectors and audit rows with separate access abstractions. Redis caches only directory emails for ten minutes. The application runs locally while Compose runs dependencies. Tests use fake model responses and mocked storage to prove wiring, plus manual live checks for provider quality and actual infrastructure. Describe the limitations clearly: volatile memory, no authentication, no ACL filtering, no idempotency, and no claim of guaranteed factual correctness.

## C. Chat request flow

```text
POST /api/chat
 -> ChatController: validate ChatRequest
 -> ChatService: provide message and ChatMemory.CONVERSATION_ID
 -> ChatClient: system prompt, Java tool, and MCP-discovered tools
 -> MessageChatMemoryAdvisor: load this conversation's messages
 -> QuestionAnswerAdvisor: similarity search through VectorStore / pgvector
 -> automatic ToolCallingAdvisor: call OpenAI and execute requested Java tools
 -> OpenAI: final answer
 -> memory advisor: retain final conversation exchange
 -> ChatInteractionRepository: persist completed question/answer
 -> ChatResponse
```

The tool loop may involve multiple model calls. Retrieval also needs a query embedding. One HTTP request is not necessarily one provider request. A JPA save failure can occur after memory was updated; these systems are not one atomic transaction.

## D. Document ingestion flow

```text
Manual text: DocumentRequest -> validation -> Spring AI Document + metadata
PDF/TXT: multipart upload -> validation -> PDFBox/UTF-8 extraction -> Spring AI Document + metadata
Both: TokenTextSplitter -> chunks with shared documentId and unique chunk IDs
 -> VectorStore.add -> OpenAI EmbeddingModel -> PostgreSQL vector_store
 -> document response with documentId and chunkCount
```

Uploaded PDF/TXT bytes are processed in memory; raw files are not persisted. PDFBox extracts selectable text. Scanned/image-only PDFs require OCR, which is outside this MVP. The upload limit is 10 MB and extracted text is limited to 200,000 characters.

An embedding is a numeric representation of meaning. Similarity search finds nearby representations; it does not directly find a verified answer. The LLM receives the retrieved text, not just the vector. We use 1536 dimensions consistently for storage and embedding requests.

## E. Concepts in simple interview language

| Concept | How to explain it here |
|---|---|
| Dependency Injection | Spring supplies dependencies through constructors, so services do not construct infrastructure themselves. |
| Spring bean | An object managed by Spring, such as ChatService or the configured ChatClient. |
| `@Controller` | Marks an MVC controller, often used with views. |
| `@RestController` | A controller whose return values become response bodies; our DTOs become JSON. |
| `@Service` | Identifies a component that implements application behavior. |
| `@Repository` | Identifies persistence components and supports exception translation; Spring Data generates our repository implementation. |
| DTO | A request/response shape independent of database mappings. Java records make these concise. |
| JPA | A standard for mapping Java entities to relational data; Hibernate is the provider here. |
| `JpaRepository` | Supplies repository operations such as save and findById without handwritten SQL for each operation. |
| Bean Validation | Constraints such as `@NotBlank` and `@Size`, enforced on controller input using `@Valid`. |
| `@RestControllerAdvice` | Centralizes error responses across REST controllers. |
| `ChatClient` | Spring AI's fluent application-facing interface for model interactions. |
| Advisor | A component around the model call that can add context or process results. |
| `EmbeddingModel` | Converts text to a vector representation for similarity search. |
| `VectorStore` | An abstraction for storing and retrieving documents using embeddings. |
| pgvector | A PostgreSQL extension that stores vectors and provides distance operators/indexes. |
| Semantic search | Finds text with similar meaning, rather than requiring identical words. |
| RAG | Retrieves external knowledge and adds it to the model request before generation. |
| `ChatMemory` | An abstraction for recent conversational context; it is different from permanent audit history. |
| `MessageChatMemoryAdvisor` | Loads messages using the supplied conversation ID and maintains memory around calls. |
| `QuestionAnswerAdvisor` | Searches the vector store and augments the question with retrieved text. |
| `@Tool` | Exposes a Java method's schema so a model can request its execution. |
| Redis | An external in-memory data service used here for expiring directory cache entries. |
| `@Cacheable` | A Spring proxy checks the cache before invoking the method and stores eligible results. |
| Docker Compose | Declares and starts the local PostgreSQL/pgvector and Redis services together. |
| MCP | A standard protocol for an AI application to discover and call tools hosted by another process. Here, Spring AI calls a local read-only demo server over Streamable HTTP. |

## F. Design decisions and tradeoffs

- **Modular monolith:** easier to run, debug, and explain; no need for distributed-service complexity at this scale.
- **Layering:** thin controllers, orchestrating services, Spring Data persistence. DTOs protect the API from entity changes.
- **Simple RAG:** `QuestionAnswerAdvisor` provides a small, understandable pipeline. It lacks history-aware query rewriting, reranking, and evaluated retrieval tuning.
- **Resource prompts:** reviewable and maintainable. Prompts reduce guessing but cannot guarantee factuality or enforce data access.
- **Memory versus audit:** memory is fast and bounded per conversation; JPA records are durable. A complete audit log is not automatically the best model context.
- **Tool separation:** an annotation exposes capability; a service implements lookup and caching. Crossing that bean boundary allows the cache proxy to work.
- **String cache serialization:** Redis values are human-readable emails; no Java object deserialization is needed.
- **Local schema initialization:** convenient for a demo. Versioned migrations are needed for controlled production changes.
- **Synchronous ingestion:** easy to demonstrate, but longer documents and provider latency occupy the request thread. Background ingestion can be added later.
- **No broad transactions:** a remote call should not hold a database connection/transaction open. This means audit and memory failures need explicit operational handling.

## G. Likely interview questions

1. **Why Java for an AI project?** It integrates AI with familiar enterprise services, types, validation, transactions, and operations.
2. **Why ChatClient instead of an HTTP client?** It provides model integration, advisors, tools, and observability conventions through one abstraction.
3. **What makes this RAG?** Retrieved enterprise text is inserted into the generation request; documents are not fine-tuning the model.
4. **Does the LLM learn uploaded documents permanently?** This application stores them in pgvector and sends relevant text on requests; it does not train the model.
5. **Why chunk documents?** Smaller passages improve retrieval focus and fit context limits; splitting may lose boundary context.
6. **Why 500 tokens?** A reasonable initial compromise, not an optimized result. I would evaluate several sizes and overlaps against representative questions.
7. **What is top-K?** The maximum number of retrieved chunks; this project starts with four.
8. **What does the similarity threshold do?** It rejects weak matches. Too high loses useful context; too low adds irrelevant text.
9. **Why pgvector?** It keeps vector and relational data in familiar PostgreSQL while Spring AI hides store-specific access.
10. **Why HNSW?** It supports efficient approximate nearest-neighbor search with memory/build-time tradeoffs. Exact search is also viable for a tiny dataset.
11. **What happens with no relevant documents?** The prompt directs the model to acknowledge missing company facts while still allowing tools and user-history questions.
12. **How do you reduce hallucinations?** Ground company facts in retrieved context, use explicit uncertainty instructions, evaluate outputs, and add stronger checks for production.
13. **How is memory isolated?** Every call supplies its conversation ID; tests verify one ID's private detail is absent from another ID's prompt.
14. **Is that secure multi-user isolation?** No. Without authentication, another client could choose the same ID. Production needs server-validated ownership.
15. **What happens after restart?** JVM conversation memory is lost; PostgreSQL audit rows and ingested documents remain in Compose volumes.
16. **How does tool calling work?** The model requests a named tool with arguments, Spring invokes Java, and the result is returned for final generation.
17. **Can a model call any Java method?** No; the application registers specific tools. Registration is an explicit capability boundary.
18. **Why cache directory data instead of answers?** Directory results are deterministic; chat answers depend on changing context, history, and retrieved documents.
19. **Why use a separate directory service?** Calling another Spring bean crosses the caching proxy. Self-invocation could bypass `@Cacheable`.
20. **What is cached for an unknown department?** Nothing; `unless` excludes the unavailable-contact sentinel.
21. **How do you prove a cache hit?** Inspect the Redis key/TTL and compare tool-execution logs with directory cache-miss logs across distinct conversations.
22. **Why separate DTOs and entities?** They have different responsibilities and evolution paths; exposing entities risks leaking persistence details.
23. **Why constructor injection?** It makes required dependencies explicit, supports final fields, and simplifies unit tests.
24. **When do you persist chat?** After a nonempty completed AI response. Failed AI calls are not stored as completed interactions.
25. **What if the audit save fails?** The API returns a storage error; the model call and memory update may already have happened. Automatic whole-request retries are unsafe.
26. **How do you test without paying for AI?** Use mocked/fake model responses and storage, while retaining the real advisor chain for wiring tests.
27. **Do unit tests prove answer quality?** No. Quality needs live evaluation with known questions, expected facts, and retrieval metrics.
28. **What does Actuator health prove?** Application and configured dependency health; it does not validate OpenAI credentials or evaluate answers.
29. **Where are secrets stored?** The API key comes from the environment; `.env` is ignored. Production should inject secrets from a secret manager.
30. **Why not microservices?** This scope has no independent scaling or ownership need that justifies their operational cost.

## H. Five difficult follow-up questions

**1. How would you prevent document leakage between tenants?** Authenticate users, derive tenant identity server-side, attach tenant/ACL metadata during ingestion, and enforce retrieval filters before context reaches the model. Never trust a model prompt or client-supplied filter as access control. Test both allowed and denied retrieval paths.

**2. How would you make chat retries safe?** Introduce an idempotency key and request lifecycle state, serialize turns per conversation, and distinguish model completion from audit completion. Persist results so a repeated request returns the prior outcome rather than invoking the model again. External calls and databases still require careful failure reconciliation.

**3. Why might “What about carry-over?” retrieve the wrong chunks?** Basic semantic retrieval embeds that short question without rewriting it from conversation context. Add history-aware query rewriting, then evaluate against multi-turn questions. Memory alone does not guarantee retrieval query resolution.

**4. How would you evaluate and tune RAG?** Build a versioned synthetic/approved question set with expected supporting documents and facts. Measure retrieval recall@K, answer correctness, groundedness, refusals, latency, and cost. Compare chunk sizes, overlap, threshold, reranking, and model changes with held-out cases and human review.

**5. What happens when many users share this process?** Each conversation window is bounded but the number of IDs is not. Add retention/eviction and persistent shared memory, authenticated quotas, rate limiting, provider concurrency controls, and connection-pool tuning. Serialize same-conversation writes or use optimistic concurrency to avoid interleaved turns.

## I. Production changes

| Area | Required change |
|---|---|
| Authentication | Validate identity with an enterprise identity provider. |
| Authorization | Enforce conversation ownership and allowed tool capabilities server-side. |
| Persistent memory | Replace the isolated memory configuration with a shared durable implementation and retention policy. |
| Observability | Capture request IDs, timings, retrieval counts, token/cost metrics, and traces with redaction. |
| Rate limiting | Apply user/tenant quotas, ingestion limits, provider concurrency limits, and request-body limits. |
| Secret management | Inject managed credentials; rotate them and restrict network/access privileges. |
| Document access control | Filter by authorized source/ACL before retrieved text reaches the model. |
| Tenant isolation | Derive tenant identity from authentication and enforce it in storage, retrieval, caches, and memory. |
| PII protection | Minimize/redact content, encrypt data, define retention/deletion, and review provider data handling. |
| Evaluation | Run versioned regression datasets and human review before prompt/model/retrieval changes. |
| Monitoring | Alert on error rates, latency, cost, cache health, failed ingestion, and quality regressions. |
| Scaling | Share memory, control same-conversation concurrency, tune PostgreSQL/indexes, and consider asynchronous ingestion. |
| Data lifecycle | Add migrations, document versions, deduplication, deletion, re-embedding workflows, and backup/restore tests. |
| Reliability | Classify provider errors, apply bounded retries only where safe, and reconcile audit failures. |

Close by distinguishing implemented behavior from future improvements. Demonstrate the code path and test evidence rather than promising production guarantees.
