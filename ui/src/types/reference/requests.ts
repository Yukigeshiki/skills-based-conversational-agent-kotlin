/** Request body for creating a new skill reference. */
export interface CreateSkillReferenceRequest {
  name: string
  content: string
}

/** Request body for partially updating an existing skill reference. All fields are optional. */
export interface UpdateSkillReferenceRequest {
  name?: string
  content?: string
}
