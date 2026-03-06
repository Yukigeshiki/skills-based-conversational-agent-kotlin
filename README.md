# skills-based-conversational-agent-kotlin

A conversational agent that uses programmable skills - built with Spring Boot, Claude Sonnet 4.6 via LangChain4j Anthropic integration, and pgvector

## Overview

User messages are embedded using AllMiniLmL6V2 (in-process ONNX) and matched against skill descriptions stored in PostgreSQL with pgvector. The best-matching skill's system prompt and tool subset are used to dynamically build a LangChain4j AI Service that calls Claude.

## Tech Stack

- Kotlin 2.1.20, Java 21, Spring Boot 3.5.7
- LangChain4j with Anthropic (Claude)
- PostgreSQL 17 with pgvector for skill embeddings
- AllMiniLmL6V2 embedding model (384 dimensions, local ONNX)
- Gradle 8.14.1 with Kotlin DSL

## Request Flow

```
POST /api/chat { "message": "What time is it in Tokyo?" }
  -> SkillRouterService: embed message, similarity search pgvector
  -> DynamicAgentService: build AiService with skill's prompt + tools
  -> Claude responds, ChatResponse includes skill name
```

## Prerequisites

- Java 21+
- Docker (for PostgreSQL with pgvector)
- `ANTHROPIC_API_KEY` environment variable

## Getting Started

```bash
# Start PostgreSQL with pgvector
docker compose up -d

# Run the service
./gradlew bootRun
```

The app seeds two skills on first startup: `datetime-assistant` and `general-assistant`.

## API Endpoints

### Chat

```bash
curl -X POST http://localhost:9090/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message": "What time is it in Tokyo?"}'
```

Response includes which skill handled the request:

```json
{ "response": "...", "skill": "datetime-assistant" }
```

### Skills CRUD

```bash
# List all skills
curl http://localhost:9090/api/skills

# Create a skill
curl -X POST http://localhost:9090/api/skills \
  -H 'Content-Type: application/json' \
  -d '{"name": "my-skill", "description": "...", "systemPrompt": "...", "toolNames": ["DateTimeTool"]}'

# Update a skill
curl -X PUT http://localhost:9090/api/skills/{id} \
  -H 'Content-Type: application/json' \
  -d '{"name": "my-skill", "description": "...", "systemPrompt": "...", "toolNames": ["DateTimeTool"]}'
```

### Other

- Swagger UI: http://localhost:9090/swagger-ui.html
- Health check: http://localhost:9090/actuator/health

## Project Structure

```
app/src/main/kotlin/io/robothouse/agent/
  ChatAgent.kt              # LangChain4j proxy interface
  config/                    # Spring config, embedding, routing properties
  controller/                # Chat and Skill REST controllers
  model/                     # JPA entities and DTOs
  repository/                # SkillRepository (JPA), ToolRepository (bean scanner)
  service/                   # SkillRouterService, DynamicAgentService
  tool/                      # Tool implementations (DateTimeTool)
  util/                      # SkillSeeder, Log extension
```

## Build Commands

- **Build:** `./gradlew build`
- **Run:** `./gradlew bootRun`
- **Test:** `./gradlew test`
- **Clean:** `./gradlew clean`
