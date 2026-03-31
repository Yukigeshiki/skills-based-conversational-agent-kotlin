package io.robothouse.agent.entity

import io.robothouse.agent.converter.HttpToolParameterListConverter
import io.robothouse.agent.converter.StringMapConverter
import io.robothouse.agent.model.HttpMethod
import io.robothouse.agent.model.HttpToolParameter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * JPA entity representing a user-defined HTTP tool that can be invoked
 * by the agent at runtime without requiring a code deployment.
 */
@Entity
@Table(name = "http_tools")
class HttpTool(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(unique = true, nullable = false, length = 64)
    var name: String = "",

    @Column(nullable = false, length = MAX_DESCRIPTION_LENGTH)
    var description: String = "",

    @Column(name = "endpoint_url", nullable = false, length = 2048)
    var endpointUrl: String = "",

    @Column(name = "http_method", nullable = false, length = 10)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    var httpMethod: HttpMethod = HttpMethod.GET,

    @Column(nullable = false, columnDefinition = "JSONB")
    @Convert(converter = StringMapConverter::class)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    var headers: Map<String, String> = emptyMap(),

    @Column(nullable = false, columnDefinition = "JSONB")
    @Convert(converter = HttpToolParameterListConverter::class)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    var parameters: List<HttpToolParameter> = emptyList(),

    @Column(name = "timeout_seconds", nullable = false)
    var timeoutSeconds: Int = DEFAULT_TIMEOUT_SECONDS,

    @Column(name = "max_response_length", nullable = false)
    var maxResponseLength: Int = DEFAULT_MAX_RESPONSE_LENGTH,

    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now()
) {
    /**
     * Sets both timestamps to the current instant before initial persistence.
     */
    @PrePersist
    fun onPrePersist() {
        val now = Instant.now()
        createdAt = now
        updatedAt = now
    }

    /**
     * Updates the modified timestamp before each update.
     */
    @PreUpdate
    fun onPreUpdate() {
        updatedAt = Instant.now()
    }

    companion object {
        const val MAX_DESCRIPTION_LENGTH = 1000
        const val DEFAULT_TIMEOUT_SECONDS = 30
        const val DEFAULT_MAX_RESPONSE_LENGTH = 8000
    }
}
