<template>
  <BaseTableCard
    :is-expanded="isExpanded"
    :is-loading="isLoading"
    @toggle="$emit('toggle', $event)"
  >
    <CardHeader class="pb-2">
      <div class="flex items-center justify-between">
        <CardTitle class="text-sm font-semibold">{{ skill.name }}</CardTitle>
      </div>
    </CardHeader>
    <CardContent class="pt-0 pb-4">
      <p class="text-xs text-muted-foreground mb-2 line-clamp-2 min-h-[2lh]">{{ skill.description }}</p>
      <div class="flex gap-1 overflow-hidden h-5 mb-2">
        <Badge v-for="tool in skill.toolNames" :key="tool" variant="outline" class="text-xs shrink-0">
          {{ tool }}
        </Badge>
      </div>
      <p class="text-xs text-muted-foreground">Created: {{ formatDate(skill.createdAt) }}</p>
    </CardContent>
  </BaseTableCard>
</template>

/** Card representation of a skill summary with name, truncated description, tool badges, and creation date. */
<script setup lang="ts">
import { CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { BaseTableCard } from '@/components/common'
import { useSkillFormatters } from '@/composables/skills'
import type { SkillSummary } from '@/types/skill'

interface Props {
  /** The skill summary data to display. */
  skill: SkillSummary
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

const { formatDate } = useSkillFormatters()
</script>
