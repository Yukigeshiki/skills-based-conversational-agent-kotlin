/** Composable for managing table filter state, dialog-driven filter application, and data fetching. */
import { ref } from 'vue'

/** Configuration for {@link useTableFilters}. */
export interface UseTableFiltersOptions {
  /** When true (default), empty string values are stripped before applying filters. */
  cleanFilters?: boolean
}

/**
 * Manages filter state, pending dialog filters, loading/error state, and data fetching.
 *
 * @param options - Optional configuration for filter cleaning behaviour.
 * @returns Reactive filter state, dialog controls, and an {@link executeFetch} method.
 */
export function useTableFilters<TParams = Record<string, unknown>, TResponse = unknown>(
  options?: UseTableFiltersOptions
) {
  const filters = ref<TParams>({} as TParams)
  const pendingFilters = ref<TParams | undefined>(undefined)
  const filterError = ref<string | undefined>(undefined)
  const filterDialogOpen = ref(false)
  const isApplyingFiltersFromDialog = ref(false)
  const loading = ref(false)
  const error = ref<string | undefined>(undefined)
  const data = ref<TResponse | undefined>(undefined)

  /**
   * Strips empty or whitespace-only string values from the filter object.
   *
   * @param filters - The raw filter object to clean.
   * @returns A new object with empty string values removed.
   */
  function cleanEmptyFilters(filters: TParams): TParams {
    const cleaned = {} as Partial<TParams>
    Object.entries(filters as Record<string, unknown>).forEach(([key, value]) => {
      if (value && typeof value === 'string' && value.trim() !== '') {
        cleaned[key as keyof TParams] = value as TParams[keyof TParams]
      } else if (value && typeof value !== 'string') {
        cleaned[key as keyof TParams] = value as TParams[keyof TParams]
      }
    })
    return cleaned as TParams
  }

  /**
   * Stages new filters from a dialog submission for the next fetch.
   *
   * @param newFilters - The filter values submitted from the dialog.
   */
  function handleFiltersUpdate(newFilters: TParams) {
    const filtersToUse = options?.cleanFilters !== false
      ? cleanEmptyFilters(newFilters)
      : newFilters

    isApplyingFiltersFromDialog.value = true
    pendingFilters.value = filtersToUse as TParams
  }

  /** Clears the current filter error message. */
  function clearFilterError() {
    filterError.value = undefined
  }

  /**
   * Executes a data fetch using either pending dialog filters or the current active filters.
   * On success from a dialog submission, commits the pending filters and closes the dialog.
   *
   * @param fetchFn - Async function that fetches data given the filter parameters.
   */
  async function executeFetch(
    fetchFn: (params: TParams) => Promise<TResponse>
  ): Promise<void> {
    const isFromDialog = isApplyingFiltersFromDialog.value
    const filtersToUse = isFromDialog && pendingFilters.value
      ? pendingFilters.value
      : filters.value

    if (!isFromDialog) {
      loading.value = true
    }
    error.value = undefined

    try {
      data.value = await fetchFn(filtersToUse)

      if (isFromDialog && pendingFilters.value) {
        filters.value = { ...pendingFilters.value }
        pendingFilters.value = undefined
      }

      filterError.value = undefined
      if (isFromDialog) {
        filterDialogOpen.value = false
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'An unexpected error occurred'
      if (isFromDialog) {
        error.value = undefined
        filterError.value = message
        pendingFilters.value = undefined
      } else {
        error.value = message
        filterError.value = undefined
      }
    } finally {
      if (!isFromDialog) {
        loading.value = false
      }
      if (isFromDialog) {
        isApplyingFiltersFromDialog.value = false
      }
    }
  }

  return {
    filters,
    pendingFilters,
    filterError,
    filterDialogOpen,
    isApplyingFiltersFromDialog,
    loading,
    error,
    data,
    handleFiltersUpdate,
    clearFilterError,
    executeFetch,
  }
}
