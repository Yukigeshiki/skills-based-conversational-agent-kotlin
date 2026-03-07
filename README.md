# Skills-Based Conversational Agent

A skills-based agentic system built with Kotlin and Spring Boot. User messages are routed to programmable skills via semantic similarity, then executed through a tool-use agent loop powered by Claude. Real-time observability via SSE streaming.

## How It Works

```
User message
  -> Embed with AllMiniLmL6V2 (local ONNX, 384 dims)
  -> Similarity search against skill descriptions in pgvector
  -> Best-matching skill provides: system prompt, tools, optional planning prompt
  -> Agent loop: Claude reasons, calls tools, observes results, repeats until done
  -> SSE stream emits typed events throughout (skill matched, tool calls, thoughts, etc.)
```

Skills with a `planningPrompt` trigger multistep task decomposition — the agent breaks the request into steps, executes each with its own tool-use loop, then synthesizes results.

## Tech Stack

**Backend** (`services/agent/`) — Kotlin 2.1.20, Java 21, Spring Boot 3.5.7, LangChain4j (Claude Sonnet 4.6), PostgreSQL 17 + pgvector, Flyway, Gradle 8.14.1

**Frontend** (`ui/`) — Vue 3, TypeScript, Vite, Tailwind CSS, Pinia, Radix/Reka UI primitives

## Prerequisites

- Java 21+
- Docker (PostgreSQL with pgvector)
- Node.js + pnpm (for UI)
- `ANTHROPIC_API_KEY` environment variable

## Getting Started

```bash
# Start PostgreSQL with pgvector
cd services/agent && docker compose up -d

# Run the backend (port 9090)
./gradlew bootRun

# Run the UI (port 5174)
cd ui && pnpm install && pnpm dev
```

Two skills are seeded on first startup: `datetime-assistant` and `general-assistant`.

## API

### Chat (SSE)

```bash
curl -N -X POST http://localhost:9090/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message": "What time is it in Tokyo?"}'
```

Streams events: `skill_matched`, `plan_created`, `iteration_started`, `thought`, `tool_call_started`, `tool_call_completed`, `final_response`, etc.

### Skills CRUD

```bash
curl http://localhost:9090/api/skills                    # List
curl -X POST http://localhost:9090/api/skills \          # Create
  -H 'Content-Type: application/json' \
  -d '{"name": "my-skill", "description": "...", "systemPrompt": "...", "toolNames": ["DateTimeTool"]}'
curl -X PATCH http://localhost:9090/api/skills/{id} ...  # Partial update
curl -X DELETE http://localhost:9090/api/skills/{id}     # Delete
```

- Swagger UI: `http://localhost:9090/swagger-ui.html`
- Health: `http://localhost:9090/actuator/health`

## Project Structure

```
services/agent/app/src/main/kotlin/io/robothouse/agent/
  config/        # Spring config, embedding, agent/routing properties
  controller/    # ChatController (SSE), SkillController (CRUD)
  service/       # DynamicAgentService, SkillRouterService, StreamingChatService, TaskPlanningService
  model/         # Entities, DTOs, AgentEvent (sealed class), TaskMemory
  repository/    # SkillRepository (JPA), ToolRepository (bean scanner)
  tool/          # Tool implementations (DateTimeTool)
  util/          # SkillSeeder, logging

ui/src/
  views/         # ChatView, SkillsView
  components/    # Skills table with CRUD dialogs, Shadcn-style primitives
  composables/   # Table logic, skill CRUD, filters, view modes
  services/      # Axios client, SSE helper, SkillService
```

## Build Commands

Backend (from `services/agent/`):

- `./gradlew build` — build
- `./gradlew test` — run tests
- `./gradlew bootRun` — run

Frontend (from `ui/`):

- `pnpm dev` — dev server
- `pnpm build` — production build
- `pnpm lint` — lint