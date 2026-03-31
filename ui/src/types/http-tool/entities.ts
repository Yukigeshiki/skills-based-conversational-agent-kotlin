/** Full HTTP tool entity as returned by the backend API. */
export interface HttpTool {
  id: string
  name: string
  description: string
  endpointUrl: string
  httpMethod: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  headers: Record<string, string>
  parameters: HttpToolParameter[]
  timeoutSeconds: number
  maxResponseLength: number
  createdAt: string
  updatedAt: string
}

/** A single parameter definition for an HTTP tool. */
export interface HttpToolParameter {
  name: string
  type: 'string' | 'integer' | 'number' | 'boolean'
  description: string
  required: boolean
}

/** Form data shape used by HTTP tool create and edit dialogs. */
export interface HttpToolFormData {
  name: string
  description: string
  endpointUrl: string
  httpMethod: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  headers: Record<string, string>
  parameters: HttpToolParameter[]
  timeoutSeconds: number
  maxResponseLength: number
}

/** Result of testing an HTTP tool with sample arguments. */
export interface HttpToolTestResult {
  statusCode: number
  body: string
  durationMs: number
  truncated: boolean
}

/** Filter criteria for the HTTP tools table. */
export interface HttpToolFilters {
  [key: string]: unknown
  search?: string
}
