import { nextTick } from 'vue'

export function useAutoScroll(getElement: () => HTMLElement | null | undefined) {
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
