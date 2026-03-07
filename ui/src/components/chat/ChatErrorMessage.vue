<template>
  <div class="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
    <AlertCircle class="mt-0.5 h-4 w-4 shrink-0" />
    <span>{{ error }}</span>
  </div>
</template>

/** Displays an inline error banner with an alert icon and message text. */
<script setup lang="ts">
import { AlertCircle } from 'lucide-vue-next'

defineProps<{
  /** The error message to display. */
  error: string
}>()
</script>
