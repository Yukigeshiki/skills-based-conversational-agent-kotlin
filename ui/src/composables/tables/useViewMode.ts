import { ref, computed } from 'vue'

export type ViewMode = 'table' | 'card'

export interface UseViewModeOptions {
  tableId: string
  defaultMode?: ViewMode
}

export function useViewMode(options: UseViewModeOptions) {
  const { tableId, defaultMode = 'card' } = options
  const storageKey = `viewMode:${tableId}`

  function readStoredMode(): ViewMode {
    try {
      const stored = localStorage.getItem(storageKey)
      if (stored === 'table' || stored === 'card') return stored
    } catch {
      // localStorage unavailable
    }
    return defaultMode
  }

  const viewMode = ref<ViewMode>(readStoredMode())

  const isCardView = computed(() => viewMode.value === 'card')
  const isTableView = computed(() => viewMode.value === 'table')

  function toggleView() {
    viewMode.value = viewMode.value === 'card' ? 'table' : 'card'
    try {
      localStorage.setItem(storageKey, viewMode.value)
    } catch {
      // localStorage unavailable
    }
  }

  function setViewMode(mode: ViewMode) {
    viewMode.value = mode
    try {
      localStorage.setItem(storageKey, mode)
    } catch {
      // localStorage unavailable
    }
  }

  return {
    viewMode,
    isCardView,
    isTableView,
    toggleView,
    setViewMode,
  }
}
