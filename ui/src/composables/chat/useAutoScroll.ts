/**
 * Composable that provides auto-scrolling for a container element.
 *
 * @param getElement - Accessor that returns the scrollable container, or null/undefined if not yet mounted.
 * @returns An object containing {@link scrollToBottom}.
 */
import { nextTick } from 'vue'

export function useAutoScroll(getElement: () => HTMLElement | null | undefined) {
  /**
   * Scrolls the container to the bottom on the next Vue tick.
   * No-op if the element is not available.
   */
  function scrollToBottom(): void {
    nextTick(() => {
      const el = getElement()
      if (el) {
        el.scrollTop = el.scrollHeight
      }
    })
  }

  return { scrollToBottom }
}
