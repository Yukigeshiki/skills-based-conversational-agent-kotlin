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

/** Displays active filter chips with individual remove buttons and a "Clear filters" action. */
<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { X } from 'lucide-vue-next'

/** Represents a single active filter displayed as a removable chip. */
export interface ActiveFilter {
  /** The filter parameter key. */
  key: string
  /** Human-readable label for the chip. */
  label: string
  /** Display value for the chip. */
  value: string
}

interface Props {
  /** The list of active filters to render as chips. */
  filters: ActiveFilter[]
}

interface Emits {
  /**
   * Emitted when a single filter chip is removed.
   *
   * @param key - The key of the filter to remove.
   */
  (e: 'remove', key: string): void
  /** Emitted when the "Clear filters" button is clicked. */
  (e: 'clear-all'): void
}

defineProps<Props>()
defineEmits<Emits>()
</script>
