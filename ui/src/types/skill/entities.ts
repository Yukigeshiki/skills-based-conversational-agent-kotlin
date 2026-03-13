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

/** Form data shape used by skill create and edit dialogs. */
export interface SkillFormData {
  name: string
  description: string
  systemPrompt: string
  responseTemplate: string
  toolNames: string[]
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
