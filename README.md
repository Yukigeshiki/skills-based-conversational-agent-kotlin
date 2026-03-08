# Skills-Based Conversational Agent

An agentic chat system built with Kotlin and Spring Boot. User messages are routed to skills via semantic similarity, then executed through a tool-use loop powered by Claude. The UI streams events in real time via SSE.

## How It Works

1. User message is embedded locally (AllMiniLmL6V2, 384-dim ONNX)
2. Similarity search in pgvector finds the best-matching skill
3. The skill provides a system prompt, tool list, and optional planning prompt
4. Agent loop: Claude reasons, calls tools, observes results, repeats until done
5. SSE events stream back throughout (skill matched, thoughts, tool calls, response)

Skills with a `planningPrompt` trigger multistep decomposition — the agent breaks the request into steps, executes each, then synthesizes results.

## Prerequisites

- [SDKMAN!](https://sdkman.io/) (for Java)
- Docker
- Node.js + pnpm
- `ANTHROPIC_API_KEY` environment variable

## Getting Started

### Agent service

```bash
cd services/agent

# Install Java 21 via SDKMAN
sdk env install

# Start PostgreSQL (pgvector) and Redis
docker compose up -d

# Run the backend (port 9090)
./gradlew bootRun
```

A `general-assistant` skill is seeded on first startup.

### UI

```bash
cd ui
pnpm install
pnpm dev  # port 5174
```

The UI provides a chat interface and a skills management page for creating, updating, and deleting skills.

## Tools

A `DateTimeTool` is included for timezone-aware date/time queries. To add more tools, create a Spring `@Component` with methods annotated with LangChain4j's `@Tool`, then reference the class name in a skill's `toolNames`.

## API

Swagger UI: http://localhost:9090/swagger-ui.html

### Chat (SSE)

```bash
curl -N -X POST http://localhost:9090/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message": "What time is it in Tokyo?"}'
```

### Skills CRUD

```bash
curl http://localhost:9090/api/skills                    # List
curl -X POST http://localhost:9090/api/skills \          # Create
  -H 'Content-Type: application/json' \
  -d '{"name": "my-skill", "description": "...", "systemPrompt": "...", "toolNames": ["DateTimeTool"]}'
curl -X PATCH http://localhost:9090/api/skills/{id} ...  # Update
curl -X DELETE http://localhost:9090/api/skills/{id}     # Delete
```

## Project Structure

```
services/agent/   Kotlin, Spring Boot 3.5.7, LangChain4j, Gradle
ui/               Vue 3, TypeScript, Vite, Tailwind CSS
```

## Build Commands

**Agent service** (from `services/agent/`):

```bash
./gradlew build     # build
./gradlew test      # test
./gradlew bootRun   # run
```

**UI** (from `ui/`):

```bash
pnpm dev       # dev server
pnpm build     # production build
pnpm lint      # lint
```
