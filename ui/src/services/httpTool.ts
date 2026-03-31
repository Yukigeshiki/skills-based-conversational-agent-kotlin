/** CRUD service for managing HTTP tools via the REST API. */
import { apiClient } from './api'
import type {
  HttpTool,
  CreateHttpToolRequest,
  UpdateHttpToolRequest,
  TestHttpToolRequest,
  HttpToolTestResult,
} from '@/types/http-tool'

class HttpToolService {
  /** Fetches all HTTP tools. */
  async getAllHttpTools(): Promise<HttpTool[]> {
    const response = await apiClient.get<HttpTool[]>('/api/http-tools')
    return response.data
  }

  /** Fetches a single HTTP tool by ID. */
  async getHttpToolById(id: string): Promise<HttpTool> {
    const response = await apiClient.get<HttpTool>(`/api/http-tools/${id}`)
    return response.data
  }

  /** Creates a new HTTP tool. */
  async createHttpTool(data: CreateHttpToolRequest): Promise<HttpTool> {
    const response = await apiClient.post<HttpTool>('/api/http-tools', data)
    return response.data
  }

  /** Partially updates an existing HTTP tool. */
  async updateHttpTool(id: string, data: UpdateHttpToolRequest): Promise<HttpTool> {
    const response = await apiClient.patch<HttpTool>(`/api/http-tools/${id}`, data)
    return response.data
  }

  /** Deletes an HTTP tool by ID. */
  async deleteHttpTool(id: string): Promise<void> {
    await apiClient.delete(`/api/http-tools/${id}`)
  }

  /** Tests an HTTP tool by executing it with sample arguments. */
  async testHttpTool(id: string, data: TestHttpToolRequest): Promise<HttpToolTestResult> {
    const response = await apiClient.post<HttpToolTestResult>(
      `/api/http-tools/${id}/test`,
      data,
    )
    return response.data
  }
}

export const httpToolService = new HttpToolService()
