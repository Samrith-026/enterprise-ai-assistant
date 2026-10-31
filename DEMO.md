# Five-minute interview demo

## Browser alternative

Open http://127.0.0.1:8080/ after application startup. Use Company knowledge to add the synthetic handbook text, then use chat for the policy, memory, and contact questions below. New conversation assigns a fresh memory ID; you can also enter a chosen ID. The command-line flow remains useful for showing Redis and database evidence. The interface is static HTML/CSS/JavaScript served by Spring Boot, with no separate frontend server.

## Preparation before the interview

Use Java 21 and Docker Desktop. Follow README environment setup, supply your own `OPENAI_API_KEY`, and ensure your account can access the configured chat/embedding models. Paid provider calls occur during document ingestion and every chat turn. Export database values if you changed `.env`; the local JVM does not load it automatically.

Run from the repository root in PowerShell 7. `curl.exe` avoids Windows PowerShell's historical `curl` alias. Bash users can substitute `curl`. Start the application in one terminal and run the demo in a second:

```powershell
docker compose config --quiet
docker compose up -d --wait
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

Keep application logs visible. Preflight the full demo before presenting; model outputs and latency vary. Run the SQL once beforehand to establish the current row count. For repeat demos, restart the JVM to clear memory or change conversation IDs. Repeated ingestion adds document copies. To force an HR cache miss without deleting unrelated Redis keys:

```powershell
docker compose exec -T redis redis-cli DEL departmentContacts::HR
```

## 0:00-0:30 — Health and architecture

Say: “One Spring Boot application connects to PostgreSQL with pgvector and Redis. The controller validates input; services coordinate the AI and persistence.”

```powershell
curl.exe -sS http://localhost:8080/actuator/health
```

Expected: `{"status":"UP"}`. Database and Redis health contribute; this endpoint does not verify OpenAI credentials or model availability.

## 0:30-1:15 — Ingest knowledge

```powershell
curl.exe -sS -X POST http://localhost:8080/api/documents -H 'Content-Type: application/json' --data-binary '@demo/employee-handbook.json'
```

Expect `documentId` and a positive `chunkCount`. This short handbook can produce one chunk; longer documents are split. Point to logs showing chunk count. Explain: “The vector store creates OpenAI embeddings and writes content, metadata, and vectors.”

```powershell
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT count(*) AS chunks, min(vector_dims(embedding)) AS dimensions FROM vector_store;"'
```

Expected dimension: 1536.

## 1:15-2:00 — RAG

```powershell
curl.exe -sS -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' --data-binary '@demo/rag-question.json'
```

Expected facts: 20 paid vacation days; up to 5 carried over, used by March 31. Explain semantic search, top-K retrieval, and retrieved context added by `QuestionAnswerAdvisor`. If the answer is wrong or unavailable, inspect ingestion and retrieval settings; do not claim quality from a single example.

## 2:00-2:45 — Memory isolation

```powershell
curl.exe -sS -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' --data-binary '@demo/memory-intro.json'
curl.exe -sS -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' --data-binary '@demo/memory-followup.json'
curl.exe -sS -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' --data-binary '@demo/memory-isolated.json'
```

The same conversation should recall Lantern. The isolated conversation should say it does not know. Explain that this history lives in JVM memory, not in the model or automatically in JPA.

## 2:45-3:45 — Tool calling and Redis

```powershell
curl.exe -sS -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' --data-binary '@demo/tool-first.json'
docker compose exec -T redis redis-cli GET departmentContacts::HR
docker compose exec -T redis redis-cli TTL departmentContacts::HR
curl.exe -sS -X POST http://localhost:8080/api/chat -H 'Content-Type: application/json' --data-binary '@demo/tool-second.json'
```

Expect `hr@company.example` and a positive TTL no greater than 600 seconds. Logs should show two tool executions but only one directory cache miss, assuming the model requested the tool both times. Distinct conversation IDs prevent history from replacing the lookup. If no tool log appears, the model did not request a tool; do not treat the answer alone as proof of execution. The deterministic offline tool-flow test provides an independent wiring check.

Say: “The model selects the function; Spring executes the Java method and sends the result back. Only directory strings are cached; conversational answers are generated every turn.”

## 3:45-4:30 — JPA audit history

```powershell
docker compose exec -T postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT conversation_id, left(question, 65) AS question, left(answer, 80) AS answer, created_at FROM chat_interaction ORDER BY created_at DESC LIMIT 8;"'
```

Explain `ChatInteractionRepository extends JpaRepository<ChatInteraction, UUID>`. Audit storage is durable but is not the memory implementation. No provider call runs within a long JPA transaction.

## 4:30-5:00 — Tests and tradeoffs

Show the test result from preflight and `AiFlowTest`. Describe the local MVP limits: no authentication, client-selected IDs, volatile memory, no document ACLs, no retrieval benchmark. Production needs persistent memory, ownership checks, migrations, retention, and evaluation.

Stop infrastructure after the demo when no longer needed:

```powershell
docker compose down
```

Do not use `down -v` unless you intend to delete local database and cache volumes.
