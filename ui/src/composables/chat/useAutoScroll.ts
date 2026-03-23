/**
 * Composable that provides auto-scrolling for a container element.
 *
 * Uses a MutationObserver to catch DOM changes from async rendering
 * (e.g. markdown parse + sanitize) that occur after the initial
 * reactive update.
 *
 * @param getElement - Accessor that returns the scrollable container, or null/undefined if not yet mounted.
 * @returns An object containing {@link scrollToBottom}.
 */
import { nextTick, onUnmounted } from 'vue'

export function useAutoScroll(getElement: () => HTMLElement | null | undefined) {
  let observer: MutationObserver | null = null
  let observedElement: HTMLElement | null = null

  function doScroll(): void {
    const el = getElement()
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  }

  function startObserving(): void {
    const el = getElement()
    if (!el || el === observedElement) return

    stopObserving()
    observedElement = el
    observer = new MutationObserver(doScroll)
    observer.observe(el, { childList: true, subtree: true, characterData: true })
  }

  function stopObserving(): void {
    observer?.disconnect()
    observer = null
    observedElement = null
  }

  /**
   * Scrolls the container to the bottom after the DOM has settled.
   * Starts a MutationObserver to catch subsequent async DOM updates
   * (e.g. markdown rendering) that change the scroll height.
   */
  function scrollToBottom(): void {
    startObserving()
    void nextTick(doScroll)
  }

  onUnmounted(stopObserving)

  return { scrollToBottom }
}
