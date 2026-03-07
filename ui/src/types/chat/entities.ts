import type { ChatEvent } from './events'

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
