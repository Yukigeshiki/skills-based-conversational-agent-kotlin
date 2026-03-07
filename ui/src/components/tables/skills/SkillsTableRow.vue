<template>
  <TableRow
    class="cursor-pointer"
    :class="{ 'border-b-0 hover:bg-transparent': isExpanded }"
    @mouseenter="$emit('hover', skill.id)"
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
    <TableCell class="font-medium">{{ skill.name }}</TableCell>
    <TableCell class="max-w-xs truncate">{{ truncateDescription(skill.description) }}</TableCell>
    <TableCell>
      <div class="flex flex-wrap gap-1">
        <Badge v-for="tool in skill.toolNames" :key="tool" variant="outline" class="text-xs">
          {{ tool }}
        </Badge>
      </div>
    </TableCell>
    <TableCell>{{ formatDate(skill.createdAt) }}</TableCell>
    <TableCell>{{ formatDate(skill.updatedAt) }}</TableCell>
  </TableRow>
</template>

/** Table row for a skill summary with expandable chevron, tool badges, and formatted dates. */
<script setup lang="ts">
import { TableCell, TableRow } from '@/components/ui/table'
import { Badge } from '@/components/ui/badge'
import { ExpandableRowControl } from '@/components/common'
import { useSkillFormatters } from '@/composables/skills'
import type { SkillSummary } from '@/types/skill'

interface Props {
  /** The skill summary data to display in the row. */
  skill: SkillSummary
  /** Whether this row is currently expanded. */
  isExpanded: boolean
  /** Whether the expanded content is loading. */
  isLoading: boolean
}

defineProps<Props>()
defineEmits<{
  /** Emitted when the row or chevron is clicked to toggle expansion. */
  toggle: [event: Event]
  /** Emitted on mouse enter/leave with the skill ID or undefined. */
  hover: [id: string | undefined]
}>()

const { truncateDescription, formatDate } = useSkillFormatters()
</script>
