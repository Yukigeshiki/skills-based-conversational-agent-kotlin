/** Request body for creating a new skill. */
export interface CreateSkillRequest {
  name: string
  description: string
  systemPrompt: string
  toolNames: string[]
  planningPrompt?: string
}

/** Request body for partially updating an existing skill. All fields are optional. */
export interface UpdateSkillRequest {
  name?: string
  description?: string
  systemPrompt?: string
  toolNames?: string[]
  planningPrompt?: string
}
