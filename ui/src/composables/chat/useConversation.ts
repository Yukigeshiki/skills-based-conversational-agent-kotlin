import { ref } from 'vue'
import { chatService } from '@/services/chat'
import type { ChatMessage } from '@/types/chat'

const STORAGE_KEY = 'conversationId'

const conversationId = ref<string | null>(localStorage.getItem(STORAGE_KEY))

export function useConversation() {
  function setConversationId(id: string): void {
    conversationId.value = id
    localStorage.setItem(STORAGE_KEY, id)
  }

  function startNewConversation(): void {
    conversationId.value = null
    localStorage.removeItem(STORAGE_KEY)
  }

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
