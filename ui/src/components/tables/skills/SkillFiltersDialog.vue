<template>
  <BaseFilterDialog
    title="Filter Skills"
    description="Filter skills by search term or tools used."
    tooltip-text="Skill Filters"
    :filters="filters"
    :open="open"
    :initial-filters="initialFilters"
    @update:open="$emit('update:open', $event)"
    @update:filters="$emit('update:filters', $event)"
  >
    <template #trigger>
      <slot name="trigger" />
    </template>
    <template #fields="{ filters: localFilters }">
      <div class="grid gap-2">
        <Label for="search">Search</Label>
        <Input
          id="search"
          v-model="localFilters.search"
          placeholder="Search name or description"
          type="text"
        />
      </div>
      <ToolSelector
        :model-value="localFilters.tools ?? []"
        label="Tools"
        @update:model-value="localFilters.tools = $event.length ? $event : undefined"
      />
    </template>
  </BaseFilterDialog>
</template>

<script setup lang="ts">
import { BaseFilterDialog, ToolSelector } from '@/components/common'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { GetSkillsParams } from '@/types/skill'

interface Props {
  filters: GetSkillsParams
  open: boolean
}

defineProps<Props>()
defineEmits<{
  'update:open': [value: boolean]
  'update:filters': [filters: GetSkillsParams]
}>()

const initialFilters: GetSkillsParams = {
  search: undefined,
  tools: undefined,
}
</script>
