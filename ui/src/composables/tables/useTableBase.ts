/** Composable providing core table state: pagination, sorting, and filter management. */
import { ref, computed, type Ref } from 'vue'
import { useFilterBase } from '@/composables/filters'
import { formatDate } from '@/lib/utils'

/** Configuration for {@link useTableBase}. */
export interface TableBaseOptions {
  /** Column to sort by initially (default `'createdAt'`). */
  defaultSortColumn?: string
  /** Initial sort direction (default `'desc'`). */
  defaultSortDirection?: 'asc' | 'desc'
  /** Initial page size as a string (default `'20'`). */
  defaultPageSize?: string
}

/** Represents a single active filter displayed as a removable chip. */
export interface ActiveFilter {
  /** The filter parameter key. */
  key: string
  /** Human-readable label for the chip. */
  label: string
  /** Display value for the chip. */
  value: string
}

/**
 * Provides reactive pagination, sorting, and filter management for data tables.
 *
 * @param options - Optional defaults for sort column, direction, and page size.
 * @returns Reactive state and handlers for sort, pagination, and filters.
 */
export function useTableBase(options: TableBaseOptions = {}) {
  const {
    defaultSortColumn = 'createdAt',
    defaultSortDirection = 'desc',
    defaultPageSize = '20',
  } = options

  const currentPage = ref(0)
  const pageSize = ref(defaultPageSize)
  const sortColumn = ref(defaultSortColumn)
  const sortDirection = ref<'asc' | 'desc'>(defaultSortDirection)

  const sortString = computed(() => `${sortColumn.value},${sortDirection.value}`)

  /**
   * Toggles sort direction if the same column is clicked, otherwise sorts ascending by the new column.
   *
   * @param column - The column key to sort by.
   * @param onSortChange - Callback invoked after the sort state updates.
   */
  function handleSort(column: string, onSortChange: () => void) {
    if (sortColumn.value === column) {
      sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
    } else {
      sortColumn.value = column
      sortDirection.value = 'asc'
    }
    onSortChange()
  }

  /**
   * Navigates to a specific page.
   *
   * @param page - Zero-based page index.
   * @param onPageChange - Callback invoked after the page updates.
   */
  function goToPage(page: number, onPageChange: () => void) {
    currentPage.value = page
    onPageChange()
  }

  /**
   * Updates the page size and resets to the first page.
   *
   * @param size - The new page size as a string.
   */
  function updatePageSize(size: string) {
    pageSize.value = size
    currentPage.value = 0
  }

  /**
   * Removes a single filter by key, resets to page 0, and triggers a refresh.
   *
   * @param key - The filter key to remove.
   * @param filters - Reactive ref holding the current filter state.
   * @param onFilterChange - Callback invoked after the filter is removed.
   */
  function removeFilter<T extends Record<string, unknown>>(
    key: string,
    filters: Ref<T>,
    onFilterChange: () => void
  ) {
    const { removeFilter: remove } = useFilterBase(filters)
    remove(key)
    currentPage.value = 0
    onFilterChange()
  }

  /**
   * Clears all filters (optionally preserving specified keys), resets to page 0, and triggers a refresh.
   *
   * @param filters - Reactive ref holding the current filter state.
   * @param onFilterChange - Callback invoked after filters are cleared.
   * @param preserveKeys - Optional keys to keep when clearing.
   */
  function clearAllFilters<T extends Record<string, unknown>>(
    filters: Ref<T>,
    onFilterChange: () => void,
    preserveKeys?: string[]
  ) {
    const { clearAllFilters: clearAll } = useFilterBase(filters)
    clearAll(preserveKeys)
    currentPage.value = 0
    onFilterChange()
  }

  /**
   * Applies a new set of filters, resets to page 0, and triggers a refresh.
   *
   * @param newFilters - The new filter values to apply.
   * @param _filters - Reactive ref holding the current filter state (unused directly).
   * @param handleFiltersUpdate - Handler that commits the new filters to state.
   * @param onFilterChange - Callback invoked after filters are applied.
   */
  function applyFilters<T extends Record<string, unknown>>(
    newFilters: T,
    _filters: Ref<T>,
    handleFiltersUpdate: (newFilters: T) => void,
    onFilterChange: () => void
  ) {
    handleFiltersUpdate(newFilters)
    currentPage.value = 0
    onFilterChange()
  }

  return {
    currentPage,
    pageSize,
    sortColumn,
    sortDirection,
    sortString,
    handleSort,
    goToPage,
    updatePageSize,
    removeFilter,
    clearAllFilters,
    applyFilters,
    formatDate,
  }
}
