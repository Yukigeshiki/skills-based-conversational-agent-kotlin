# Skills-Based Conversational Agent

[![Agent Test](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-test.yml/badge.svg)](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-test.yml)
[![Agent Build](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-build.yml/badge.svg)](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-build.yml)
[![UI Build](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/ui-build.yml/badge.svg)](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/ui-build.yml)

A conversational agent built with Kotlin and Spring Boot. User messages are routed to skills via a cascade of skill routing strategies, then executed through a tool-use loop powered by Claude (LangChain4j). The UI streams events in real time via SSE.

## How It Works

1. **Skill routing** — the user message is routed to the best-matching skill (see [Skill Routing](#skill-routing) below)
2. **Planning** — the skill provides a system prompt and tool list; the agent decomposes the request into execution steps
3. **Agent loop** — Claude reasons, calls tools, observes results, and repeats until done
4. **Validation** — specialist skill responses are validated; inadequate ones are rerouted to the general-assistant fallback
5. **Streaming** — SSE events stream back throughout (skill matched, thoughts, tool calls, plan steps, response)

## Prerequisites

- [SDKMAN!](https://sdkman.io/) (for Java)
- Docker
- Node.js + pnpm
- `ANTHROPIC_API_KEY` environment variable
- `OPENAI_API_KEY` environment variable

## Getting Started

### Docker Compose (full stack)

```bash
# Start everything — databases, agent service, and UI
docker compose --profile all up -d
```

This builds and runs all services:
- Agent service at http://localhost:9090
- UI at http://localhost:5173
- PostgreSQL (pgvector) and Redis

To start only the databases:

```bash
docker compose up -d
```

### Local development

#### Agent service

```bash
cd services/agent

# Install Java 21 via SDKMAN
sdk env install

# Run the backend (port 9090)
./gradlew bootRun
```

A `general-assistant` skill is seeded on first startup.

#### UI

```bash
cd ui
pnpm install
pnpm dev  # port 5173
```

The UI provides a chat interface and a skills management page for creating, updating, and deleting skills.

## Skills

Skills define how the agent handles different types of requests. Each skill has a name, description, system prompt, and a list of tools. When a user sends a message, it is routed to the most relevant skill via the [routing cascade](#skill-routing) described below.

A `general-assistant` skill is seeded on first startup. To create additional skills, use the skills management page in the UI or the REST API. System prompts should be written in markdown.

All skills use multistep planning — the agent decomposes the request into steps, executes each with the skill's tools, then synthesizes the results. Simple requests produce single-step plans and skip per-step overhead.

### Skill Routing

Messages are routed through a cascade of strategies:

1. **Name mention** — if the message contains a skill name (case-insensitive, ignoring hyphens/underscores), that skill is used directly
2. **Embedding similarity** — the message is embedded via OpenAI text-embedding-3-small (1536-dim) and matched against skill embeddings in pgvector; the highest-scoring non-fallback skill wins
3. **Context retry** — if the best match is the fallback skill with a score below the configured threshold (default 0.6) and conversation history exists, the last agent message is prepended to the query, and similarity search is retried — this handles terse follow-ups like "yes" or "do it" that lack semantic content on their own
4. **Fallback** — if no better match is found, the `general-assistant` skill handles the request
5. **Response validation reroute** — after a specialist skill responds, a light model (Claude Haiku) classifies the response as adequate or inadequate; if the skill deflected or failed to answer, the request is automatically rerouted to the `general-assistant` fallback for a second attempt

## Tools

An example tool (`DateTimeTool`) is included. To add more tools, create a Spring `@Component` in the tool directory, with methods annotated with LangChain4j's `@Tool`, then reference the class name in a skill's `toolNames`.

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
