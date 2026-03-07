<template>
  <div class="flex items-start gap-2 rounded-lg border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
    <AlertCircle class="mt-0.5 h-4 w-4 shrink-0" />
    <span>{{ error }}</span>
  </div>
</template>

<script setup lang="ts">
import { AlertCircle } from 'lucide-vue-next'

defineProps<{ error: string }>()
</script>
