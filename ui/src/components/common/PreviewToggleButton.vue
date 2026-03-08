<template>
  <Button
    type="button"
    variant="ghost"
    size="sm"
    class="cursor-pointer h-6 px-2 text-xs"
    @click="$emit('toggle')"
  >
    {{ previewing ? 'Edit' : 'Preview' }}
  </Button>
</template>

<script setup lang="ts">
import { Button } from '@/components/ui/button'

defineProps<{
  previewing: boolean
}>()

defineEmits<{
  toggle: []
}>()
</script>
