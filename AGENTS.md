# Repository Guidelines

## Project Structure & Module Organization

This Java 21 Maven application uses `src/main/java/com/example/enterpriseassistant/` for application code and `src/test/java/` for tests. Packages separate controllers, services, DTOs, entities, repositories, configuration, tools, and exceptions. Configuration and prompts live in `src/main/resources/`; `static/` contains the buildless browser interface. Demo JSON payloads live in `demo/`; root documentation includes README, INTERVIEW_GUIDE, and DEMO.

## Build, Test, and Development Commands

Use Java 21 and the Maven wrapper: `./mvnw compile`, `./mvnw test`, and `./mvnw package` (PowerShell: `.\mvnw.cmd`). Start dependencies with `docker compose up -d --wait`, then run `./mvnw spring-boot:run`. Validate Compose with `docker compose config --quiet`. Export `OPENAI_API_KEY` for runtime; normal tests require no key or running containers.

## Coding Style & Naming Conventions

Use four-space Java indentation, PascalCase classes, camelCase methods/fields, and constructor injection with final dependency fields. Prefer records for DTOs; keep JPA entities separate. No Lombok or extra service interfaces without a concrete need. No formatter or linter is configured. Pin Spring Boot 4.1.2 and Spring AI 2.0.2; check current APIs before changing AI configuration.

## Testing Guidelines

Use JUnit Jupiter, Mockito, and MockMvc. Name test classes `*Test` and methods after observable behavior. Run one class with `./mvnw -Dtest=AiFlowTest test`. Verify isolation, tool execution, retrieval context, persistence, validation, and failure paths without live OpenAI calls. No coverage threshold is configured; actual infrastructure and provider checks are documented in DEMO.md.

## Commit & Pull Request Guidelines

No historical commit convention exists. Use concise imperative subjects, such as `Add configuration validation`. Keep commits focused. Pull requests should explain behavior, link relevant issues, list validation, and disclose checks that could not run.

## Security & Configuration

Never commit credentials, `.env`, build artifacts, or private enterprise data. Keep `.env.example` placeholders. Do not cache conversational responses or log raw enterprise content. Keep document and tool permissions explicit when extending the demo.
