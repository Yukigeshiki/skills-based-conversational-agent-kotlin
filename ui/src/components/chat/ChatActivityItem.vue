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

      <template v-else-if="event.type === 'skill_rerouted'">
        <span class="text-yellow-600 dark:text-yellow-400">
          Rerouted from
          <span class="font-medium">{{ event.fromSkill }}</span>
          to
          <span class="rounded bg-accent px-1.5 py-0.5 font-medium text-accent-foreground">{{ event.toSkill }}</span>
          — {{ event.reason }}
        </span>
      </template>

      <template v-else-if="event.type === 'plan_created'">
        <div>
          <span class="italic">{{ event.plan.reasoning }}</span>
          <ol class="mt-1 list-inside list-decimal space-y-0.5 pl-1">
            <li v-for="step in event.plan.steps" :key="step.stepNumber">
              {{ step.description }}
              <template v-if="step.skillName && event.plan.steps.length > 1">
                .
                <span class="ml-1">Matched skill:</span>
                <span class="rounded bg-accent px-1.5 py-0.5 font-medium text-accent-foreground">{{ step.skillName }}</span>
              </template>
            </li>
          </ol>
        </div>
      </template>

      <template v-else-if="event.type === 'plan_step_started'">
        <span>Starting step {{ event.stepNumber }}: {{ event.description }}</span>
      </template>

      <template v-else-if="event.type === 'plan_step_completed'">
        <span :class="event.status === 'FAILED' ? 'text-destructive' : event.status === 'SKIPPED' ? 'text-muted-foreground/60' : ''">
          Step {{ event.stepNumber }}
          {{ event.status === 'COMPLETED' ? 'completed' : event.status === 'SKIPPED' ? 'skipped' : 'failed' }}
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

      <template v-else-if="event.type === 'skill_handoff_started'">
        <span>
          Delegating to
          <span class="rounded bg-accent px-1.5 py-0.5 font-medium text-accent-foreground">{{ event.toSkill }}</span>
        </span>
      </template>

      <template v-else-if="event.type === 'skill_handoff_completed'">
        <span :class="event.success ? '' : 'text-destructive'">
          Delegation to
          <span class="font-medium">{{ event.toSkill }}</span>
          {{ event.success ? 'completed' : 'failed' }}
        </span>
      </template>

      <template v-else-if="event.type === 'approval_required'">
        <div>
          <span class="text-yellow-600 dark:text-yellow-400">
            Approval required for tool execution in
            <span class="font-medium">{{ event.skillName }}</span>
          </span>
          <div v-if="showApprovalButtons" class="flex gap-2 mt-2">
            <Button size="sm" class="cursor-pointer" @click="$emit('approve')">
              Approve
            </Button>
            <Button size="sm" variant="outline" class="cursor-pointer" @click="$emit('reject')">
              Reject
            </Button>
          </div>
        </div>
      </template>

      <template v-else-if="event.type === 'llm_retrying'">
        <span class="text-yellow-600 dark:text-yellow-400">
          Retrying LLM call (attempt {{ event.attempt }}/{{ event.maxAttempts }})
          <span class="text-muted-foreground">— {{ event.message }}</span>
        </span>
      </template>
    </div>
  </div>
</template>

/**
 * Renders a single activity event (skill match, thought, tool call, plan step, etc.)
 * with an appropriate icon and formatted content.
 */
<script setup lang="ts">
import { computed } from 'vue'
import { Button } from '@/components/ui/button'
import {
  Zap,
  List,
  Circle,
  CheckCircle,
  RotateCw,
  Brain,
  Wrench,
  GitBranch,
  ArrowRightLeft,
  ShieldCheck,
} from 'lucide-vue-next'
import type { ChatEvent } from '@/types/chat'

const props = defineProps<{
  event: ChatEvent
  showApprovalButtons?: boolean
}>()

defineEmits<{
  approve: []
  reject: []
}>()

/** Maps event types to their corresponding Lucide icon components. */
const iconMap = {
  skill_matched: Zap,
  skill_rerouted: GitBranch,
  plan_created: List,
  plan_step_started: Circle,
  plan_step_completed: CheckCircle,
  iteration_started: RotateCw,
  thought: Brain,
  tool_call_started: Wrench,
  tool_call_completed: Wrench,
  skill_handoff_started: ArrowRightLeft,
  skill_handoff_completed: ArrowRightLeft,
  approval_required: ShieldCheck,
  llm_retrying: RotateCw,
} as const

/** Resolves the icon component for the current event type, falling back to {@link Circle}. */
const icon = computed(() => iconMap[props.event.type as keyof typeof iconMap] ?? Circle)

/**
 * Pretty-prints a JSON arguments string, returning the raw string on parse failure.
 *
 * @param args - The raw JSON arguments string.
 * @returns A formatted JSON string or the original value.
 */
function formatArgs(args: string): string {
  try {
    return JSON.stringify(JSON.parse(args), null, 2)
  } catch {
    return args
  }
}
</script>
