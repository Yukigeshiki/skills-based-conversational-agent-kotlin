# Skills-Based Conversational Agent

[![Agent Test](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-tests.yml/badge.svg)](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-tests.yml)
[![Agent Build](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-build.yml/badge.svg)](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/agent-build.yml)
[![UI Build](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/ui-build.yml/badge.svg)](https://github.com/Yukigeshiki/skills-based-conversational-agent-kotlin/actions/workflows/ui-build.yml)

A conversational agent built with Kotlin and Spring Boot. User messages are routed to skills via a cascade of skill routing strategies, then executed through a tool-use loop powered by Claude (LangChain4j). The conversation flow is modelled as LangGraph4j state graphs, with optional PostgreSQL-backed checkpointing for audit trails and workflow resumption. The UI streams events in real time via SSE.

## How It Works

1. **Skill routing** — the user message is routed to the best-matching skill (see [Skill Routing](#skill-routing) below)
2. **Planning** — the agent decomposes the request into execution steps with declared dependencies; independent steps run in parallel on virtual threads, dependent steps wait for their prerequisites; each step can target a different skill for multi-skill workflows
3. **Agent loop** — Claude reasons, calls tools, observes results, and repeats until done; skills can delegate to other skills at runtime via the `delegateToSkill` meta-tool; skills with `requiresApproval` pause before tool execution for human review
4. **Validation** — specialist skill responses are validated; inadequate ones are rerouted to the general-assistant fallback
5. **Streaming** — SSE events stream back throughout (skill matched, thoughts, tool calls, plan steps, response chunks); the LLM response streams token-by-token as a thought preview in the activity log, then renders as complete markdown once confirmed
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

The UI provides a chat interface, a skills management page, and an HTTP tools management page.

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

Handles the LLM tool-execution cycle within each plan step. Cyclic graph that repeats until the LLM responds with text or the iteration limit is reached. When a skill has `requiresApproval` enabled and checkpointing is active, `interruptBefore("execute_tools")` pauses the graph before tool execution, allowing human review via the approval endpoint.

```
START -> call_llm -> conditional -> execute_tools -> call_llm (cycle)
                                 -> END (when done)
```

### Checkpointing

Both graphs support optional PostgreSQL-backed checkpointing, persisting full workflow state as JSONB after each node transition. Enabled by default with:

```properties
agent.checkpointing-enabled=true
```

When enabled, checkpoints are stored in the `graph_checkpoints` table and keyed by conversation ID (orchestration graph) or `agent:{conversationId}:{nanoTime}` (agent loop graph). Checkpoint state is human-readable and queryable via standard PostgreSQL JSONB operators. Custom Jackson serialization handles Langchain4j's ChatMessage hierarchy.

## Skills

Skills define how the agent handles different types of requests. Each skill has a name, description, system prompt, an optional response template, an optional list of tools, and a `requiresApproval` flag. When a user sends a message, it is routed to the most relevant skill via the [routing cascade](#skill-routing) described below.

A `general-assistant` skill is seeded on first startup and is protected (non-deletable and immutable). To create additional skills, use the skills management page in the UI or the REST API. System prompts should be written in markdown.

All queries use multistep planning — the agent decomposes the request into steps, and each step can target a different skill for multi-skill workflows. Simple requests produce single-step plans and skip per-step overhead. If a step fails, remaining steps are short-circuited.

### Skill-to-Skill Delegation

During single-step execution, every skill has access to a `delegateToSkill` meta-tool that enables runtime delegation to other skills. When the LLM determines that a task requires capabilities from a different skill, it can call `delegateToSkill(skillName, request)` to hand the work off. The target skill executes with its own system prompt, tools, and RAG context, and the result flows back as a tool execution result. A configurable recursion depth limit (default 2) prevents infinite delegation chains. The current skill is excluded from the available delegation targets to prevent self-delegation.

Delegation is only available in single-step plans. Multistep plans handle cross-skill orchestration via the planner, which assigns each step to the appropriate skill — delegation is not needed and is excluded to prevent duplicate work between parallel steps.

### Tool Approval (Human-in-the-Loop)

Skills can optionally require human approval before tool execution by setting `requiresApproval` to `true`. When enabled (requires `agent.checkpointing-enabled=true`), the agent loop pauses before executing tools, emits an `approval_required` SSE event with the pending tool calls, and waits for a human decision via the `POST /api/chat/{conversationId}/approve` endpoint. Approved requests resume from the checkpoint; rejected requests return a rejection message.

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

### Bean Tools

An example tool (`DateTimeTool`) is included. To add more tools, create a Spring `@Component` in the tool directory, with methods annotated with LangChain4j's `@Tool`, then reference the class name in a skill's `toolNames`.

A built-in `delegateToSkill` meta-tool is automatically available to all skills (it does not need to be added to `toolNames`). It enables skill-to-skill handoff at runtime — see [Skill-to-Skill Delegation](#skill-to-skill-delegation).

### HTTP Tools

HTTP tools let users define tools backed by external HTTP endpoints — without writing code or redeploying. Each tool specifies an endpoint URL, HTTP method, headers (with `{{ENV_VAR}}` placeholder support for secrets), typed parameters, a timeout, and a max response length. HTTP tools are created and managed via the UI's HTTP Tools page or the REST API. They appear alongside bean tools in the tool selector and can be assigned to skills.

HTTP calls are protected by Failsafe resilience policies (retry, circuit breaker, rate limiter, bulkhead) and SSRF validation that blocks private networks, loopback addresses, cloud metadata endpoints, CGNAT, and other reserved IP ranges. Tool names must be camelCase alphanumeric (e.g. `weatherLookup`) to remain compatible with skill `toolNames` validation.

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

### Tool Approval (SSE)

```bash
# Approve pending tool execution (returns SSE stream with remaining events)
curl -N -X POST http://localhost:9090/api/chat/{conversationId}/approve \
  -H 'Content-Type: application/json' \
  -d '{"approvalId": "<uuid>", "decision": "APPROVED"}'

# Reject pending tool execution
curl -N -X POST http://localhost:9090/api/chat/{conversationId}/approve \
  -H 'Content-Type: application/json' \
  -d '{"approvalId": "<uuid>", "decision": "REJECTED"}'
```

### Skills CRUD

```bash
curl http://localhost:9090/api/skills                    # List
curl -X POST http://localhost:9090/api/skills \          # Create
  -H 'Content-Type: application/json' \
  -d '{"name": "my-skill", "description": "...", "systemPrompt": "...", "responseTemplate": "...", "toolNames": ["DateTimeTool"], "requiresApproval": false}'
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
curl http://localhost:9090/api/tools                     # List all registered tool names (bean + HTTP)
```

### HTTP Tools CRUD

```bash
curl http://localhost:9090/api/http-tools                    # List
curl -X POST http://localhost:9090/api/http-tools \          # Create
  -H 'Content-Type: application/json' \
  -d '{"name": "weatherLookup", "description": "Gets current weather", "endpointUrl": "https://api.example.com/weather", "httpMethod": "GET", "headers": {}, "parameters": [{"name": "city", "type": "string", "description": "City name", "required": true}], "timeoutSeconds": 30, "maxResponseLength": 8000}'
curl -X PATCH http://localhost:9090/api/http-tools/{id} ...  # Update
curl -X DELETE http://localhost:9090/api/http-tools/{id}     # Delete
curl -X POST http://localhost:9090/api/http-tools/{id}/test \  # Test
  -H 'Content-Type: application/json' \
  -d '{"arguments": {"city": "London"}}'
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
