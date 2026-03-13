import { ref, watchEffect, toValue, type MaybeRefOrGetter } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * Asynchronously renders a markdown string to sanitized HTML.
 *
 * Uses `marked.parse` in async mode to avoid blocking the main thread
 * on large content, and sanitizes the output with DOMPurify.
 */
export async function renderMarkdown(content: string): Promise<string> {
  const html = await marked.parse(content || '', { async: true })
  return DOMPurify.sanitize(html)
}

/**
 * Reactive composable that renders markdown content to sanitized HTML.
 *
 * Accepts a ref, getter, or plain value and returns a ref that updates
 * whenever the source content changes. Suitable for use with `v-html`
 * bindings in Vue templates.
 */
export function useRenderedMarkdown(source: MaybeRefOrGetter<string>) {
  const rendered = ref('')

  watchEffect(async () => {
    const content = toValue(source)
    rendered.value = await renderMarkdown(content)
  })

  return rendered
}
