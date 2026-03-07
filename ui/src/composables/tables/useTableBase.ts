import { ref, computed, type Ref } from 'vue'
import { useFilterBase } from '@/composables/filters'
import { formatDate } from '@/lib/utils'

export interface TableBaseOptions {
  defaultSortColumn?: string
  defaultSortDirection?: 'asc' | 'desc'
  defaultPageSize?: string
}

export interface ActiveFilter {
  key: string
  label: string
  value: string
}

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

  function handleSort(column: string, onSortChange: () => void) {
    if (sortColumn.value === column) {
      sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc'
    } else {
      sortColumn.value = column
      sortDirection.value = 'asc'
    }
    onSortChange()
  }

  function goToPage(page: number, onPageChange: () => void) {
    currentPage.value = page
    onPageChange()
  }

  function updatePageSize(size: string) {
    pageSize.value = size
    currentPage.value = 0
  }

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
