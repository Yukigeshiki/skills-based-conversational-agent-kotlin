/**
 * Composable that connects to the backend SSE stream for a chat message.
 *
 * Orchestrates the send/stop lifecycle: creates an assistant message placeholder,
 * streams events into it via the provided message actions, tracks the conversation
 * ID, and supports aborting an in-flight request.
 */
import { ref, type Ref } from 'vue'
import { chatService } from '@/services/chat'
import type { ChatEvent } from '@/types/chat'

/** Callbacks into the message store used by the stream to update UI state. */
export interface MessageActions {
  addUserMessage: (content: string) => void
  startAssistantMessage: () => string
  addActivity: (messageId: string, event: ChatEvent) => void
  completeMessage: (messageId: string) => void
}

/** Configuration for {@link useChatStream}. */
export interface ChatStreamOptions {
  /** Message store actions to call as events arrive. */
  actions: MessageActions
  /** Reactive conversation ID ref; passed to the backend on each request. */
  conversationId: Ref<string | null>
  /** Called when the backend emits a `conversation_started` event with the resolved ID. */
  onConversationStarted?: (id: string) => void
}

export function useChatStream(options: ChatStreamOptions) {
  const { actions, conversationId, onConversationStarted } = options
  const isStreaming = ref(false)
  let abortController: AbortController | null = null
  let currentMessageId: string | null = null

  /**
   * Sends a message to the backend and begins streaming the response.
   * No-op if already streaming or the message is empty.
   *
   * @param message - The user's message text.
   */
  function send(message: string): void {
    if (isStreaming.value || !message.trim()) return

    actions.addUserMessage(message)
    currentMessageId = actions.startAssistantMessage()
    isStreaming.value = true

    const messageId = currentMessageId

    abortController = chatService.sendMessage(
      message,
      {
        onEvent(event: ChatEvent) {
          if (event.type === 'conversation_started' && onConversationStarted) {
            onConversationStarted(event.conversationId)
          }
          actions.addActivity(messageId, event)
        },
        onError(error: string) {
          actions.addActivity(messageId, {
            type: 'error',
            message: error,
            timestamp: new Date().toISOString(),
          })
          isStreaming.value = false
          abortController = null
          currentMessageId = null
        },
        onComplete() {
          actions.completeMessage(messageId)
          isStreaming.value = false
          abortController = null
          currentMessageId = null
        },
      },
      conversationId.value ?? undefined,
    )
  }

  /** Aborts the in-flight request and marks the current assistant message as complete. */
  function stop(): void {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    if (currentMessageId) {
      actions.completeMessage(currentMessageId)
      currentMessageId = null
    }
    isStreaming.value = false
  }

  return { isStreaming, send, stop }
}
