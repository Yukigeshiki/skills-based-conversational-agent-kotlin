package io.robothouse.agent.graph

import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

/**
 * Unwraps exceptions from LangGraph4j's CompletableFuture chains to expose
 * the original cause. LangGraph4j wraps node exceptions in [CompletionException]
 * or [ExecutionException] layers; this peels them off so callers see the
 * expected exception type (e.g., TimeoutException).
 */
fun unwrapGraphException(e: Exception): Exception {
    var cause: Throwable = e
    while (cause is CompletionException || cause is ExecutionException) {
        cause = cause.cause ?: break
    }
    return cause as? Exception ?: RuntimeException(cause)
}
