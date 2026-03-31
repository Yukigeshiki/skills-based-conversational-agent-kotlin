<template>
  <TableRow
    class="cursor-pointer"
    :class="{ 'border-b-0 hover:bg-transparent': isExpanded }"
    @mouseenter="$emit('hover', tool.id)"
    @mouseleave="$emit('hover', undefined)"
    @click="$emit('toggle', $event)"
    @keydown.enter="$emit('toggle', $event)"
    @keydown.space.prevent="$emit('toggle', $event)"
    tabindex="0"
  >
    <TableCell class="w-8">
      <ExpandableRowControl
        :is-expanded="isExpanded"
        :is-loading="isLoading"
        @toggle="$emit('toggle', $event)"
      />
    </TableCell>
    <TableCell class="font-medium">{{ tool.name }}</TableCell>
    <TableCell class="max-w-xs truncate">{{ truncateDescription(tool.description) }}</TableCell>
    <TableCell>
      <Badge variant="outline">{{ tool.httpMethod }}</Badge>
    </TableCell>
    <TableCell>{{ formatParameterCount(tool.parameters) }}</TableCell>
    <TableCell>{{ formatDate(tool.createdAt) }}</TableCell>
  </TableRow>
</template>

/** Table row for an HTTP tool summary with expandable chevron, method badge, and formatted dates. */
<script setup lang="ts">
import { TableCell, TableRow } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { ExpandableRowControl } from '@/components/common'
import { useHttpToolFormatters } from '@/composables/http-tools'
import type { HttpTool } from '@/types/http-tool'

interface Props {
  /** The http tool data to display in the row. */
  tool: HttpTool
  /** Whether this row is currently expanded. */
  isExpanded: boolean
  /** Whether the expanded content is loading. */
  isLoading: boolean
}

defineProps<Props>()
defineEmits<{
  /** Emitted when the row or chevron is clicked to toggle expansion. */
  toggle: [event: Event]
  /** Emitted on mouse enter/leave with the tool ID or undefined. */
  hover: [id: string | undefined]
}>()

const { truncateDescription, formatDate, formatParameterCount } = useHttpToolFormatters()
</script>
