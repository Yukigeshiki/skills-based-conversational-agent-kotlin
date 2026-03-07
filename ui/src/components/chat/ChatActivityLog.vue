<template>
  <div v-if="displayActivities.length > 0" class="mb-2">
    <button
      @click="isExpanded = !isExpanded"
      class="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
    >
      <ChevronDown v-if="isExpanded" class="h-3.5 w-3.5" />
      <ChevronRight v-else class="h-3.5 w-3.5" />
      <span>{{ displayActivities.length }} {{ displayActivities.length === 1 ? 'step' : 'steps' }}</span>
    </button>
    <div v-if="isExpanded" class="mt-1 border-l-2 border-border pl-3 space-y-0.5">
      <ChatActivityItem v-for="(event, index) in displayActivities" :key="index" :event="event" />
    </div>
  </div>
</template>

/**
 * Collapsible activity log that shows the number of steps and expands to reveal
 * individual {@link ChatActivityItem} entries. Auto-expands while streaming.
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ChevronDown, ChevronRight } from 'lucide-vue-next'
import type { ChatEvent } from '@/types/chat'
import ChatActivityItem from './ChatActivityItem.vue'

const props = defineProps<{
  /** The list of activity events to display. */
  activities: ChatEvent[]
  /** Whether the parent message is currently streaming. */
  streaming: boolean
}>()

/**
 * Filters out trailing `iteration_started` events that have no meaningful
 * follow-up (thought or tool call), keeping the log concise.
 */
const displayActivities = computed(() => {
  const activities = props.activities
  return activities.filter((event, index) => {
    if (event.type !== 'iteration_started') return true
    // Hide the last iteration_started if nothing meaningful follows it
    const remaining = activities.slice(index + 1)
    return remaining.some(
      (e) => e.type === 'thought' || e.type === 'tool_call_started' || e.type === 'tool_call_completed',
    )
  })
})

const isExpanded = ref(props.streaming)

watch(
  () => props.streaming,
  (streaming) => {
    if (streaming) isExpanded.value = true
  },
)
</script>
