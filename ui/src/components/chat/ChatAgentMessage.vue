<template>
  <div class="flex justify-start">
    <div class="max-w-[80%]">
      <ChatActivityLog
        :activities="displayActivities"
        :streaming="message.status === 'streaming'"
      />
      <ChatErrorMessage v-if="message.error" :error="message.error" />
      <div
        v-if="message.content"
        class="rounded-2xl rounded-bl-sm bg-muted px-4 py-2.5 prose prose-sm prose-neutral dark:prose-invert max-w-none"
        v-html="renderedContent"
      />
    </div>
  </div>
</template>

/** Renders an assistant message with its activity log, error state, and sanitised markdown content. */
<script setup lang="ts">
import { computed } from 'vue'
import { renderMarkdown } from '@/composables/ui'
import type { ChatMessage } from '@/types/chat'
import ChatActivityLog from './ChatActivityLog.vue'
import ChatErrorMessage from './ChatErrorMessage.vue'

const props = defineProps<{
  /** The assistant chat message to render. */
  message: ChatMessage
}>()

/** Activities to display, excluding terminal event types (final_response, error). */
const displayActivities = computed(() =>
  props.message.activities.filter((a) => a.type !== 'final_response' && a.type !== 'error'),
)

/** The message content parsed as markdown and sanitised with DOMPurify. */
const renderedContent = computed(() => renderMarkdown(props.message.content))
</script>
