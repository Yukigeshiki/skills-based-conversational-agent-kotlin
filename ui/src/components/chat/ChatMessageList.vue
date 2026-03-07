<template>
  <div ref="containerRef" class="flex-1 overflow-y-auto px-4 py-4 space-y-4">
    <template v-for="message in messages" :key="message.id">
      <ChatUserMessage v-if="message.role === 'user'" :message="message" />
      <ChatAgentMessage v-else :message="message" />
    </template>
    <ChatStreamingIndicator
      v-if="streamingMessage"
      :activities="streamingMessage.activities"
    />
    <div v-if="messages.length === 0" class="flex h-full items-center justify-center">
      <p class="text-muted-foreground">Send a message to start a new conversation.</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { ChatMessage } from '@/types/chat'
import ChatUserMessage from './ChatUserMessage.vue'
import ChatAgentMessage from './ChatAgentMessage.vue'
import ChatStreamingIndicator from './ChatStreamingIndicator.vue'

const props = defineProps<{ messages: ChatMessage[] }>()

const containerRef = ref<HTMLElement | null>(null)

defineExpose({ containerRef })

const streamingMessage = computed(() => {
  const last = props.messages[props.messages.length - 1]
  return last?.status === 'streaming' ? last : null
})
</script>
