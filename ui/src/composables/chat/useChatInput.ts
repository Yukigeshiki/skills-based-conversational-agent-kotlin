import { ref } from 'vue'

export function useChatInput(onSubmit: (message: string) => void) {
  const inputText = ref('')

  function handleKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      submit()
    }
  }

  function submit(): void {
    const text = inputText.value.trim()
    if (!text) return
    inputText.value = ''
    onSubmit(text)
  }

  return { inputText, handleKeydown, submit }
}
