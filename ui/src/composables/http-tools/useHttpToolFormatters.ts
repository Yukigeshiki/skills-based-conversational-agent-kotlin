/** Composable providing display formatters for HTTP tool fields (descriptions, parameters, dates). */
import { formatDate } from '@/lib/utils'

export function useHttpToolFormatters() {
  /** Truncates a description to the given max length, appending '...' if trimmed. */
  function truncateDescription(description: string, maxLength = 100): string {
    if (description.length <= maxLength) return description
    return description.substring(0, maxLength) + '...'
  }

  /** Formats a parameter count as a human-readable string with correct pluralisation. */
  function formatParameterCount(params: unknown[]): string {
    return `${params.length} param${params.length !== 1 ? 's' : ''}`
  }

  return { truncateDescription, formatParameterCount, formatDate }
}
