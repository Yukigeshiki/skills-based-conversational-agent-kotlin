# AGENT.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Monorepo containing backend services and a UI. Backend services live in `services/`, and the frontend lives in `ui/`.

## Repo Structure

- `services/agent/` — Spring Boot 3.5.7 Kotlin service for agent development experimentation. Uses Gradle 8.14.1 with Kotlin DSL, Java 21, Kotlin 2.1.20, and virtual threads.
- `ui/` — Vue 3 + TypeScript frontend with Vite, Tailwind CSS, and Radix Vue/Reka UI components.

## Agent Service

### Build Commands

Run from `services/agent/`:

- **Build:** `./gradlew build`
- **Run:** `./gradlew bootRun`
- **Test all:** `./gradlew test`
- **Run single test:** `./gradlew test --tests "io.robothouse.agent.ApplicationTest"`
- **Clean:** `./gradlew clean`

### Architecture

Single-module Gradle project (`app` subproject) with Spring Boot. Base package: `io.robothouse.agent`.

- Source: `services/agent/app/src/main/kotlin/io/robothouse/agent/`
- Tests: `services/agent/app/src/test/kotlin/io/robothouse/agent/` (JUnit 5/Kotlin)
- Config: `services/agent/app/src/main/resources/application.properties`
- Profiles: `application-production.properties`, `application-test.properties`
- Version catalog: `services/agent/gradle/libs.versions.toml`

### Key Patterns

- **Testing:** JUnit 5 with Kotlin, `@SpringBootTest` and `@ActiveProfiles("test")`
- **Logging:** kotlin-logging (`io.github.oshai.kotlinlogging`)
- **API docs:** SpringDoc OpenAPI at `/swagger-ui.html`
- **Actuator:** Health/info/prometheus exposed at `/actuator/health`
- **Config classes** go in `config/` package, controllers in `controller/`, services in `service/`
- Constructor injection only (no @Autowired)
- `-Xjsr305=strict` for null-safety with Spring annotations

## UI

### Build Commands

Run from `ui/`:

- **Dev server:** `pnpm dev`
- **Build:** `pnpm build`
- **Lint:** `pnpm lint`
- **Type check:** `npx vue-tsc --noEmit`

### Architecture

Vue 3 SPA with TypeScript and Vite. Uses pnpm for package management.

- Source: `ui/src/`
- Components: `ui/src/components/` (reusable UI in `common/`, shadcn-style primitives in `ui/`, feature components in `tables/`)
- Views: `ui/src/views/` (page-level components: `ChatView`, `SkillsView`)
- Services: `ui/src/services/` (API clients: `chatService`, `skillService`, `toolService`)
- Composables: `ui/src/composables/` (reusable logic: `chat/`, `skills/`, `tables/`, `ui/`)
- Types: `ui/src/types/` (TypeScript interfaces for API requests/responses)
- Stores: `ui/src/stores/` (Pinia state management)
- Router: `ui/src/router/` (Vue Router configuration)

### Key Patterns

- **UI components:** Radix Vue / Reka UI primitives wrapped in shadcn-style components under `components/ui/`
- **Styling:** Tailwind CSS
- **API client:** Axios with shared `apiClient` instance in `services/api.ts`
- **SSE streaming:** Native `EventSource` for chat, helper in `services/api.ts`
- **State management:** Pinia stores
- **Markdown rendering:** `marked` with DOMPurify sanitization (via `renderMarkdown` in `composables/ui/`)
- **Icons:** Lucide Vue Next
- Services are singleton class instances exported from `services/index.ts`
- Reusable table logic (sorting, pagination, filtering, expandable rows) lives in `composables/tables/`
