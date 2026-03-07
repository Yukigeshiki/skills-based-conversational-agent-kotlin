/** Composable providing display formatters for skill fields (tool names, descriptions, dates). */
import { formatDate } from '@/lib/utils'

export function useSkillFormatters() {
  /**
   * Joins tool names into a comma-separated string.
   *
   * @param toolNames - The list of tool name strings.
   * @returns A comma-separated display string.
   */
  function formatToolNames(toolNames: string[]): string {
    return toolNames.join(', ')
  }

  /**
   * Truncates a description to the given max length, appending '...' if trimmed.
   *
   * @param description - The full description text.
   * @param maxLength - Maximum character count before truncation (default 100).
   * @returns The original or truncated description.
   */
  function truncateDescription(description: string, maxLength = 100): string {
    if (description.length <= maxLength) return description
    return description.substring(0, maxLength) + '...'
  }

  return {
    formatToolNames,
    truncateDescription,
    formatDate,
  }
}
