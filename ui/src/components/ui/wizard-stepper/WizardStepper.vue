<!--
/**
 * Wizard stepper component for multi-step form navigation.
 *
 * Displays a vertical list of steps with visual indicators for
 * completed, current, and upcoming steps. Includes connecting
 * lines between steps that reflect completion status.
 */
-->
<template>
  <nav aria-label="Progress" class="w-full">
    <ol role="list" class="relative">
      <li
        v-for="(step, index) in steps"
        :key="step.id"
        class="relative pb-1"
      >
        <!-- Connecting line -->
        <div
          v-if="index < steps.length - 1"
          :class="[
            'absolute w-0.5 h-[calc(100%-0.5rem)]',
            isStepVisited(index + 1)
              ? 'bg-primary'
              : 'bg-border',
          ]"
          :style="{ left: 'calc(0.75rem + 1rem - 1px)', top: 'calc(2.5rem + 0.25rem)' }"
        />

        <button
          type="button"
          :disabled="!canNavigateToStep(index)"
          :class="[
            'group relative flex w-full items-center gap-3 rounded-lg px-3 py-2 text-left transition-colors',
            currentStepIndex === index
              ? 'bg-primary/10'
              : canNavigateToStep(index)
                ? 'hover:bg-muted cursor-pointer'
                : 'cursor-not-allowed',
          ]"
          @click="navigateToStep(index)"
        >
          <!-- Step number circle -->
          <span
            :class="[
              'relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-sm font-medium transition-all',
              currentStepIndex === index
                ? 'bg-primary text-primary-foreground ring-4 ring-primary/20'
                : index < currentStepIndex
                  ? 'bg-primary text-primary-foreground ring-2 ring-foreground/50 ring-offset-2 ring-offset-background'
                  : isStepVisited(index)
                    ? 'bg-background text-muted-foreground ring-2 ring-foreground/70 ring-offset-2 ring-offset-background'
                    : 'bg-background text-muted-foreground ring-2 ring-muted-foreground/30 ring-offset-2 ring-offset-background',
            ]"
          >
            <Check v-if="index < currentStepIndex" class="h-4 w-4" />
            <span v-else>{{ index + 1 }}</span>
          </span>

          <!-- Step content -->
          <div class="flex flex-col">
            <span
              :class="[
                'text-sm font-medium leading-tight',
                currentStepIndex === index
                  ? 'text-primary'
                  : isStepVisited(index)
                    ? 'text-foreground'
                    : 'text-muted-foreground',
              ]"
            >
              {{ step.title }}
            </span>
            <span
              v-if="step.optional"
              class="text-xs text-muted-foreground"
            >
              Optional
            </span>
          </div>
        </button>
      </li>
    </ol>
  </nav>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check } from 'lucide-vue-next'

export interface WizardStep {
  id: string
  title: string
  optional?: boolean
}

interface Props {
  steps: WizardStep[]
  currentStepIndex: number
  highestStepReached?: number
  allowNavigation?: boolean
}

interface Emits {
  (e: 'navigate', index: number): void
}

const props = withDefaults(defineProps<Props>(), {
  highestStepReached: undefined,
  allowNavigation: true,
})

const emit = defineEmits<Emits>()

const effectiveHighestStep = computed(() =>
  props.highestStepReached !== undefined
    ? props.highestStepReached
    : props.currentStepIndex
)

function isStepVisited(index: number): boolean {
  return index <= effectiveHighestStep.value
}

function canNavigateToStep(index: number): boolean {
  if (!props.allowNavigation) return false
  return index !== props.currentStepIndex && isStepVisited(index)
}

function navigateToStep(index: number) {
  if (canNavigateToStep(index)) {
    emit('navigate', index)
  }
}
</script>
