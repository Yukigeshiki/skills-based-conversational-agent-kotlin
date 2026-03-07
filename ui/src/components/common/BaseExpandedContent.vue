<template>
  <div v-if="isLoading" :class="containerClasses">
    <div class="text-center text-muted-foreground">
      {{ loadingMessage }}
    </div>
  </div>
  <div v-else-if="hasData" :class="containerClasses">
    <slot />
  </div>
  <div v-else :class="containerClasses">
    <div class="text-center text-destructive">
      {{ errorMessage }}
    </div>
  </div>
</template>

/**
 * Wrapper for expanded row/card content that shows loading, error, or data states.
 * Adapts its styling based on whether it is embedded inside a card grid.
 */
<script setup lang="ts">
import { computed, inject } from 'vue'
import { EXPANDED_CONTENT_EMBEDDED_KEY } from '@/components/common/expandedContentKey'

interface Props {
  /** Whether the content is currently loading. */
  isLoading: boolean
  /** Whether data is available to display. */
  hasData: boolean
  /** Message shown during loading state. */
  loadingMessage?: string
  /** Message shown when data failed to load. */
  errorMessage?: string
}

withDefaults(defineProps<Props>(), {
  loadingMessage: 'Loading details...',
  errorMessage: 'Failed to load details',
})

const isEmbedded = inject(EXPANDED_CONTENT_EMBEDDED_KEY, false)

/**
 * Returns container CSS classes based on whether the content is embedded
 * (inside a card grid) or standalone (inside a table expanded row).
 */
const containerClasses = computed(() =>
  isEmbedded
    ? 'p-6'
    : 'bg-accent/50 border rounded-lg p-6 shadow-sm',
)
</script>
