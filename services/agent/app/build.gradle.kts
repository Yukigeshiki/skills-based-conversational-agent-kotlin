import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
}

group = "io.robothouse"
version = "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_21
}

repositories {
    mavenCentral()
}

configurations.all {
    exclude(group = "commons-logging", module = "commons-logging")
}

ext["commons-lang3.version"] = libs.versions.commons.lang3.get()

dependencies {
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.logging)

    // LangChain4j with Anthropic
    implementation(libs.langchain4j.spring.boot.starter)
    implementation(libs.langchain4j.anthropic.spring.boot.starter)
    implementation(libs.langchain4j.pgvector)
    implementation(libs.langchain4j.open.ai.spring.boot.starter)

    // Tokenizer
    implementation(libs.jtokkit)

    // Caching
    implementation(libs.caffeine)

    // Redis
    implementation(libs.spring.boot.starter.data.redis)

    // JPA, Flyway & PostgreSQL
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    // OpenAPI documentation
    implementation(libs.springdoc.openapi)

    // Testing dependencies
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.langchain4j.embeddings.all.minilm.l6.v2)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(libs.h2.database)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.register("printVersion") {
    val projectVersion = version
    doLast {
        println(projectVersion)
    }
}
