<template>
  <div
    @mouseenter="isHovered = true"
    @mouseleave="isHovered = false"
    class="relative"
  >
    <div class="flex items-center gap-2 mb-2">
      <h3 :class="['font-semibold', titleClass]">{{ title }}</h3>
      <template v-if="showEdit">
        <slot name="edit-button" :is-hovered="isHovered" :force-show-edit="forceShowEdit">
          <button
            @click.stop="$emit('edit')"
            class="p-1 rounded hover:bg-accent transition-all cursor-pointer"
            :class="isHovered || forceShowEdit ? 'opacity-100' : 'opacity-30'"
            :aria-label="`Edit ${title.toLowerCase()}`"
          >
            <Pencil class="h-3 w-3" />
          </button>
        </slot>
      </template>
      <button
        @click.stop="isExpanded = !isExpanded"
        class="p-1 rounded hover:bg-accent transition-all cursor-pointer"
        :class="isHovered ? 'opacity-100' : 'opacity-30'"
        :aria-label="isExpanded ? 'Collapse section' : 'Expand section'"
      >
        <ChevronDown v-if="isExpanded" class="h-4 w-4" />
        <ChevronRight v-else class="h-4 w-4" />
      </button>
    </div>
    <div v-if="!isExpanded" class="text-sm text-muted-foreground italic">
      Expand to view {{ title.toLowerCase() }}
    </div>
    <div v-else>
      <slot />
    </div>
  </div>
</template>

/**
 * Collapsible section with a title, optional edit button (shown on hover),
 * and expand/collapse toggle. Syncs with the `defaultExpanded` prop.
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { ChevronDown, ChevronRight, Pencil } from 'lucide-vue-next'

interface Props {
  /** The section heading text. */
  title: string
  /** Whether to show the edit button on hover. */
  showEdit?: boolean
  /** Forces the edit button to always be visible. */
  forceShowEdit?: boolean
  /** Whether the section starts expanded. */
  defaultExpanded?: boolean
  /** Additional CSS classes for the title element. */
  titleClass?: string
}

const props = withDefaults(defineProps<Props>(), {
  showEdit: false,
  forceShowEdit: false,
  defaultExpanded: false,
  titleClass: '',
})

defineEmits<{
  /** Emitted when the edit button is clicked. */
  edit: []
}>()

const isHovered = ref(false)
const isExpanded = ref(props.defaultExpanded)

watch(() => props.defaultExpanded, (newVal) => {
  isExpanded.value = newVal
})
</script>
