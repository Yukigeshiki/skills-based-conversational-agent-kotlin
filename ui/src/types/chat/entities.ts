import type { ChatEvent } from '@/types'

export type MessageRole = 'user' | 'assistant'

export type MessageStatus = 'streaming' | 'complete' | 'error'

export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  activities: ChatEvent[]
  status: MessageStatus
  error?: string
}

export interface ConversationHistoryMessage {
  role: string
  content: string
  activities: ChatEvent[]
  timestamp: string
}
