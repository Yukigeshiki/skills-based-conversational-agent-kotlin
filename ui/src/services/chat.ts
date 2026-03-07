import type { ChatEvent } from '@/types/chat'

const API_URL = import.meta.env.AGENT_SERVICE_URL || 'http://localhost:9090'

export interface ChatStreamCallbacks {
  onEvent: (event: ChatEvent) => void
  onError: (error: string) => void
  onComplete: () => void
}

class ChatService {
  sendMessage(message: string, callbacks: ChatStreamCallbacks): AbortController {
    const controller = new AbortController()

    this.streamResponse(message, callbacks, controller.signal).catch((err) =>
      callbacks.onError(err instanceof Error ? err.message : 'Unexpected error'),
    )

    return controller
  }

  private async streamResponse(
    message: string,
    callbacks: ChatStreamCallbacks,
    signal: AbortSignal,
  ): Promise<void> {
    try {
      const response = await fetch(`${API_URL}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
        signal,
      })

      if (!response.ok) {
        callbacks.onError(`Server error: ${response.status} ${response.statusText}`)
        return
      }

      const reader = response.body?.getReader()
      if (!reader) {
        callbacks.onError('No response stream available')
        return
      }

      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()

        if (done) {
          // Process any remaining buffer
          if (buffer.trim()) {
            this.processBlock(buffer, callbacks)
          }
          callbacks.onComplete()
          return
        }

        buffer += decoder.decode(value, { stream: true })

        const blocks = buffer.split('\n\n')
        // Keep the last (potentially incomplete) block as the buffer
        buffer = blocks.pop() ?? ''

        for (const block of blocks) {
          if (block.trim()) {
            this.processBlock(block, callbacks)
          }
        }
      }
    } catch (err) {
      if (signal.aborted) return
      const message =
        err instanceof TypeError
          ? 'Unable to connect to the server. Please check that the backend is running.'
          : err instanceof Error
            ? err.message
            : 'Connection failed'
      callbacks.onError(message)
    }
  }

  private processBlock(block: string, callbacks: ChatStreamCallbacks): void {
    let data = ''

    for (const line of block.split('\n')) {
      if (line.startsWith('data:')) {
        data = line.slice(5).trim()
      }
    }

    if (!data) return

    try {
      callbacks.onEvent(JSON.parse(data) as ChatEvent)
    } catch {
      // Skip malformed events
    }
  }
}

export const chatService = new ChatService()
