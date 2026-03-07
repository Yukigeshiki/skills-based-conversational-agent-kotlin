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
    const updated: ChatMessage = { ...msg, activities: [...msg.activities, event] }

    if (event.type === 'final_response') {
      updated.content = event.response
      updated.status = 'complete'
    } else if (event.type === 'error') {
      updated.error = event.message
      updated.status = 'error'
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

  return { messages, addUserMessage, startAssistantMessage, addActivity, completeMessage, setMessages }
}
