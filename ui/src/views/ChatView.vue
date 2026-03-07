<template>
  <AppLayout>
    <div class="flex flex-col h-[calc(100vh-4rem)]">
      <ChatMessageList
        ref="messageListRef"
        :messages="messages"
      />
      <ChatInputBar
        v-model="inputText"
        :is-streaming="isStreaming"
        @submit="submit"
        @stop="stop"
      />
    </div>
  </AppLayout>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { AppLayout } from '@/layouts'
import { ChatMessageList, ChatInputBar } from '@/components/chat'
import { useChatMessages, useChatStream, useChatInput, useAutoScroll } from '@/composables/chat'

const { messages, addUserMessage, startAssistantMessage, addActivity, completeMessage } =
  useChatMessages()
const { isStreaming, send, stop } = useChatStream({
  addUserMessage,
  startAssistantMessage,
  addActivity,
  completeMessage,
})

const messageListRef = ref<InstanceType<typeof ChatMessageList> | null>(null)
const { scrollToBottom } = useAutoScroll(() => messageListRef.value?.containerRef)
const { inputText, submit } = useChatInput((text) => send(text))

watch(
  [
    () => messages.value.length,
    () => messages.value[messages.value.length - 1]?.activities.length ?? 0,
  ],
  () => scrollToBottom(),
)
</script>
