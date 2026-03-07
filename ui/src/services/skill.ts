import { apiClient } from './api'
import type { Skill, CreateSkillRequest, UpdateSkillRequest } from '@/types/skill'

class SkillService {
  async getAllSkills(): Promise<Skill[]> {
    const response = await apiClient.get<Skill[]>('/api/skills')
    return response.data
  }

  async getSkillById(id: string): Promise<Skill> {
    const response = await apiClient.get<Skill>(`/api/skills/${id}`)
    return response.data
  }

  async createSkill(data: CreateSkillRequest): Promise<Skill> {
    const response = await apiClient.post<Skill>('/api/skills', data)
    return response.data
  }

  async updateSkill(id: string, data: UpdateSkillRequest): Promise<Skill> {
    const response = await apiClient.patch<Skill>(`/api/skills/${id}`, data)
    return response.data
  }

  async deleteSkill(id: string): Promise<void> {
    await apiClient.delete(`/api/skills/${id}`)
  }
}

export const skillService = new SkillService()
