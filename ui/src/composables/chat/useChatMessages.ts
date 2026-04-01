/**
 * Composable for managing the reactive list of chat messages.
 *
 * Provides actions to add user/assistant messages, append activity events
 * from the SSE stream, transition message status, and bulk-set messages
 * when loading conversation history.
 */
import { ref } from 'vue'
import type { ChatMessage, ChatEvent } from '@/types/chat'

export function useChatMessages() {
  let messageCounter = 0

  function generateId(): string {
    return `msg-${Date.now()}-${++messageCounter}`
  }

  const messages = ref<ChatMessage[]>([])

  // Buffer for accumulating streaming chunks before flushing to reactive state.
  // Flushed on requestAnimationFrame to avoid per-token markdown re-parsing.
  let chunkBuffer = ''
  let chunkTargetId: string | null = null
  let chunkFlushHandle: number | null = null

  function flushChunkBuffer(): void {
    chunkFlushHandle = null
    if (!chunkTargetId || !chunkBuffer) return

    const idx = messages.value.findIndex((m) => m.id === chunkTargetId)
    if (idx === -1) {
      chunkBuffer = ''
      chunkTargetId = null
      return
    }

    const msg = messages.value[idx]!
    const newMessages = [...messages.value]
    newMessages[idx] = { ...msg, streamingText: (msg.streamingText ?? '') + chunkBuffer }
    messages.value = newMessages
    chunkBuffer = ''
  }

  function resetChunkBuffer(): void {
    chunkBuffer = ''
    chunkTargetId = null
    if (chunkFlushHandle !== null) {
      cancelAnimationFrame(chunkFlushHandle)
      chunkFlushHandle = null
    }
  }

  /**
   * Appends a completed user message to the list.
   *
   * @param content - The user's message text.
   */
  function addUserMessage(content: string): void {
    messages.value = [
      ...messages.value,
      { id: generateId(), role: 'user', content, activities: [], status: 'complete' },
    ]
  }

  /**
   * Creates an empty assistant message in 'streaming' status.
   *
   * @returns The generated message ID for use with {@link addActivity} and {@link completeMessage}.
   */
  function startAssistantMessage(): string {
    const id = generateId()
    messages.value = [
      ...messages.value,
      { id, role: 'assistant', content: '', activities: [], status: 'streaming' },
    ]
    return id
  }

  /**
   * Appends an activity event to the specified message. Automatically
   * sets the message content on `final_response` and marks it as errored
   * on `error` events.
   *
   * @param messageId - The ID of the assistant message to update.
   * @param event - The agent event to append.
   */
  function addActivity(messageId: string, event: ChatEvent): void {
    const idx = messages.value.findIndex((m) => m.id === messageId)
    if (idx === -1) return

    const msg = messages.value[idx]!

    // Streaming chunks buffer and flush on requestAnimationFrame to avoid per-token re-renders
    if (event.type === 'response_chunk') {
      chunkBuffer += event.chunk
      chunkTargetId = messageId
      if (chunkFlushHandle === null) {
        chunkFlushHandle = requestAnimationFrame(flushChunkBuffer)
      }
      return
    }

    const updated: ChatMessage = { ...msg, activities: [...msg.activities, event] }

    if (event.type === 'final_response') {
      resetChunkBuffer()
      updated.content = event.response
      updated.streamingText = undefined
      updated.status = 'complete'
    } else if (event.type === 'error') {
      resetChunkBuffer()
      updated.streamingText = undefined
      updated.error = event.message
      updated.status = 'error'
    } else if (event.type === 'thought') {
      resetChunkBuffer()
      updated.streamingText = undefined
    } else if (event.type === 'skill_rerouted') {
      resetChunkBuffer()
      updated.streamingText = undefined
    } else if (event.type === 'approval_required') {
      updated.pendingApprovalId = event.approvalId
    } else if (event.type === 'approval_resolved') {
      updated.pendingApprovalId = undefined
    }

    const newMessages = [...messages.value]
    newMessages[idx] = updated
    messages.value = newMessages
  }

  /**
   * Transitions a streaming message to 'complete' status. No-op if already complete.
   *
   * @param messageId - The ID of the message to complete.
   */
  function completeMessage(messageId: string): void {
    const idx = messages.value.findIndex((m) => m.id === messageId)
    if (idx === -1) return

    const msg = messages.value[idx]!
    if (msg.status === 'streaming') {
      const newMessages = [...messages.value]
      newMessages[idx] = { ...msg, status: 'complete' }
      messages.value = newMessages
    }
  }

  /**
   * Replaces the entire message list (used when loading history or clearing the chat).
   *
   * @param newMessages - The messages to set.
   */
  function setMessages(newMessages: ChatMessage[]): void {
    messages.value = newMessages
  }

  /**
   * Returns a message by ID, or undefined if not found.
   */
  function getMessage(messageId: string): ChatMessage | undefined {
    return messages.value.find((m) => m.id === messageId)
  }

  return { messages, addUserMessage, startAssistantMessage, addActivity, completeMessage, setMessages, getMessage }
}
