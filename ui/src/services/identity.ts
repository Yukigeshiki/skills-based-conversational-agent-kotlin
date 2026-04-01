/** Service for reading and updating the identity configuration via the REST API. */
import { apiClient } from './api'
import type { Identity, UpdateIdentityRequest } from '@/types/identity'

class IdentityService {
  /** Fetches the singleton identity configuration. */
  async getIdentity(): Promise<Identity> {
    const response = await apiClient.get<Identity>('/api/identity')
    return response.data
  }

  /** Updates the identity system prompt. */
  async updateIdentity(data: UpdateIdentityRequest): Promise<Identity> {
    const response = await apiClient.put<Identity>('/api/identity', data)
    return response.data
  }
}

export const identityService = new IdentityService()
