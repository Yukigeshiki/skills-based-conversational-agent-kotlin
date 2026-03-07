<template>
  <TableHead class="cursor-pointer" @click="$emit('sort', column)">
    <div class="flex items-center gap-1 min-w-0">
      <span class="truncate">
        <slot />
      </span>
      <span v-if="sortColumn === column" class="text-xs shrink-0">
        {{ sortDirection === 'asc' ? '↑' : '↓' }}
      </span>
    </div>
  </TableHead>
</template>

/** Clickable table header cell that displays a sort direction indicator when active. */
<script setup lang="ts">
import { TableHead } from '@/components/ui/table'

interface Props {
  /** The column key this header represents. */
  column: string
  /** The currently sorted column key. */
  sortColumn: string
  /** The current sort direction. */
  sortDirection: 'asc' | 'desc'
}

interface Emits {
  /**
   * Emitted when the header is clicked to request sorting.
   *
   * @param column - The column key to sort by.
   */
  (e: 'sort', column: string): void
}

defineProps<Props>()
defineEmits<Emits>()
</script>
