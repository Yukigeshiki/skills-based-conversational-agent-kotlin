/** CRUD service for managing skill references via the REST API. */
import { apiClient } from './api'
import type { SkillReference, CreateSkillReferenceRequest, UpdateSkillReferenceRequest } from '@/types/reference'

class ReferenceService {
  /**
   * Fetches all references for a skill.
   *
   * @param skillId - The skill's unique identifier.
   * @returns A list of skill references.
   */
  async getReferences(skillId: string): Promise<SkillReference[]> {
    const response = await apiClient.get<SkillReference[]>(
      `/api/skills/${skillId}/references`,
    )
    return response.data
  }

  /**
   * Fetches a single reference by its ID.
   *
   * @param skillId - The skill's unique identifier.
   * @param referenceId - The reference's unique identifier.
   * @returns The full reference entity.
   */
  async getReferenceById(skillId: string, referenceId: string): Promise<SkillReference> {
    const response = await apiClient.get<SkillReference>(
      `/api/skills/${skillId}/references/${referenceId}`,
    )
    return response.data
  }

  /**
   * Creates a new reference for a skill.
   *
   * @param skillId - The skill's unique identifier.
   * @param data - The reference creation payload.
   * @returns The newly created reference.
   */
  async createReference(skillId: string, data: CreateSkillReferenceRequest): Promise<SkillReference> {
    const response = await apiClient.post<SkillReference>(
      `/api/skills/${skillId}/references`,
      data,
    )
    return response.data
  }

  /**
   * Partially updates an existing reference.
   *
   * @param skillId - The skill's unique identifier.
   * @param referenceId - The reference's unique identifier.
   * @param data - The fields to update.
   * @returns The updated reference.
   */
  async updateReference(
    skillId: string,
    referenceId: string,
    data: UpdateSkillReferenceRequest,
  ): Promise<SkillReference> {
    const response = await apiClient.patch<SkillReference>(
      `/api/skills/${skillId}/references/${referenceId}`,
      data,
    )
    return response.data
  }

  /**
   * Deletes a reference by its ID.
   *
   * @param skillId - The skill's unique identifier.
   * @param referenceId - The reference's unique identifier.
   */
  async deleteReference(skillId: string, referenceId: string): Promise<void> {
    await apiClient.delete(`/api/skills/${skillId}/references/${referenceId}`)
  }
}

export const referenceService = new ReferenceService()
