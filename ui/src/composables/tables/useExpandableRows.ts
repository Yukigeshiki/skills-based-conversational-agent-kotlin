/** Composable for managing expandable table rows with lazy data loading, accordion mode, and error tracking. */
import { ref, shallowRef } from 'vue'

/** Configuration for {@link useExpandableRows}. */
export interface UseExpandableRowsOptions {
  /** When true, only one row can be expanded at a time. */
  accordion?: boolean
}

/**
 * Manages expandable row state including loading, hover tracking, and per-row errors.
 *
 * @param options - Optional configuration for accordion behaviour.
 * @returns Reactive state and helpers for row expansion, hover, and error tracking.
 */
export function useExpandableRows<T>(options: UseExpandableRowsOptions = {}) {
  const { accordion = false } = options

  const expandedRows = shallowRef(new Map<string, T | undefined>())
  const loadingExpandedRow = ref<string | undefined>(undefined)
  const hoveredRow = ref<string | undefined>(undefined)
  const rowErrors = shallowRef(new Map<string, string>())

  /**
   * Toggles a row between expanded and collapsed. Fetches data on first expand.
   *
   * @param id - The unique row identifier.
   * @param event - The DOM event (propagation is stopped).
   * @param fetchFn - Async function to load the expanded row data.
   */
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

  /** Collapses all expanded rows. */
  function collapseAll() {
    expandedRows.value = new Map()
  }

  /**
   * Sets the currently hovered row.
   *
   * @param id - The row ID to mark as hovered, or null/undefined to clear.
   */
  function setHoveredRow(id: string | null | undefined) {
    hoveredRow.value = id ?? undefined
  }

  /**
   * Checks whether a row is currently expanded.
   *
   * @param id - The row ID to check.
   * @returns True if the row is expanded.
   */
  function isRowExpanded(id: string): boolean {
    return expandedRows.value.has(id)
  }

  /**
   * Checks whether a row is currently loading its expanded data.
   *
   * @param id - The row ID to check.
   * @returns True if the row is loading.
   */
  function isRowLoading(id: string): boolean {
    return loadingExpandedRow.value === id
  }

  /**
   * Checks whether a row is currently hovered.
   *
   * @param id - The row ID to check.
   * @returns True if the row is hovered.
   */
  function isRowHovered(id: string): boolean {
    return hoveredRow.value === id
  }

  /**
   * Returns the error message for a row, if any.
   *
   * @param id - The row ID to look up.
   * @returns The error message string, or undefined if no error.
   */
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
