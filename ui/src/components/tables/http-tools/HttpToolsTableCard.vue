<template>
  <BaseTableCard
    :is-expanded="isExpanded"
    :is-loading="isLoading"
    @toggle="$emit('toggle', $event)"
  >
    <CardHeader class="pb-2">
      <div class="flex items-center justify-between">
        <CardTitle class="text-sm font-semibold">{{ tool.name }}</CardTitle>
      </div>
    </CardHeader>
    <CardContent class="pt-0 pb-4">
      <p class="text-xs text-muted-foreground mb-2 line-clamp-2 min-h-[2lh]">{{ tool.description }}</p>
      <div class="flex gap-1 overflow-hidden h-5 mb-2">
        <Badge variant="outline" class="text-xs shrink-0">{{ tool.httpMethod }}</Badge>
        <span class="text-xs text-muted-foreground">{{ formatParameterCount(tool.parameters) }}</span>
      </div>
      <p class="text-xs text-muted-foreground">Created: {{ formatDate(tool.createdAt) }}</p>
    </CardContent>
  </BaseTableCard>
</template>

/** Card representation of an HTTP tool with name, truncated description, method badge, and creation date. */
<script setup lang="ts">
import { CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { BaseTableCard } from '@/components/common'
import { useHttpToolFormatters } from '@/composables/http-tools'
import type { HttpTool } from '@/types/http-tool'

interface Props {
  /** The http tool data to display. */
  tool: HttpTool
  /** Whether this card is currently expanded. */
  isExpanded: boolean
  /** Whether the expanded content is loading. */
  isLoading: boolean
}

defineProps<Props>()
defineEmits<{
  /** Emitted when the card is clicked to toggle expansion. */
  toggle: [event: Event]
}>()

const { formatDate, formatParameterCount } = useHttpToolFormatters()
</script>
