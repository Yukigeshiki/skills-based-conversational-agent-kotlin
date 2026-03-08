/** CRUD service for managing agent skills via the REST API. */
import { apiClient } from './api'
import type { Skill, GetSkillsParams, CreateSkillRequest, UpdateSkillRequest, SkillSummary } from '@/types/skill'
import type { PagedResponse } from '@/types/common'

class SkillService {
  private toolNamesCache: string[] | null = null
  /**
   * Fetches a paginated list of skills from the backend with optional filtering.
   *
   * @param params - Optional filter and pagination parameters.
   * @returns A paged response of skill summaries.
   */
  async getAllSkills(params?: GetSkillsParams): Promise<PagedResponse<SkillSummary>> {
    const response = await apiClient.get<PagedResponse<SkillSummary>>('/api/skills', {
      params,
      paramsSerializer: { indexes: null },
    })
    return response.data
  }

  /**
   * Fetches the names of all registered tool beans.
   *
   * @returns A sorted list of tool names.
   */
  async getToolNames(): Promise<string[]> {
    if (this.toolNamesCache) return this.toolNamesCache
    const response = await apiClient.get<string[]>('/api/tools')
    this.toolNamesCache = response.data
    return response.data
  }

  /**
   * Fetches a single skill by its ID, including the full system prompt and planning prompt.
   *
   * @param id - The skill's unique identifier.
   * @returns The full skill entity.
   */
  async getSkillById(id: string): Promise<Skill> {
    const response = await apiClient.get<Skill>(`/api/skills/${id}`)
    return response.data
  }

  /**
   * Creates a new skill and returns the persisted entity.
   *
   * @param data - The skill creation payload.
   * @returns The newly created skill.
   */
  async createSkill(data: CreateSkillRequest): Promise<Skill> {
    const response = await apiClient.post<Skill>('/api/skills', data)
    return response.data
  }

  /**
   * Partially updates an existing skill and returns the updated entity.
   *
   * @param id - The skill's unique identifier.
   * @param data - The fields to update.
   * @returns The updated skill.
   */
  async updateSkill(id: string, data: UpdateSkillRequest): Promise<Skill> {
    const response = await apiClient.patch<Skill>(`/api/skills/${id}`, data)
    return response.data
  }

  /**
   * Deletes a skill by its ID.
   *
   * @param id - The skill's unique identifier.
   */
  async deleteSkill(id: string): Promise<void> {
    await apiClient.delete(`/api/skills/${id}`)
  }
}

export const skillService = new SkillService()
