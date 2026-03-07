import { ref, shallowRef } from 'vue'

export interface UseExpandableRowsOptions {
  accordion?: boolean
}

export function useExpandableRows<T>(options: UseExpandableRowsOptions = {}) {
  const { accordion = false } = options

  const expandedRows = shallowRef(new Map<string, T | undefined>())
  const loadingExpandedRow = ref<string | undefined>(undefined)
  const hoveredRow = ref<string | undefined>(undefined)
  const rowErrors = shallowRef(new Map<string, string>())

  async function toggleRowExpansion(
    id: string,
    event: Event,
    fetchFn: (id: string) => Promise<T>
  ) {
    event.stopPropagation()

    if (expandedRows.value.has(id)) {
      const newMap = new Map(expandedRows.value)
      newMap.delete(id)
      expandedRows.value = newMap
    } else {
      loadingExpandedRow.value = id
      try {
        const expandedData = await fetchFn(id)
        const newMap = accordion ? new Map<string, T | undefined>() : new Map(expandedRows.value)
        newMap.set(id, expandedData)
        expandedRows.value = newMap
      } catch (err) {
        console.error('Failed to fetch expanded row details', err)
        const errorMsg = err instanceof Error ? err.message : 'Failed to load details'
        const newErrors = new Map(rowErrors.value)
        newErrors.set(id, errorMsg)
        rowErrors.value = newErrors
        const newMap = accordion ? new Map<string, T | undefined>() : new Map(expandedRows.value)
        newMap.set(id, undefined)
        expandedRows.value = newMap
      } finally {
        loadingExpandedRow.value = undefined
      }
    }
  }

  function collapseAll() {
    expandedRows.value = new Map()
  }

  function setHoveredRow(id: string | null | undefined) {
    hoveredRow.value = id ?? undefined
  }

  function isRowExpanded(id: string): boolean {
    return expandedRows.value.has(id)
  }

  function isRowLoading(id: string): boolean {
    return loadingExpandedRow.value === id
  }

  function isRowHovered(id: string): boolean {
    return hoveredRow.value === id
  }

  function getRowError(id: string): string | undefined {
    return rowErrors.value.get(id)
  }

  return {
    expandedRows,
    loadingExpandedRow,
    hoveredRow,
    toggleRowExpansion,
    collapseAll,
    setHoveredRow,
    isRowExpanded,
    isRowLoading,
    isRowHovered,
    rowErrors,
    getRowError,
  }
}
