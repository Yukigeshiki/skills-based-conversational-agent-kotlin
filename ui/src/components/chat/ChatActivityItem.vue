<template>
  <div class="flex items-start gap-2 py-1 text-xs text-muted-foreground">
    <component :is="icon" class="mt-0.5 h-3.5 w-3.5 shrink-0" />
    <div class="min-w-0 flex-1">
      <template v-if="event.type === 'skill_matched'">
        <span>Matched skill: </span>
        <span class="rounded bg-accent px-1.5 py-0.5 font-medium text-accent-foreground">
          {{ event.skillName }}
        </span>
      </template>

      <template v-else-if="event.type === 'plan_created'">
        <div>
          <span class="italic">{{ event.plan.reasoning }}</span>
          <ol class="mt-1 list-inside list-decimal space-y-0.5 pl-1">
            <li v-for="step in event.plan.steps" :key="step.stepNumber">{{ step.description }}</li>
          </ol>
        </div>
      </template>

      <template v-else-if="event.type === 'plan_step_started'">
        <span>Starting step {{ event.stepNumber }}: {{ event.description }}</span>
      </template>

      <template v-else-if="event.type === 'plan_step_completed'">
        <span :class="event.status === 'FAILED' ? 'text-destructive' : ''">
          Step {{ event.stepNumber }} {{ event.status === 'COMPLETED' ? 'completed' : 'failed' }}
        </span>
      </template>

      <template v-else-if="event.type === 'iteration_started'">
        <span class="text-muted-foreground/60">Iteration {{ event.iterationNumber }}</span>
      </template>

      <template v-else-if="event.type === 'thought'">
        <span class="italic">{{ event.thought }}</span>
      </template>

      <template v-else-if="event.type === 'tool_call_started'">
        <details>
          <summary class="cursor-pointer">
            Calling <span class="font-medium">{{ event.toolName }}</span>
          </summary>
          <pre class="mt-1 max-h-48 overflow-y-auto overflow-x-auto rounded bg-muted p-2 text-[11px]">{{ formatArgs(event.arguments) }}</pre>
        </details>
      </template>

      <template v-else-if="event.type === 'tool_call_completed'">
        <details>
          <summary class="cursor-pointer" :class="event.error ? 'text-destructive' : ''">
            {{ event.toolName }} {{ event.error ? 'failed' : 'result' }}
          </summary>
          <pre class="mt-1 max-h-48 overflow-y-auto overflow-x-auto rounded bg-muted p-2 text-[11px]">{{ event.result }}</pre>
        </details>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  Zap,
  List,
  Circle,
  CheckCircle,
  RotateCw,
  Brain,
  Wrench,
} from 'lucide-vue-next'
import type { ChatEvent } from '@/types/chat'

const props = defineProps<{ event: ChatEvent }>()

const iconMap = {
  skill_matched: Zap,
  plan_created: List,
  plan_step_started: Circle,
  plan_step_completed: CheckCircle,
  iteration_started: RotateCw,
  thought: Brain,
  tool_call_started: Wrench,
  tool_call_completed: Wrench,
} as const

const icon = computed(() => iconMap[props.event.type as keyof typeof iconMap] ?? Circle)

function formatArgs(args: string): string {
  try {
    return JSON.stringify(JSON.parse(args), null, 2)
  } catch {
    return args
  }
}
</script>
