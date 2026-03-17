package io.robothouse.agent.graph

import org.bsc.langgraph4j.serializer.StateSerializer
import org.bsc.langgraph4j.state.AgentState
import org.bsc.langgraph4j.state.AgentStateFactory
import java.io.ObjectInput
import java.io.ObjectOutput

/**
 * State serializer for in-memory graph execution that avoids Java serialization.
 *
 * LangGraph4j clones state between node transitions using the serializer's
 * [cloneObject] method. The default implementation serializes to bytes and
 * back, which fails for non-serializable objects like Langchain4j's ChatMessage.
 * This serializer creates a shallow copy of the state data map with deep-copied
 * list values to prevent shared mutable references between node transitions.
 */
class InMemoryStateSerializer<S : AgentState>(
    stateFactory: AgentStateFactory<S>
) : StateSerializer<S>(stateFactory) {

    /** Not supported — this serializer is for in-memory graph execution only. */
    override fun writeData(data: Map<String, Any>, out: ObjectOutput) {
        throw UnsupportedOperationException("Byte serialization not supported — this graph runs in-memory only")
    }

    /** Not supported — this serializer is for in-memory graph execution only. */
    override fun readData(input: ObjectInput): Map<String, Any> {
        throw UnsupportedOperationException("Byte deserialization not supported — this graph runs in-memory only")
    }

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
}
