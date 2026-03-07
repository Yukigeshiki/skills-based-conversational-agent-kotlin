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

<script setup lang="ts">
import { ChevronRight, Loader2 } from 'lucide-vue-next'

defineProps<{
  isExpanded: boolean
  isLoading: boolean
}>()

defineEmits<{
  toggle: [event: Event]
}>()
</script>
