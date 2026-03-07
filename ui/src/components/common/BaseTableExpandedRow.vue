<template>
  <TableRow class="bg-transparent hover:bg-transparent border-t-0">
    <TableCell :colspan="colspan" class="p-4">
      <slot />
    </TableCell>
  </TableRow>
</template>

/** Full-width table row used to render expanded content beneath a data row. */
<script setup lang="ts">
import { TableCell, TableRow } from '@/components/ui/table'

interface Props {
  /** Number of columns to span across the table. */
  colspan: number
}

defineProps<Props>()
</script>
