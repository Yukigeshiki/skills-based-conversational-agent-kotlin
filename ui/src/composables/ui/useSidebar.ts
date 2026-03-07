import { ref } from 'vue'

const STORAGE_KEY = 'sidebar-expanded'

function readStorage(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

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

export function useSidebar() {
  function toggle() {
    isExpanded.value = !isExpanded.value
    writeStorage(STORAGE_KEY, String(isExpanded.value))
  }

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
