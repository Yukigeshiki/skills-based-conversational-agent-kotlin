<template>
  <div v-if="isLoading">
    <Loader2 class="h-4 w-4 animate-spin" />
  </div>
  <button
    v-else
    @click="$emit('toggle', $event)"
    class="cursor-pointer transition-transform"
    :class="{ 'rotate-90': isExpanded }"
  >
    <ChevronRight class="h-4 w-4" />
  </button>
</template>

/** Chevron toggle button for table row expansion, showing a spinner while loading. */
<script setup lang="ts">
import { ChevronRight, Loader2 } from 'lucide-vue-next'

defineProps<{
  /** Whether the row is currently expanded. */
  isExpanded: boolean
  /** Whether the expanded content is loading. */
  isLoading: boolean
}>()

defineEmits<{
  /** Emitted when the toggle control is clicked. */
  toggle: [event: Event]
}>()
</script>
