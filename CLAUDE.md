# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.5.7 Kotlin service for agent development experimentation. Uses Gradle 8.14.1 with Kotlin DSL, Java 21, Kotlin 2.1.20, and virtual threads.

## Build Commands

- **Build:** `./gradlew build`
- **Run:** `./gradlew bootRun`
- **Test all:** `./gradlew test`
- **Run single test:** `./gradlew test --tests "io.robothouse.agent.ApplicationTest"`
- **Clean:** `./gradlew clean`

## Architecture

Single-module Gradle project (`app` subproject) with Spring Boot. Base package: `io.robothouse.agent`.

- Source: `app/src/main/kotlin/io/robothouse/agent/`
- Tests: `app/src/test/kotlin/io/robothouse/agent/` (JUnit 5/Kotlin)
- Config: `app/src/main/resources/application.properties`
- Profiles: `application-production.properties`, `application-test.properties`
- Version catalog: `gradle/libs.versions.toml`

## Key Patterns

- **Testing:** JUnit 5 with Kotlin, `@SpringBootTest` and `@ActiveProfiles("test")`
- **Logging:** kotlin-logging (`io.github.oshai.kotlinlogging`)
- **API docs:** SpringDoc OpenAPI at `/swagger-ui.html`
- **Actuator:** Health/info/prometheus exposed at `/actuator/health`
- **Config classes** go in `config/` package, controllers in `controller/`, services in `service/`
- Constructor injection only (no @Autowired)
- `-Xjsr305=strict` for null-safety with Spring annotations
