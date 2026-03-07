<template>
  <div v-if="filters.length > 0" class="flex items-center gap-2 flex-wrap flex-1">
    <span class="text-sm text-muted-foreground">Active filters:</span>
    <Badge
      v-for="filter in filters"
      :key="filter.key"
      variant="outline"
      class="gap-1 pr-1 py-0.5 border-transparent bg-blue-50 text-blue-600 dark:bg-blue-950/40 dark:text-blue-300"
    >
      <span>{{ filter.label }}: {{ filter.value }}</span>
      <button
        @click="$emit('remove', filter.key)"
        class="ml-1 rounded-full hover:bg-accent p-0.5 cursor-pointer"
        :aria-label="`Remove ${filter.label} filter`"
      >
        <X class="h-3 w-3" />
      </button>
    </Badge>
    <Button
      variant="ghost"
      size="sm"
      @click="$emit('clear-all')"
      class="h-6 text-xs cursor-pointer text-blue-600 dark:text-blue-300 hover:text-blue-700 dark:hover:text-blue-200"
    >
      Clear filters
    </Button>
  </div>
</template>

<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { X } from 'lucide-vue-next'

export interface ActiveFilter {
  key: string
  label: string
  value: string
}

interface Props {
  filters: ActiveFilter[]
}

interface Emits {
  (e: 'remove', key: string): void
  (e: 'clear-all'): void
}

defineProps<Props>()
defineEmits<Emits>()
</script>
