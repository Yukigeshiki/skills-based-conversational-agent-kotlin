export interface PlanStep {
  stepNumber: number
  description: string
  expectedTools: string[]
}

export interface TaskPlan {
  steps: PlanStep[]
  reasoning: string
}

export type PlanStepStatus = 'COMPLETED' | 'FAILED'

export interface ConversationStartedEvent {
  type: 'conversation_started'
  conversationId: string
  timestamp: string
}

export interface SkillMatchedEvent {
  type: 'skill_matched'
  skillName: string
  timestamp: string
}

export interface PlanCreatedEvent {
  type: 'plan_created'
  plan: TaskPlan
  timestamp: string
}

export interface PlanStepStartedEvent {
  type: 'plan_step_started'
  stepNumber: number
  description: string
  timestamp: string
}

export interface PlanStepCompletedEvent {
  type: 'plan_step_completed'
  stepNumber: number
  status: PlanStepStatus
  response: string
  timestamp: string
}

export interface IterationStartedEvent {
  type: 'iteration_started'
  iterationNumber: number
  timestamp: string
}

export interface ThoughtEvent {
  type: 'thought'
  iterationNumber: number
  thought: string
  timestamp: string
}

export interface ToolCallStartedEvent {
  type: 'tool_call_started'
  iterationNumber: number
  toolName: string
  arguments: string
  timestamp: string
}

export interface ToolCallCompletedEvent {
  type: 'tool_call_completed'
  iterationNumber: number
  toolName: string
  result: string
  error: boolean
  timestamp: string
}

export interface FinalResponseEvent {
  type: 'final_response'
  response: string
  skill: string | null
  timestamp: string
}

export interface WarningEvent {
  type: 'warning'
  message: string
  timestamp: string
}

export interface ErrorEvent {
  type: 'error'
  message: string
  timestamp: string
}

export type ChatEvent =
  | ConversationStartedEvent
  | SkillMatchedEvent
  | PlanCreatedEvent
  | PlanStepStartedEvent
  | PlanStepCompletedEvent
  | IterationStartedEvent
  | ThoughtEvent
  | ToolCallStartedEvent
  | ToolCallCompletedEvent
  | FinalResponseEvent
  | WarningEvent
  | ErrorEvent
