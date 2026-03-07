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
      <p class="text-xs text-muted-foreground mb-2">{{ truncateDescription(skill.description) }}</p>
      <div class="flex flex-wrap gap-1 mb-2">
        <Badge v-for="tool in skill.toolNames" :key="tool" variant="outline" class="text-xs">
          {{ tool }}
        </Badge>
      </div>
      <p class="text-xs text-muted-foreground">Created: {{ formatDate(skill.createdAt) }}</p>
    </CardContent>
  </BaseTableCard>
</template>

<script setup lang="ts">
import { CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { BaseTableCard } from '@/components/common'
import { useSkillFormatters } from '@/composables/skills'
import type { SkillSummary } from '@/types/skill'

interface Props {
  skill: SkillSummary
  isExpanded: boolean
  isLoading: boolean
}

defineProps<Props>()
defineEmits<{
  toggle: [event: Event]
}>()

const { truncateDescription, formatDate } = useSkillFormatters()
</script>
