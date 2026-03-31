<template>
  <BaseFilterDialog
    title="Filter HTTP Tools"
    description="Filter tools by search term."
    tooltip-text="Tool Filters"
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
    </template>
  </BaseFilterDialog>
</template>

<script setup lang="ts">
import { BaseFilterDialog } from '@/components/common'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import type { HttpToolFilters } from '@/types/http-tool'

interface Props {
  filters: HttpToolFilters
  open: boolean
}

defineProps<Props>()
defineEmits<{
  'update:open': [value: boolean]
  'update:filters': [filters: HttpToolFilters]
}>()

const initialFilters: HttpToolFilters = {
  search: undefined,
}
</script>
