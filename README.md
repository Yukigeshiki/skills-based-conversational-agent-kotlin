# Skills-Based Conversational Agent

[![Agent Test](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-tests.yml/badge.svg)](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-tests.yml)
[![Agent Build](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-build.yml/badge.svg)](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-build.yml)
[![UI Build](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/ui-build.yml/badge.svg)](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/ui-build.yml)

A conversational agent built with Kotlin and Spring Boot. User messages are routed to skills via a cascade of skill routing strategies, then executed through a tool-use loop powered by Claude (LangChain4j). The conversation flow is modelled as LangGraph4j state graphs, with optional PostgreSQL-backed checkpointing for audit trails and workflow resumption. The UI streams events in real time via SSE.

## How It Works

1. **Skill routing** — the user message is routed to the best-matching skill (see [Skill Routing](#skill-routing) below)
2. **Planning** — the agent decomposes the request into execution steps; each step can target a different skill for multi-skill workflows
3. **Agent loop** — Claude reasons, calls tools, observes results, and repeats until done (step failure short-circuits remaining steps)
4. **Validation** — specialist skill responses are validated; inadequate ones are rerouted to the general-assistant fallback
5. **Streaming** — SSE events stream back throughout (skill matched, thoughts, tool calls, plan steps, response)
6. **Memory** — Redis-backed conversation history (50 messages, 24h TTL) enables multi-turn context

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

The UI provides a chat interface and a skills management page for creating, updating, and deleting skills and their reference documents.

## Graph Architecture

The conversation flow is modelled as two LangGraph4j state graphs, each with a clear separation between mutable graph state and immutable infrastructure context captured in node closures.

### Orchestration Graph

Handles the high-level request lifecycle. Acyclic flow with conditional edges for validation and fallback rerouting.

```
START -> load_memory -> route_skill -> execute_skill -> conditional
                                                         -> END (fallback skill)
                                                         -> validate_response -> conditional
                                                                                  -> END (adequate)
                                                                                  -> reroute_fallback -> END
```

### Agent Loop Graph

Handles the LLM tool-execution cycle within each plan step. Cyclic graph that repeats until the LLM responds with text or the iteration limit is reached.

```
START -> call_llm -> conditional -> execute_tools -> call_llm (cycle)
                                 -> END (when done)
```

### Checkpointing

Both graphs support optional PostgreSQL-backed checkpointing, persisting full workflow state as JSONB after each node transition. Disabled by default; enable with:

```properties
agent.checkpointing-enabled=true
```

When enabled, checkpoints are stored in the `graph_checkpoints` table and keyed by conversation ID (orchestration graph) or `agent:{conversationId}:{nanoTime}` (agent loop graph). Checkpoint state is human-readable and queryable via standard PostgreSQL JSONB operators. Custom Jackson serialization handles Langchain4j's ChatMessage hierarchy.

## Skills

Skills define how the agent handles different types of requests. Each skill has a name, description, system prompt, an optional response template, and a list of tools. When a user sends a message, it is routed to the most relevant skill via the [routing cascade](#skill-routing) described below.

A `general-assistant` skill is seeded on first startup and is protected (non-deletable and immutable). To create additional skills, use the skills management page in the UI or the REST API. System prompts should be written in markdown.

All skills use multistep planning — the agent decomposes the request into steps, and each step can target a different skill for multi-skill workflows. Simple requests produce single-step plans and skip per-step overhead. If a step fails, remaining steps are short-circuited.

### Response Templates

Skills can optionally define a **response template** — a markdown template that is injected into the system prompt to standardize how the agent structures its output for that skill.

### Skill References (RAG)

Skills can have **reference documents** attached — markdown or plain-text content that is chunked, embedded, and retrieved at chat time via RAG (Retrieval-Augmented Generation). Only the most relevant chunks are injected into the system prompt alongside the skill's own instructions, keeping token usage predictable while giving the agent access to large bodies of knowledge.

References are managed through the UI's skill expanded view or the REST API. Content is automatically chunked using a structural splitting algorithm (markdown headings, paragraphs, sentences) targeting ~500 tokens per chunk with overlap, then embedded via OpenAI text-embedding-3-small into the same pgvector store used for skill routing. Embedding type metadata (`type=skill` vs `type=reference`) keeps routing and retrieval isolated. Retrieval is skill-scoped — during multistep planning, each step retrieves reference chunks specific to its targeted skill.

### Skill Routing

Messages are routed through a cascade of strategies:

1. **Name mention** — if the message contains a skill name (case-insensitive, ignoring hyphens/underscores), that skill is used directly
2. **Query enrichment** — when conversation history exists, a light model (Claude Haiku) enriches the user message before embedding — resolving pronouns, expanding terse follow-ups like "yes" or "do it" into self-contained queries, and adding domain context for stronger semantic signal
3. **Embedding similarity** — the (enriched) message is embedded via OpenAI text-embedding-3-small (1536-dim) and matched against skill embeddings in pgvector; the highest-scoring non-fallback skill wins, provided it meets the minimum similarity threshold (default 0.60)
4. **Fallback** — if no match meets the minimum similarity threshold, the `general-assistant` skill handles the request
5. **Response validation reroute** — after a specialist skill responds, a light model (Claude Haiku) classifies the response as adequate or inadequate; if the skill deflected or failed to answer, the request is automatically rerouted to the `general-assistant` fallback for a second attempt

## Tools

An example tool (`DateTimeTool`) is included. To add more tools, create a Spring `@Component` in the tool directory, with methods annotated with LangChain4j's `@Tool`, then reference the class name in a skill's `toolNames`.

## API

Swagger UI: http://localhost:9090/swagger-ui.html

### Chat (SSE)

```bash
# New conversation
curl -N -X POST http://localhost:9090/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message": "What time is it in Tokyo?"}'

# Continue an existing conversation
curl -N -X POST http://localhost:9090/api/chat \
  -H 'Content-Type: application/json' \
  -d '{"message": "And in New York?", "conversationId": "<uuid>"}'
```

### Conversation History

```bash
curl http://localhost:9090/api/chat/{conversationId}/history
```

### Skills CRUD

```bash
curl http://localhost:9090/api/skills                    # List
curl -X POST http://localhost:9090/api/skills \          # Create
  -H 'Content-Type: application/json' \
  -d '{"name": "my-skill", "description": "...", "systemPrompt": "...", "responseTemplate": "...", "toolNames": ["DateTimeTool"]}'
curl -X PATCH http://localhost:9090/api/skills/{id} ...  # Update
curl -X DELETE http://localhost:9090/api/skills/{id}     # Delete
```

### Skill References CRUD

```bash
curl http://localhost:9090/api/skills/{skillId}/references                    # List
curl -X POST http://localhost:9090/api/skills/{skillId}/references \          # Create
  -H 'Content-Type: application/json' \
  -d '{"name": "product-docs", "content": "# Product Documentation\n..."}'
curl -X PATCH http://localhost:9090/api/skills/{skillId}/references/{id} ...  # Update
curl -X DELETE http://localhost:9090/api/skills/{skillId}/references/{id}     # Delete
```

### Tools

```bash
curl http://localhost:9090/api/tools                     # List registered tool names
```

## Project Structure

```
services/agent/   Kotlin, Spring Boot 3.5.7, LangChain4j, LangGraph4j, Gradle
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
