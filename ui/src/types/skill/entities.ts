export interface Skill {
  id: string
  name: string
  description: string
  systemPrompt: string
  toolNames: string[]
  planningPrompt: string | null
  createdAt: string
  updatedAt: string
}

export interface SkillSummary {
  id: string
  name: string
  description: string
  toolNames: string[]
  createdAt: string
  updatedAt: string
}
