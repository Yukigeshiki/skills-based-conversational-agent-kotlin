<template>
  <div class="flex justify-end">
    <div class="max-w-[70%] rounded-2xl rounded-br-sm bg-primary px-4 py-2.5 text-primary-foreground">
      <p class="whitespace-pre-wrap text-sm">{{ message.content }}</p>
    </div>
  </div>
</template>

/** Renders a user-sent message in a right-aligned primary-coloured bubble. */
<script setup lang="ts">
import type { ChatMessage } from '@/types/chat'

defineProps<{
  /** The user chat message to render. */
  message: ChatMessage
}>()
</script>
