package io.robothouse.agent.util

/**
 * Per-thread channel that bridges the Spring-singleton retry decorators
 * ([RetryingChatModel], [RetryingStreamingChatModel]) to the per-request agent
 * loop. The agent installs a retry-event callback and a remaining-budget
 * supplier before each chat call; the decorators read them on every attempt;
 * both are cleared in a finally block. Storage is two [ThreadLocal] slots —
 * safe with virtual threads and stable across the synchronous retry loop.
 */
object LlmRetryEventEmitter {

    private val callback = ThreadLocal<RetryCallback?>()
    private val budget = ThreadLocal<BudgetSupplier?>()

    /**
     * Callback signature: `(attempt, maxAttempts, safeDescription)`. The decorators
     * sanitise the underlying throwable via [LlmRetryClassifier.safeDescription]
     * before emitting, so callers receive a UI-safe category label rather than the
     * raw `throwable.message` (which can contain provider response bodies, internal
     * hostnames, or other sensitive payloads). Never reintroduce a `Throwable`
     * parameter here without a strong reason — the type-level guarantee is what
     * keeps unsafe error text out of user-facing events.
     */
    fun interface RetryCallback {
        fun onRetry(attempt: Int, maxAttempts: Int, safeDescription: String)
    }

    /**
     * Returns the remaining wall-clock budget in milliseconds for the in-flight
     * agent operation. Decorators consult this before scheduling a retry to
     * avoid running past the agent timeout.
     */
    fun interface BudgetSupplier {
        fun remainingMs(): Long
    }

    /** Installs a retry callback for the current thread, replacing any prior installation. */
    fun setCallback(callback: RetryCallback) {
        this.callback.set(callback)
    }

    /** Removes the retry callback for the current thread. */
    fun clearCallback() {
        callback.remove()
    }

    /** Installs a remaining-budget supplier for the current thread. */
    fun setBudgetSupplier(supplier: BudgetSupplier) {
        budget.set(supplier)
    }

    /** Removes the remaining-budget supplier for the current thread. */
    fun clearBudgetSupplier() {
        budget.remove()
    }

    /**
     * Invokes the installed callback if present. Silently no-ops when no callback
     * is installed. Decorators must pass a sanitised description (typically from
     * [LlmRetryClassifier.safeDescription]) — see [RetryCallback] for the rationale.
     */
    fun emit(attempt: Int, maxAttempts: Int, safeDescription: String) {
        callback.get()?.onRetry(attempt, maxAttempts, safeDescription)
    }

    /**
     * Returns the remaining budget in milliseconds, or null when no supplier
     * is installed (in which case decorators do not enforce a wall-clock budget).
     */
    fun remainingBudgetMs(): Long? = budget.get()?.remainingMs()

    /**
     * Convenience helper that installs both a retry callback and a budget
     * supplier for the duration of [block], clearing both in a finally block.
     */
    inline fun <T> withCallbackAndBudget(
        callback: RetryCallback,
        budgetSupplier: BudgetSupplier,
        block: () -> T
    ): T {
        setCallback(callback)
        setBudgetSupplier(budgetSupplier)
        try {
            return block()
        } finally {
            clearCallback()
            clearBudgetSupplier()
        }
    }
}
