import { type Ref } from 'vue'
import type { ActiveFilter } from '@/components/common'

export interface DateFilters {
  date?: string
  startDate?: string
  endDate?: string
}

export function useFilterBase<T extends object>(filters: Ref<T>) {
  function removeFilter(key: string) {
    const newFilters = { ...filters.value } as Record<string, unknown>

    if (key === 'dateRange') {
      delete newFilters.startDate
      delete newFilters.endDate
    } else {
      delete newFilters[key]
    }

    filters.value = newFilters as T
  }

  function clearAllFilters(preserveKeys?: string[]) {
    if (preserveKeys?.length) {
      const preserved = {} as Record<string, unknown>
      for (const key of preserveKeys) {
        if (key in (filters.value as Record<string, unknown>)) {
          preserved[key] = (filters.value as Record<string, unknown>)[key]
        }
      }
      filters.value = preserved as T
    } else {
      filters.value = {} as T
    }
  }

  function buildDateFilterEntries(): ActiveFilter[] {
    const f = filters.value as Record<string, unknown>
    const entries: ActiveFilter[] = []

    if (f.date) {
      entries.push({ key: 'date', label: 'Date', value: f.date as string })
    } else if (f.startDate && f.endDate) {
      entries.push({ key: 'dateRange', label: 'Date Range', value: `${f.startDate} - ${f.endDate}` })
    } else if (f.startDate) {
      entries.push({ key: 'dateRange', label: 'Start Date', value: f.startDate as string })
    } else if (f.endDate) {
      entries.push({ key: 'dateRange', label: 'End Date', value: f.endDate as string })
    }

    return entries
  }

  return {
    removeFilter,
    clearAllFilters,
    buildDateFilterEntries,
  }
}
