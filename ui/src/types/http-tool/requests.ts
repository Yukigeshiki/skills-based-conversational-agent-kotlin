import type { HttpToolParameter } from './entities'

/** Request body for creating a new HTTP tool. */
export interface CreateHttpToolRequest {
  name: string
  description: string
  endpointUrl: string
  httpMethod: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  headers?: Record<string, string>
  parameters?: HttpToolParameter[]
  timeoutSeconds?: number
  maxResponseLength?: number
}

/** Request body for partially updating an existing HTTP tool. All fields are optional. */
export interface UpdateHttpToolRequest {
  name?: string
  description?: string
  endpointUrl?: string
  httpMethod?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  headers?: Record<string, string>
  parameters?: HttpToolParameter[]
  timeoutSeconds?: number
  maxResponseLength?: number
}

/** Request body for testing an HTTP tool with sample arguments. */
export interface TestHttpToolRequest {
  arguments: Record<string, unknown>
}
