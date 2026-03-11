/** Full skill entity as returned by the backend API. */
export interface Skill {
  id: string
  name: string
  description: string
  systemPrompt: string
  responseTemplate: string | null
  isProtected: boolean
  toolNames: string[]
  createdAt: string
  updatedAt: string
}

/** Lightweight skill projection without the system prompt. */
export interface SkillSummary {
  id: string
  name: string
  description: string
  toolNames: string[]
  createdAt: string
  updatedAt: string
}
