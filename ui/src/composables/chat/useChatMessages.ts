import { ref } from 'vue'
import type { ChatMessage, ChatEvent } from '@/types/chat'

export function useChatMessages() {
  let messageCounter = 0

  function generateId(): string {
    return `msg-${Date.now()}-${++messageCounter}`
  }

  const messages = ref<ChatMessage[]>([])

  function addUserMessage(content: string): void {
    messages.value = [
      ...messages.value,
      { id: generateId(), role: 'user', content, activities: [], status: 'complete' },
    ]
  }

  function startAssistantMessage(): string {
    const id = generateId()
    messages.value = [
      ...messages.value,
      { id, role: 'assistant', content: '', activities: [], status: 'streaming' },
    ]
    return id
  }

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

  function setMessages(newMessages: ChatMessage[]): void {
    messages.value = newMessages
  }

  return { messages, addUserMessage, startAssistantMessage, addActivity, completeMessage, setMessages }
}
