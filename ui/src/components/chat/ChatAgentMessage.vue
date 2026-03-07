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

<script setup lang="ts">
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import type { ChatMessage } from '@/types/chat'
import ChatActivityLog from './ChatActivityLog.vue'
import ChatErrorMessage from './ChatErrorMessage.vue'

const props = defineProps<{ message: ChatMessage }>()

const displayActivities = computed(() =>
  props.message.activities.filter((a) => a.type !== 'final_response' && a.type !== 'error'),
)

const renderedContent = computed(() =>
  DOMPurify.sanitize(marked.parse(props.message.content) as string),
)
</script>
