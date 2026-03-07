import { formatDate } from '@/lib/utils'

export function useSkillFormatters() {
  function formatToolNames(toolNames: string[]): string {
    return toolNames.join(', ')
  }

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
