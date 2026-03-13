/**
 * Chat service for streaming agent interactions via SSE and fetching conversation history.
 *
 * Uses the fetch API for SSE streaming (to support POST with a request body)
 * and Axios for the REST history endpoint.
 */
import type { ChatEvent, ConversationHistoryMessage } from '@/types/chat'
import { apiClient } from './api'

const API_URL = import.meta.env.VITE_AGENT_SERVICE_URL || 'http://localhost:9090'

/** Callbacks invoked during an SSE chat stream. */
export interface ChatStreamCallbacks {
  /** Called for each parsed agent event received from the stream. */
  onEvent: (event: ChatEvent) => void
  /** Called when the stream encounters an error or the server returns a non-OK status. */
  onError: (error: string) => void
  /** Called when the stream ends successfully. */
  onComplete: () => void
}

class ChatService {
  /**
   * Sends a chat message and streams the response via SSE.
   *
   * @param message - The user's message text.
   * @param callbacks - Handlers for stream events, errors, and completion.
   * @param conversationId - Optional existing conversation ID to continue.
   * @returns An AbortController that can be used to cancel the stream.
   */
  sendMessage(
    message: string,
    callbacks: ChatStreamCallbacks,
    conversationId?: string,
  ): AbortController {
    const controller = new AbortController()

    this.streamResponse(message, callbacks, controller.signal, conversationId).catch((err) =>
      callbacks.onError(err instanceof Error ? err.message : 'Unexpected error'),
    )

    return controller
  }

  /**
   * Fetches the full conversation history for a given conversation ID.
   *
   * @param conversationId - The UUID of the conversation to retrieve.
   * @returns The ordered list of messages in the conversation.
   */
  async getHistory(conversationId: string): Promise<ConversationHistoryMessage[]> {
    const response = await apiClient.get<ConversationHistoryMessage[]>(
      `/api/chat/${conversationId}/history`,
    )
    return response.data
  }

  /**
   * Performs the actual fetch-based SSE streaming, reading chunks from the
   * response body and splitting them into SSE blocks for parsing.
   *
   * @param message - The user's message text.
   * @param callbacks - Handlers for stream events, errors, and completion.
   * @param signal - AbortSignal to cancel the request.
   * @param conversationId - Optional conversation ID to continue.
   */
  private async streamResponse(
    message: string,
    callbacks: ChatStreamCallbacks,
    signal: AbortSignal,
    conversationId?: string,
  ): Promise<void> {
    try {
      const body: Record<string, string> = { message }
      if (conversationId) {
        body.conversationId = conversationId
      }

      const response = await fetch(`${API_URL}/api/chat`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
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

  /**
   * Extracts the `data:` line from an SSE block and parses it as a ChatEvent.
   *
   * @param block - A single SSE block (text between double newlines).
   * @param callbacks - Handlers to invoke with the parsed event.
   */
  private processBlock(block: string, callbacks: ChatStreamCallbacks): void {
    let data = ''
    let eventType = ''

    for (const line of block.split('\n')) {
      if (line.startsWith('data:')) {
        data = line.slice(5).trim()
      } else if (line.startsWith('event:')) {
        eventType = line.slice(6).trim()
      }
    }

    if (!data) return

    // Skip heartbeat events — they exist only to keep the connection alive
    if (eventType === 'heartbeat') return

    try {
      callbacks.onEvent(JSON.parse(data) as ChatEvent)
    } catch {
      // Skip malformed events
    }
  }
}

export const chatService = new ChatService()
