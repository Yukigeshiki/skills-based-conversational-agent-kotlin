/** Reusable composable for managing active table filters with remove/clear and date range support. */
import { type Ref } from 'vue'
import type { ActiveFilter } from '@/components/common'

/** Optional date-related filter fields supported by {@link useFilterBase}. */
export interface DateFilters {
  date?: string
  startDate?: string
  endDate?: string
}

/**
 * Provides generic filter management utilities for table filter objects.
 *
 * @param filters - A reactive ref holding the current filter state.
 * @returns An object containing {@link removeFilter}, {@link clearAllFilters}, and {@link buildDateFilterEntries}.
 */
export function useFilterBase<T extends object>(filters: Ref<T>) {
  /**
   * Removes a single filter by key. Handles the special `dateRange` key
   * by clearing both `startDate` and `endDate`.
   *
   * @param key - The filter key to remove.
   */
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

  /**
   * Clears all filters, optionally preserving specified keys.
   *
   * @param preserveKeys - Keys to keep when clearing (e.g. default sort).
   */
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

  /**
   * Builds display entries for active date filters (single date or date range).
   *
   * @returns An array of {@link ActiveFilter} entries for the UI filter chips.
   */
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
