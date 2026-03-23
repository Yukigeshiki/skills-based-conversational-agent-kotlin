/**
 * SSE event types emitted by the agent during a chat interaction.
 *
 * Each interface corresponds to a named SSE event and maps 1:1 with
 * the backend AgentEvent sealed class subtypes.
 */

export interface PlanStep {
  stepNumber: number
  description: string
  expectedTools: string[]
  skillName: string | null
  dependsOn: number[]
}

export interface TaskPlan {
  steps: PlanStep[]
  reasoning: string
}

export type PlanStepStatus = 'COMPLETED' | 'FAILED' | 'SKIPPED'

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
  skillName: string | null
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

export interface SkillReroutedEvent {
  type: 'skill_rerouted'
  fromSkill: string
  toSkill: string
  reason: string
  timestamp: string
}

export interface SkillHandoffStartedEvent {
  type: 'skill_handoff_started'
  fromSkill: string
  toSkill: string
  request: string
  delegationDepth: number
  timestamp: string
}

export interface SkillHandoffCompletedEvent {
  type: 'skill_handoff_completed'
  fromSkill: string
  toSkill: string
  delegationDepth: number
  success: boolean
  timestamp: string
}

export interface PendingToolCall {
  toolName: string
  arguments: string
}

export type ApprovalDecision = 'APPROVED' | 'REJECTED'

export interface ApprovalRequiredEvent {
  type: 'approval_required'
  approvalId: string
  toolCalls: PendingToolCall[]
  skillName: string
  timestamp: string
}

export interface ApprovalResolvedEvent {
  type: 'approval_resolved'
  approvalId: string
  decision: ApprovalDecision
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

export interface ResponseChunkEvent {
  type: 'response_chunk'
  chunk: string
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
  | SkillReroutedEvent
  | SkillHandoffStartedEvent
  | SkillHandoffCompletedEvent
  | ApprovalRequiredEvent
  | ApprovalResolvedEvent
  | WarningEvent
  | ErrorEvent
  | ResponseChunkEvent
