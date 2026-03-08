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
      <div class="grid gap-2">
        <Label>Tools</Label>
        <div v-if="localFilters.tools?.length" class="flex flex-wrap gap-1.5 mb-1">
          <Badge
            v-for="tool in localFilters.tools"
            :key="tool"
            variant="outline"
            class="gap-1 pr-1 py-0.5"
          >
            {{ tool }}
            <button
              class="ml-0.5 rounded-full hover:bg-accent p-0.5 cursor-pointer"
              @click="removeTool(localFilters, tool)"
            >
              <X class="h-3 w-3" />
            </button>
          </Badge>
        </div>
        <div v-if="toolsLoading" class="text-sm text-muted-foreground">Loading tools...</div>
        <div v-else ref="comboboxContainerRef" @focusout="handleBlur">
          <Combobox
            v-model="comboboxSelection"
            v-model:search-term="searchTerm"
            :filter-function="filterTools"
            reset-search-term-on-select
            reset-search-term-on-blur
            @update:model-value="(val: any) => onToolSelected(localFilters, val)"
          >
            <ComboboxAnchor class="w-full">
              <ComboboxInput
                placeholder="Select a tool..."
                @focus="comboboxOpen = true"
              />
            </ComboboxAnchor>
            <ComboboxList
              v-if="comboboxOpen"
              align="start"
              class="w-[--reka-combobox-trigger-width] max-h-48 overflow-auto"
            >
              <ComboboxEmpty class="px-3 py-2">No tools available</ComboboxEmpty>
              <ComboboxItem
                v-for="tool in availableTools(localFilters)"
                :key="tool"
                :value="tool"
                class="cursor-pointer"
              >
                {{ tool }}
              </ComboboxItem>
            </ComboboxList>
          </Combobox>
        </div>
      </div>
    </template>
  </BaseFilterDialog>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { X } from 'lucide-vue-next'
import { BaseFilterDialog } from '@/components/common'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Combobox,
  ComboboxAnchor,
  ComboboxInput,
  ComboboxList,
  ComboboxItem,
  ComboboxEmpty,
} from '@/components/ui/combobox'
import { skillService } from '@/services'
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

const allTools = ref<string[]>([])
const toolsLoading = ref(false)
const comboboxSelection = ref<string | undefined>(undefined)
const searchTerm = ref('')
const comboboxOpen = ref(false)
const comboboxContainerRef = ref<HTMLElement | null>(null)

function availableTools(localFilters: GetSkillsParams): string[] {
  const selected = localFilters.tools ?? []
  return allTools.value.filter(t => !selected.includes(t))
}

function filterTools(items: string[], searchTerm: string): string[] {
  if (!searchTerm) return items
  const lower = searchTerm.toLowerCase()
  return items.filter(item => item.toLowerCase().includes(lower))
}

function onToolSelected(localFilters: GetSkillsParams, tool: string) {
  if (!tool) return
  const current = localFilters.tools ?? []
  if (!current.includes(tool)) {
    localFilters.tools = [...current, tool]
  }
  nextTick(() => {
    comboboxSelection.value = undefined
    searchTerm.value = ''
  })
}

function removeTool(localFilters: GetSkillsParams, tool: string) {
  const current = localFilters.tools ?? []
  localFilters.tools = current.filter(t => t !== tool)
  if (localFilters.tools.length === 0) {
    localFilters.tools = undefined
  }
}

function handleBlur(event: FocusEvent) {
  const container = comboboxContainerRef.value
  const relatedTarget = event.relatedTarget as Node | null
  if (container && relatedTarget && container.contains(relatedTarget)) {
    return
  }
  comboboxOpen.value = false
}

onMounted(async () => {
  toolsLoading.value = true
  try {
    allTools.value = await skillService.getToolNames()
  } catch {
    allTools.value = []
  } finally {
    toolsLoading.value = false
  }
})
</script>
