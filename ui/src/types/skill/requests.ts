/** Query parameters for filtering and paginating the skills list. */
export interface GetSkillsParams {
  [key: string]: unknown
  search?: string
  tools?: string[]
  page?: number
  size?: number
  sort?: string
}

/** Request body for creating a new skill. */
export interface CreateSkillRequest {
  name: string
  description: string
  systemPrompt: string
  responseTemplate?: string
  toolNames: string[]
}

/** Request body for partially updating an existing skill. All fields are optional. */
export interface UpdateSkillRequest {
  name?: string
  description?: string
  systemPrompt?: string
  responseTemplate?: string
  toolNames?: string[]
}
