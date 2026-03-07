import { ref } from 'vue'
import { chatService } from '@/services/chat'
import type { ChatEvent } from '@/types/chat'

export interface MessageActions {
  addUserMessage: (content: string) => void
  startAssistantMessage: () => string
  addActivity: (messageId: string, event: ChatEvent) => void
  completeMessage: (messageId: string) => void
}

export function useChatStream(actions: MessageActions) {
  const isStreaming = ref(false)
  let abortController: AbortController | null = null
  let currentMessageId: string | null = null

  function send(message: string): void {
    if (isStreaming.value || !message.trim()) return

    actions.addUserMessage(message)
    currentMessageId = actions.startAssistantMessage()
    isStreaming.value = true

    const messageId = currentMessageId

    abortController = chatService.sendMessage(message, {
      onEvent(event: ChatEvent) {
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
    })
  }

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
