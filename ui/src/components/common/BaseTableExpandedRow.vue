<template>
  <TableRow class="bg-transparent hover:bg-transparent border-t-0">
    <TableCell :colspan="colspan" class="p-4">
      <slot />
    </TableCell>
  </TableRow>
</template>

<script setup lang="ts">
import { TableCell, TableRow } from '@/components/ui/table'

interface Props {
  colspan: number
}

defineProps<Props>()
</script>
