package io.robothouse.agent.graph.checkpoint

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.langchain4j.data.message.ChatMessage
import io.robothouse.agent.entity.Skill
import io.robothouse.agent.graph.agent.AgentGraphState
import io.robothouse.agent.graph.orchestration.OrchestrationGraphState
import io.robothouse.agent.model.AgentResponse
import io.robothouse.agent.model.ConversationMessage
import io.robothouse.agent.model.ToolExecutionStep
import org.bsc.langgraph4j.serializer.plain_text.PlainTextStateSerializer
import org.bsc.langgraph4j.state.AgentState
import org.bsc.langgraph4j.state.AgentStateFactory

/**
 * Base class for graph state serializers that use Jackson for JSON serialization
 * and fast shallow-copy for state cloning between node transitions.
 *
 * Subclasses implement [deserializeState] to handle type-safe deserialization
 * of their specific state fields since generic `Map<String, Object>` deserialization
 * loses type information for complex values like Langchain4j's ChatMessage hierarchy.
 */
abstract class JsonGraphStateSerializer<S : AgentState>(
    stateFactory: AgentStateFactory<S>,
    protected val objectMapper: ObjectMapper
) : PlainTextStateSerializer<S>(stateFactory) {

    /** Serializes the state data map to a JSON string. */
    override fun writeDataAsString(data: Map<String, Any>): String {
        return objectMapper.writeValueAsString(data)
    }

    /**
     * Deserializes a JSON string back to a typed state map, delegating to
     * [deserializeState] to restore complex types that Jackson would
     * otherwise deserialize as generic LinkedHashMaps.
     */
    override fun readDataFromString(json: String): Map<String, Any> {
        val rawMap: Map<String, Any?> = objectMapper.readValue(json)
        return deserializeState(rawMap)
    }

    /**
     * Converts a raw deserialized map (where complex types are LinkedHashMaps)
     * into a properly typed state map. Subclasses know their field names and
     * types and can deserialize each field appropriately.
     */
    protected abstract fun deserializeState(raw: Map<String, Any?>): Map<String, Any>

    /**
     * Creates a copy of the state by shallow-copying the data map and
     * deep-copying any list values to prevent shared mutable references
     * between node transitions.
     */
    override fun cloneObject(obj: S): S {
        val copied = obj.data().mapValues { (_, v) ->
            if (v is List<*>) ArrayList(v) else v
        }
        return stateFactory().apply(copied)
    }

    /**
     * Converts a raw deserialized value back to its expected type using the
     * ObjectMapper. Returns null if the value is null or missing.
     */
    protected inline fun <reified T> reconvert(value: Any?): T? {
        if (value == null) return null
        if (value is T) return value
        return objectMapper.convertValue(value, T::class.java)
    }

    /**
     * Converts a raw deserialized list back to a typed list using the
     * ObjectMapper. Returns an empty list if the value is null.
     */
    protected inline fun <reified T> reconvertList(value: Any?): List<T> {
        if (value == null) return emptyList()
        if (value is List<*> && (value.isEmpty() || value.first() is T)) {
            @Suppress("UNCHECKED_CAST")
            return value as List<T>
        }
        val typeFactory = objectMapper.typeFactory
        val listType = typeFactory.constructCollectionType(List::class.java, T::class.java)
        return objectMapper.convertValue(value, listType)
    }
}

/**
 * Serializer for [AgentGraphState] that handles Langchain4j's ChatMessage
 * hierarchy and ToolExecutionStep lists via Jackson with the [ChatMessageModule].
 */
class AgentGraphStateSerializer(
    objectMapper: ObjectMapper
) : JsonGraphStateSerializer<AgentGraphState>(::AgentGraphState, objectMapper) {

    /**
     * Restores ChatMessage lists, ToolExecutionStep lists, and primitive
     * fields from the raw deserialized map.
     */
    override fun deserializeState(raw: Map<String, Any?>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        raw[AgentGraphState.MESSAGES]?.let {
            result[AgentGraphState.MESSAGES] = reconvertList<ChatMessage>(it)
        }
        raw[AgentGraphState.STEPS]?.let {
            result[AgentGraphState.STEPS] = reconvertList<ToolExecutionStep>(it)
        }
        raw[AgentGraphState.ITERATION]?.let {
            result[AgentGraphState.ITERATION] = (it as Number).toInt()
        }
        raw[AgentGraphState.RESPONSE]?.let {
            result[AgentGraphState.RESPONSE] = it as String
        }
        raw[AgentGraphState.DONE]?.let {
            result[AgentGraphState.DONE] = it as Boolean
        }
        return result
    }
}

/**
 * Serializer for [OrchestrationGraphState] that handles Skill entities,
 * AgentResponse, and ConversationMessage lists via Jackson.
 */
class OrchestrationGraphStateSerializer(
    objectMapper: ObjectMapper
) : JsonGraphStateSerializer<OrchestrationGraphState>(::OrchestrationGraphState, objectMapper) {

    /**
     * Restores Skill, AgentResponse, ConversationMessage lists, and
     * primitive fields from the raw deserialized map.
     */
    override fun deserializeState(raw: Map<String, Any?>): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        raw[OrchestrationGraphState.CONVERSATION_ID]?.let {
            result[OrchestrationGraphState.CONVERSATION_ID] = it as String
        }
        raw[OrchestrationGraphState.USER_MESSAGE]?.let {
            result[OrchestrationGraphState.USER_MESSAGE] = it as String
        }
        raw[OrchestrationGraphState.CONVERSATION_HISTORY]?.let {
            result[OrchestrationGraphState.CONVERSATION_HISTORY] = reconvertList<ConversationMessage>(it)
        }
        raw[OrchestrationGraphState.MATCHED_SKILL]?.let {
            result[OrchestrationGraphState.MATCHED_SKILL] = reconvert<Skill>(it)!!
        }
        raw[OrchestrationGraphState.AGENT_RESPONSE]?.let {
            result[OrchestrationGraphState.AGENT_RESPONSE] = reconvert<AgentResponse>(it)!!
        }
        raw[OrchestrationGraphState.VALIDATED]?.let {
            result[OrchestrationGraphState.VALIDATED] = it as Boolean
        }
        return result
    }
}
