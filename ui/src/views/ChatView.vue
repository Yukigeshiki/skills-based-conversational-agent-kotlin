<template>
  <AppLayout>
    <div class="flex flex-col h-[calc(100vh-4rem)]">
      <div class="flex items-center justify-end px-4 py-2 border-b border-border">
        <Button size="sm" class="cursor-pointer" @click="handleNewChat">
          New Convo
        </Button>
      </div>
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

/**
 * Chat view page — composes message list, input bar, streaming, and conversation
 * management into the main chat experience with history persistence.
 */
<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { AppLayout } from '@/layouts'
import { Button } from '@/components/ui/button'
import { ChatMessageList, ChatInputBar } from '@/components/chat'
import {
  useChatMessages,
  useChatStream,
  useChatInput,
  useAutoScroll,
  useConversation,
} from '@/composables/chat'

const { messages, addUserMessage, startAssistantMessage, addActivity, completeMessage, setMessages } =
  useChatMessages()
const { conversationId, setConversationId, startNewConversation, loadHistory } = useConversation()

const { isStreaming, send, stop } = useChatStream({
  actions: {
    addUserMessage,
    startAssistantMessage,
    addActivity,
    completeMessage,
  },
  conversationId,
  onConversationStarted: setConversationId,
})

const messageListRef = ref<InstanceType<typeof ChatMessageList> | null>(null)
const { scrollToBottom } = useAutoScroll(() => messageListRef.value?.containerRef)
const { inputText, submit } = useChatInput((text) => send(text))

watch(
  [
    () => messages.value.length,
    () => messages.value[messages.value.length - 1]?.activities.length ?? 0,
    () => messages.value[messages.value.length - 1]?.content.length ?? 0,
    () => messages.value[messages.value.length - 1]?.streamingText?.length ?? 0,
  ],
  () => scrollToBottom(),
)

onMounted(async () => {
  const history = await loadHistory()
  if (history.length > 0) {
    setMessages(history)
    scrollToBottom()
  }
})

/** Starts a new conversation and clears all displayed messages. */
function handleNewChat(): void {
  startNewConversation()
  setMessages([])
}
</script>
