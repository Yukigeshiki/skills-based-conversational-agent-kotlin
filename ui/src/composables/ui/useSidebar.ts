/** Composable providing shared sidebar expand/collapse state with localStorage persistence. */
import { ref } from 'vue'

const STORAGE_KEY = 'sidebar-expanded'

/**
 * Reads a value from localStorage, returning null on failure.
 *
 * @param key - The storage key to read.
 * @returns The stored value, or null if unavailable.
 */
function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

/**
 * Writes a value to localStorage, silently ignoring errors.
 *
 * @param key - The storage key to write.
 * @param value - The value to store.
 */
function writeStorage(key: string, value: string): void {
  try {
    localStorage.setItem(key, value)
  } catch {
    // Ignore — private browsing or quota exceeded
  }
}

// Module-level refs: intentional singleton so all consumers share the same sidebar state
const isExpanded = ref(readStorage(STORAGE_KEY) === 'true')
const expandedItems = ref<Set<string>>(new Set())

/**
 * Provides singleton sidebar state shared across all consumers.
 *
 * @returns Reactive sidebar expansion state and toggle helpers.
 */
export function useSidebar() {
  /** Toggles the sidebar between expanded and collapsed, persisting the new state. */
  function toggle() {
    isExpanded.value = !isExpanded.value
    writeStorage(STORAGE_KEY, String(isExpanded.value))
  }

  /**
   * Toggles a sidebar sub-item between expanded and collapsed.
   *
   * @param label - The label of the item to toggle.
   */
  function toggleItem(label: string) {
    if (expandedItems.value.has(label)) {
      expandedItems.value.delete(label)
    } else {
      expandedItems.value.add(label)
    }
  }

  return {
    isExpanded,
    expandedItems,
    toggle,
    toggleItem,
  }
}
