/** Shared Axios client and SSE helper for communicating with the agent backend. */
import axios from 'axios'

const API_TIMEOUT = 30000

const API_URL = import.meta.env.VITE_AGENT_SERVICE_URL || 'http://localhost:9090'

export const apiClient = axios.create({
  baseURL: API_URL,
  timeout: API_TIMEOUT,
  headers: {
    'Content-Type': 'application/json',
  },
})

/**
 * Opens a native EventSource connection to the given backend path.
 *
 * @param path - API path to connect to (appended to the base URL).
 * @param options - Optional callbacks for open, message, and error events.
 * @returns The EventSource instance for the caller to close when done.
 */
export function createSSEConnection(path: string, options?: { onMessage?: (data: string) => void; onError?: (err: Event) => void; onOpen?: () => void }) {
  const url = `${API_URL}${path}`
  const eventSource = new EventSource(url)

  if (options?.onOpen) eventSource.onopen = options.onOpen
  if (options?.onMessage) eventSource.onmessage = (event) => options.onMessage!(event.data)
  if (options?.onError) eventSource.onerror = options.onError

  return eventSource
}

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
