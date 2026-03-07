<template>
  <div v-if="activities.length > 0" class="mb-2">
    <button
      @click="isExpanded = !isExpanded"
      class="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
    >
      <ChevronDown v-if="isExpanded" class="h-3.5 w-3.5" />
      <ChevronRight v-else class="h-3.5 w-3.5" />
      <span>{{ activities.length }} {{ activities.length === 1 ? 'step' : 'steps' }}</span>
    </button>
    <div v-if="isExpanded" class="mt-1 border-l-2 border-border pl-3 space-y-0.5">
      <ChatActivityItem v-for="(event, index) in activities" :key="index" :event="event" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ChevronDown, ChevronRight } from 'lucide-vue-next'
import type { ChatEvent } from '@/types/chat'
import ChatActivityItem from './ChatActivityItem.vue'

const props = defineProps<{
  activities: ChatEvent[]
  streaming: boolean
}>()

const isExpanded = ref(props.streaming)

watch(
  () => props.streaming,
  (streaming) => {
    if (streaming) isExpanded.value = true
  },
)
</script>
