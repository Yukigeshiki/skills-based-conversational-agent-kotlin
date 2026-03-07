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

<script setup lang="ts">
import { computed, inject } from 'vue'
import { EXPANDED_CONTENT_EMBEDDED_KEY } from '@/components/common/expandedContentKey'

interface Props {
  isLoading: boolean
  hasData: boolean
  loadingMessage?: string
  errorMessage?: string
}

withDefaults(defineProps<Props>(), {
  loadingMessage: 'Loading details...',
  errorMessage: 'Failed to load details',
})

const isEmbedded = inject(EXPANDED_CONTENT_EMBEDDED_KEY, false)

const containerClasses = computed(() =>
  isEmbedded
    ? 'p-6'
    : 'bg-accent/50 border rounded-lg p-6 shadow-sm',
)
</script>
