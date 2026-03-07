<template>
  <div class="border-t border-border bg-background px-4 py-3">
    <div class="flex items-end gap-2">
      <textarea
        ref="textareaRef"
        v-model="inputText"
        @keydown="handleKeydown"
        :disabled="disabled"
        placeholder="Type a message..."
        rows="1"
        class="flex-1 resize-none rounded-lg border border-input bg-background px-3 py-2 text-sm max-h-32 overflow-y-auto placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:opacity-50"
      />
      <button
        v-if="isStreaming"
        @click="emit('stop')"
        class="shrink-0 rounded-lg bg-destructive px-3 py-2 text-sm font-medium text-destructive-foreground hover:bg-destructive/90 transition-colors cursor-pointer"
      >
        <Square class="h-4 w-4" />
      </button>
      <button
        v-else
        @click="submit"
        :disabled="!inputText || !inputText.trim() || disabled"
        class="shrink-0 rounded-lg bg-primary px-3 py-2 text-sm font-medium text-primary-foreground hover:bg-primary/90 disabled:opacity-50 transition-colors cursor-pointer"
      >
        <SendHorizontal class="h-4 w-4" />
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { SendHorizontal, Square } from 'lucide-vue-next'

defineProps<{
  isStreaming: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  submit: []
  stop: []
}>()

const inputText = defineModel<string>()
const textareaRef = ref<HTMLTextAreaElement | null>(null)

watch(inputText, () => {
  nextTick(() => autoResize())
})

function autoResize(): void {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = `${el.scrollHeight}px`
}

function handleKeydown(event: KeyboardEvent): void {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    submit()
  }
}

function submit(): void {
  if (!inputText.value?.trim()) return
  emit('submit')
}
</script>
