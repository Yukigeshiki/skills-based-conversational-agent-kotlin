<template>
  <div class="grid gap-2">
    <Label v-if="label">{{ label }}</Label>
    <div v-if="modelValue.length" class="flex flex-wrap gap-1.5 mb-1">
      <Badge
        v-for="tool in modelValue"
        :key="tool"
        variant="outline"
        class="gap-1 pr-1 py-0.5"
      >
        {{ tool }}
        <button
          class="ml-0.5 rounded-full hover:bg-accent p-0.5 cursor-pointer"
          @click="removeTool(tool)"
        >
          <X class="h-3 w-3" />
        </button>
      </Badge>
    </div>
    <div v-if="loading" class="text-sm text-muted-foreground">Loading tools...</div>
    <div v-else>
      <Combobox
        v-model="selection"
        v-model:open="comboboxOpen"
        reset-search-term-on-select
        reset-search-term-on-blur
        open-on-focus
      >
        <ComboboxAnchor class="w-full">
          <ComboboxInput :placeholder="placeholder" />
        </ComboboxAnchor>
        <ComboboxList
          align="start"
          class="w-[--reka-combobox-trigger-width] max-h-48 overflow-auto"
        >
          <ComboboxEmpty class="px-3 py-2">No tools available</ComboboxEmpty>
          <ComboboxItem
            v-for="tool in availableTools"
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

/**
 * Searchable combobox for selecting tool names from the list of registered tools.
 * Displays selected tools as removable badges above the search input.
 */
<script setup lang="ts">
import { ref, computed, watch, nextTick, onMounted } from 'vue'
import { X } from 'lucide-vue-next'
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
import { toolService } from '@/services'

interface Props {
  modelValue: string[]
  label?: string
  placeholder?: string
}

const props = withDefaults(defineProps<Props>(), {
  label: undefined,
  placeholder: 'Select a tool...',
})

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

const allTools = ref<string[]>([])
const loading = ref(false)
const selection = ref<string | undefined>(undefined)
const comboboxOpen = ref(false)

const availableTools = computed(() =>
  allTools.value.filter(t => !props.modelValue.includes(t))
)

watch(selection, (tool) => {
  if (!tool) return
  if (!props.modelValue.includes(tool)) {
    emit('update:modelValue', [...props.modelValue, tool])
  }
  nextTick(() => {
    selection.value = undefined
    comboboxOpen.value = false
  })
})

function removeTool(tool: string) {
  emit('update:modelValue', props.modelValue.filter(t => t !== tool))
}

onMounted(async () => {
  loading.value = true
  try {
    allTools.value = await toolService.getToolNames()
  } catch {
    allTools.value = []
  } finally {
    loading.value = false
  }
})
</script>
