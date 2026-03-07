/**
 * Composable for chat input state with Enter-to-submit and Shift+Enter for newlines.
 *
 * @param onSubmit - Callback invoked with the trimmed message text when the user submits.
 * @returns An object containing the reactive {@link inputText}, {@link handleKeydown}, and {@link submit}.
 */
import { ref } from 'vue'

export function useChatInput(onSubmit: (message: string) => void) {
  const inputText = ref('')

  /**
   * Keydown handler that submits on Enter (without Shift).
   *
   * @param event - The keyboard event from the input element.
   */
  function handleKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  /**
   * Submits the current input text if non-empty, then clears the input.
   */
  function submit(): void {
    const text = inputText.value.trim()
    if (!text) return
    inputText.value = ''
    onSubmit(text)
  }

  return { inputText, handleKeydown, submit }
}
