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
import { useRenderedMarkdown } from '@/composables/ui'
import type { ChatMessage } from '@/types/chat'
import ChatActivityLog from './ChatActivityLog.vue'
import ChatErrorMessage from './ChatErrorMessage.vue'

const props = defineProps<{
  /** The assistant chat message to render. */
  message: ChatMessage
}>()

/** True when the plan has 2+ steps. */
const isMultiStep = computed(() => {
  const planEvent = props.message.activities.find((a) => a.type === 'plan_created')
  return planEvent?.type === 'plan_created' && planEvent.plan.steps.length > 1
})

/**
 * Activities to display:
 * - Single step: show skill_matched at the top, hide per-step skill in plan_created
 * - Multi-step: hide standalone skill_matched, show per-step skills in plan_created
 */
/** Event types that are infrastructure-only and should not appear in the activity log. */
const hiddenEventTypes = new Set([
  'conversation_started',
  'final_response',
  'error',
  'warning',
  'heartbeat',
  'approval_resolved',
])

const displayActivities = computed(() =>
  props.message.activities.filter(
    (a) =>
      !hiddenEventTypes.has(a.type) &&
      (a.type !== 'skill_matched' || !isMultiStep.value),
  ),
)

/** The message content parsed as markdown and sanitised with DOMPurify. */
const renderedContent = useRenderedMarkdown(() => props.message.content)
</script>
