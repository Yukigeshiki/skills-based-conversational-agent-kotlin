<template>
  <div v-if="displayActivities.length > 0 || streamingPreview" class="mb-2">
    <button
      @click="isExpanded = !isExpanded"
      class="flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground transition-colors cursor-pointer"
    >
      <ChevronDown v-if="isExpanded" class="h-3.5 w-3.5" />
      <ChevronRight v-else class="h-3.5 w-3.5" />
      <span v-if="displayActivities.length > 0">
        {{ displayActivities.length }} {{ displayActivities.length === 1 ? 'step' : 'steps' }}
      </span>
      <span v-else>Composing&hellip;</span>
    </button>
    <div v-if="isExpanded" class="mt-1 border-l-2 border-border pl-3 space-y-0.5">
      <ChatActivityItem
        v-for="(event, index) in displayActivities"
        :key="index"
        :event="event"
        :show-approval-buttons="event.type === 'approval_required' && event.approvalId === pendingApprovalId && !approvingInProgress"
        @approve="$emit('approve')"
        @reject="$emit('reject')"
      />
      <div v-if="streamingPreview" class="flex items-start gap-2 py-1 text-xs text-muted-foreground">
        <Brain class="mt-0.5 h-3.5 w-3.5 shrink-0 animate-pulse" />
        <pre class="min-w-0 flex-1 whitespace-pre-wrap font-sans italic">{{ streamingPreview }}</pre>
      </div>
    </div>
  </div>
</template>

/**
 * Collapsible activity log that shows the number of steps and expands to reveal
 * individual {@link ChatActivityItem} entries. Auto-expands while streaming.
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ChevronDown, ChevronRight, Brain } from 'lucide-vue-next'
import type { ChatEvent } from '@/types/chat'
import ChatActivityItem from './ChatActivityItem.vue'

const STREAMING_PREVIEW_LINES = 5
const STREAMING_PREVIEW_MAX_CHARS = 500

const props = defineProps<{
  /** The list of activity events to display. */
  activities: ChatEvent[]
  /** Whether the parent message is currently streaming. */
  streaming: boolean
  /** Accumulated streaming text to show as a thought-like preview. */
  streamingText?: string
  /** The approval ID currently pending, if any. */
  pendingApprovalId?: string
  /** Whether an approval request is currently in flight. */
  approvingInProgress?: boolean
}>()

defineEmits<{
  approve: []
  reject: []
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

/** Last 5 lines of streaming text (capped at 500 chars), or empty if not streaming. */
const streamingPreview = computed(() => {
  if (!props.streamingText) return ''
  const trimmed = props.streamingText.slice(-STREAMING_PREVIEW_MAX_CHARS)
  const lines = trimmed.split('\n')
  return lines.slice(-STREAMING_PREVIEW_LINES).join('\n')
})

const isExpanded = ref(props.streaming)

watch(
  () => props.streaming,
  (streaming) => {
    if (streaming) isExpanded.value = true
  },
)
</script>
