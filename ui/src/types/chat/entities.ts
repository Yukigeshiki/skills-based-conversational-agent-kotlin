/** Chat message types used by the UI to track message state and conversation history. */
import type { ChatEvent } from '@/types'

export type MessageRole = 'user' | 'assistant'

export type MessageStatus = 'streaming' | 'complete' | 'error'

/** A chat message in the UI, including streaming state and associated activity events. */
export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  activities: ChatEvent[]
  status: MessageStatus
  error?: string
  streamingText?: string
}

/** A message as returned by the conversation history REST endpoint. */
export interface ConversationHistoryMessage {
  role: string
  content: string
  activities: ChatEvent[]
  timestamp: string
}
