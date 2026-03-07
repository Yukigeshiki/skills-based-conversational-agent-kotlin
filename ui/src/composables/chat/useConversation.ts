/**
 * Composable for conversation lifecycle management.
 *
 * Persists the active conversation ID to localStorage so it survives page
 * refreshes. Provides methods to start a new conversation and load history
 * from the backend. State is module-scoped (singleton) so all callers share
 * the same conversation ID.
 */
import { ref } from 'vue'
import { chatService } from '@/services/chat'
import type { ChatMessage } from '@/types/chat'

const STORAGE_KEY = 'conversationId'

const conversationId = ref<string | null>(localStorage.getItem(STORAGE_KEY))

export function useConversation() {
  /**
   * Sets the active conversation ID and persists it to localStorage.
   *
   * @param id - The conversation UUID received from the backend.
   */
  function setConversationId(id: string): void {
    conversationId.value = id
    localStorage.setItem(STORAGE_KEY, id)
  }

  /** Clears the active conversation ID so the next message starts a fresh conversation. */
  function startNewConversation(): void {
    conversationId.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

  /**
   * Fetches conversation history from the backend and maps it to ChatMessage format.
   *
   * @returns The conversation messages as ChatMessage objects, or an empty array
   *          if no conversation is active or the request fails.
   */
  async function loadHistory(): Promise<ChatMessage[]> {
    if (!conversationId.value) return []

    try {
      const history = await chatService.getHistory(conversationId.value)
      let counter = 0
      return history.map((msg) => ({
        id: `history-${++counter}`,
        role: msg.role as 'user' | 'assistant',
        content: msg.content,
        activities: msg.activities ?? [],
        status: 'complete' as const,
      }))
    } catch {
      return []
    }
  }

  return { conversationId, setConversationId, startNewConversation, loadHistory }
}
