/**
 * Shared Axios client for communicating with the agent backend.
 *
 * Transforms undefined values to null for proper JSON serialization so the
 * backend can distinguish "field not provided" from "field should be cleared".
 */
import axios from 'axios'

const API_TIMEOUT = 30000

const API_URL = import.meta.env.VITE_AGENT_SERVICE_URL || 'http://localhost:9090'

/**
 * Recursively transforms undefined values to null in an object.
 *
 * JavaScript/Vue idiomatically uses undefined for "no value", but JSON.stringify
 * strips undefined values entirely. The backend needs explicit null to know when
 * a field should be cleared.
 */
function transformUndefinedToNull<T>(obj: T): T {
  if (obj === undefined) {
    return null as T
  }

  if (obj === null || typeof obj !== 'object') {
    return obj
  }

  if (Array.isArray(obj)) {
    return obj.map(item => transformUndefinedToNull(item)) as T
  }

  const result: Record<string, unknown> = {}
  for (const key of Object.keys(obj)) {
    const value = (obj as Record<string, unknown>)[key]
    result[key] = value === undefined ? null : transformUndefinedToNull(value)
  }
  return result as T
}

export const apiClient = axios.create({
  baseURL: API_URL,
  timeout: API_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use(
  (config) => {
    if (config.data && typeof config.data === 'object' && !(config.data instanceof FormData)) {
      config.data = transformUndefinedToNull(config.data)
    }
    return config
  },
  (error) => Promise.reject(error),
)

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response) {
      const { data, status } = error.response
      const errorMessage = data?.detail || data?.message || data?.title || 'An unexpected error occurred'
      console.error(`[API] Request failed with status ${status}:`, errorMessage)
      const enhancedError = new Error(errorMessage) as Error & { status: number }
      enhancedError.status = status
      return Promise.reject(enhancedError)
    }

    if (error.request) {
      console.error('[API] No response from server')
      return Promise.reject(new Error('No response from server. Please check your connection.'))
    }

    console.error('[API] Request setup failed:', error)
    return Promise.reject(new Error('Request failed. Please try again.'))
  },
)
