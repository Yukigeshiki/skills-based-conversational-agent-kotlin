/** Service for tool discovery via the REST API. */
import { apiClient } from './api'

class ToolService {
  /**
   * Fetches the names of all registered tool beans.
   *
   * @returns A sorted list of tool names.
   */
  async getToolNames(): Promise<string[]> {
    const response = await apiClient.get<string[]>('/api/tools')
    return response.data
  }
}

export const toolService = new ToolService()
