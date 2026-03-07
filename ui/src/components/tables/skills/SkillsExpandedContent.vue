<template>
  <BaseExpandedContent
    :is-loading="isLoading"
    :has-data="!!skill"
  >
    <div v-if="skill" class="space-y-4">
      <div class="max-w-4xl space-y-4">
        <!-- Details section -->
        <CollapsibleSection title="Details" :default-expanded="true" show-edit @edit="$emit('edit', skill.id)">
          <dl class="space-y-1 text-sm">
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Name:</dt>
              <dd>{{ skill.name }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Description:</dt>
              <dd>{{ skill.description }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Created:</dt>
              <dd>{{ formatDate(skill.createdAt) }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Last Updated:</dt>
              <dd>{{ formatDate(skill.updatedAt) }}</dd>
            </div>
          </dl>
        </CollapsibleSection>

        <!-- System Prompt section -->
        <CollapsibleSection title="System Prompt" :default-expanded="false">
          <pre class="text-sm whitespace-pre-wrap bg-background rounded border p-4 max-h-64 overflow-y-auto">{{ skill.systemPrompt }}</pre>
        </CollapsibleSection>

        <!-- Planning Prompt section -->
        <CollapsibleSection v-if="skill.planningPrompt" title="Planning Prompt" :default-expanded="false">
          <pre class="text-sm whitespace-pre-wrap bg-background rounded border p-4 max-h-64 overflow-y-auto">{{ skill.planningPrompt }}</pre>
        </CollapsibleSection>

        <!-- Tools section -->
        <CollapsibleSection title="Tools" :default-expanded="true">
          <div v-if="skill.toolNames.length" class="flex flex-wrap gap-2">
            <Badge v-for="tool in skill.toolNames" :key="tool" variant="outline">
              {{ tool }}
            </Badge>
          </div>
          <div v-else class="text-sm text-muted-foreground italic">
            No tools configured
          </div>
        </CollapsibleSection>

        <!-- Metadata section -->
        <CollapsibleSection title="Metadata" :default-expanded="false">
          <dl class="space-y-1 text-sm">
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">ID:</dt>
              <dd class="font-mono text-xs">{{ skill.id }}</dd>
            </div>
          </dl>
        </CollapsibleSection>
      </div>

      <!-- Danger Zone -->
      <div class="relative pt-4 mt-4 border-t border-destructive/20">
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-semibold text-destructive">Danger Zone</h3>
            <p class="text-sm text-muted-foreground mt-1">Permanently remove this skill from the system.</p>
          </div>
          <DestructiveButton
            size="sm"
            @click="$emit('delete', skill.id)"
          >
            Delete Skill
          </DestructiveButton>
        </div>
      </div>
    </div>
  </BaseExpandedContent>
</template>

<script setup lang="ts">
import { Badge } from '@/components/ui/badge'
import { BaseExpandedContent, CollapsibleSection, DestructiveButton } from '@/components/common'
import { useSkillFormatters } from '@/composables/skills'
import type { Skill } from '@/types/skill'

interface Props {
  skill: Skill | undefined
  isLoading: boolean
}

defineProps<Props>()
defineEmits<{
  edit: [skillId: string]
  delete: [skillId: string]
}>()

const { formatDate } = useSkillFormatters()
</script>