<template>
  <div class="flex items-center gap-2 px-1 py-2 text-sm text-muted-foreground">
    <Loader2 class="h-4 w-4 animate-spin" />
    <span>{{ label }}</span>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Loader2 } from 'lucide-vue-next'
import type { ChatEvent } from '@/types/chat'

const props = defineProps<{ activities: ChatEvent[] }>()

const label = computed(() => {
  if (props.activities.length === 0) return 'Thinking...'
  const last = props.activities[props.activities.length - 1]
  switch (last.type) {
    case 'skill_matched':
      return `Using ${last.skillName}...`
    case 'plan_created':
      return 'Plan created, executing...'
    case 'plan_step_started':
      return `Step ${last.stepNumber}: ${last.description}...`
    case 'iteration_started':
      return 'Reasoning...'
    case 'thought':
      return 'Thinking...'
    case 'tool_call_started':
      return `Calling ${last.toolName}...`
    case 'tool_call_completed':
      return 'Processing result...'
    case 'plan_step_completed':
      return 'Step completed, continuing...'
    default:
      return 'Working...'
  }
})
</script>
