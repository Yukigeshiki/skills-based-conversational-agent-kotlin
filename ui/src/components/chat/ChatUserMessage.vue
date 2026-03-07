<template>
  <div class="flex justify-end">
    <div class="max-w-[70%] rounded-2xl rounded-br-sm bg-primary px-4 py-2.5 text-primary-foreground">
      <p class="whitespace-pre-wrap text-sm">{{ message.content }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { ChatMessage } from '@/types/chat'

defineProps<{ message: ChatMessage }>()
</script>
