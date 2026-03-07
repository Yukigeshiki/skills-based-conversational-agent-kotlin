export interface CreateSkillRequest {
  name: string
  description: string
  systemPrompt: string
  toolNames: string[]
  planningPrompt?: string
}

export interface UpdateSkillRequest {
  name?: string
  description?: string
  systemPrompt?: string
  toolNames?: string[]
  planningPrompt?: string
}
