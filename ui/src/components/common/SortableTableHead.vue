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

<script setup lang="ts">
import { TableHead } from '@/components/ui/table'

interface Props {
  column: string
  sortColumn: string
  sortDirection: 'asc' | 'desc'
}

interface Emits {
  (e: 'sort', column: string): void
}

defineProps<Props>()
defineEmits<Emits>()
</script>
