<template>
  <Card
    class="cursor-pointer transition-shadow"
    :class="isExpanded ? '' : 'hover:shadow-md'"
    tabindex="0"
    role="button"
    :aria-expanded="isExpanded"
    @click="$emit('toggle', $event)"
    @keydown.enter="$emit('toggle', $event)"
    @keydown.space.prevent="$emit('toggle', $event)"
  >
    <slot />
    <CardContent v-if="isLoading" class="pt-0 pb-4">
      <div class="flex items-center gap-2 text-xs text-muted-foreground">
        <Loader2 class="h-3 w-3 animate-spin" />
        Loading details...
      </div>
    </CardContent>
  </Card>
</template>

<script setup lang="ts">
import { Loader2 } from 'lucide-vue-next'
import { Card, CardContent } from '@/components/ui/card'

interface Props {
  isExpanded?: boolean
  isLoading?: boolean
}

withDefaults(defineProps<Props>(), {
  isExpanded: false,
  isLoading: false,
})

defineEmits<{
  toggle: [event: MouseEvent | KeyboardEvent | Event]
}>()
</script>
