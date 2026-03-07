/** Composable for toggling between table and card view modes with localStorage persistence. */
import { ref, computed } from 'vue'

/** The supported display modes for data tables. */
export type ViewMode = 'table' | 'card'

/** Configuration for {@link useViewMode}. */
export interface UseViewModeOptions {
  /** Unique identifier used as the localStorage key suffix. */
  tableId: string
  /** Initial view mode if nothing is stored (default `'card'`). */
  defaultMode?: ViewMode
}

/**
 * Manages a persisted view mode toggle between table and card layouts.
 *
 * @param options - Table ID for storage key and optional default mode.
 * @returns Reactive view mode state and toggle/set helpers.
 */
export function useViewMode(options: UseViewModeOptions) {
  const { tableId, defaultMode = 'card' } = options
  const storageKey = `viewMode:${tableId}`

  /**
   * Reads the stored view mode from localStorage.
   *
   * @returns The stored mode, or the default if unavailable.
   */
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

  /** Toggles between table and card view and persists the choice. */
  function toggleView() {
    viewMode.value = viewMode.value === 'card' ? 'table' : 'card'
    try {
      localStorage.setItem(storageKey, viewMode.value)
    } catch {
      // localStorage unavailable
    }
  }

  /**
   * Sets the view mode explicitly and persists it.
   *
   * @param mode - The view mode to set.
   */
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
