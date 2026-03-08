<template>
  <Dialog :open="open" @update:open="handleOpenChange">
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger as-child>
          <DialogTrigger as-child :disabled="disabled">
            <slot name="trigger">
              <button
                :disabled="disabled"
                :class="['h-9 w-9 flex items-center justify-center border rounded-full transition-colors', disabled ? 'cursor-not-allowed opacity-50' : 'cursor-pointer hover:bg-accent', triggerClass]"
              >
                <Filter class="h-4 w-4 text-foreground" />
              </button>
            </slot>
          </DialogTrigger>
        </TooltipTrigger>
        <TooltipContent v-if="tooltipText">
          <p>{{ tooltipText }}</p>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
    <DialogContent class="sm:max-w-106.25">
      <DialogHeader>
        <DialogTitle>{{ title }}</DialogTitle>
        <DialogDescription>{{ description }}</DialogDescription>
      </DialogHeader>

      <div class="grid gap-4 py-4">
        <slot name="fields" :filters="localFilters" />
      </div>

      <p v-if="localError || error" class="text-sm text-destructive">
        {{ localError || error }}
      </p>

      <DialogFooter class="gap-3">
        <Button
          variant="outline"
          @click="handleClear"
          class="cursor-pointer"
        >
          Clear
        </Button>
        <Button
          @click="handleApply"
          class="cursor-pointer"
        >
          Apply
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

<script setup lang="ts" generic="T extends object">
import { ref, watch } from 'vue'
import { Filter } from 'lucide-vue-next'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'

interface Props {
  title: string
  description: string
  filters: T
  open: boolean
  error?: string | null
  initialFilters: T
  validate?: (filters: T) => string | null
  tooltipText?: string
  triggerClass?: string
  disabled?: boolean
}

interface Emits {
  (e: 'update:open', value: boolean): void
  (e: 'update:filters', filters: T): void
  (e: 'clear-error'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const localError = ref<string | null>(null)
const localFilters = ref<T>(JSON.parse(JSON.stringify(props.filters)))

watch(() => props.filters, (newFilters) => {
  localFilters.value = JSON.parse(JSON.stringify(newFilters))
}, { deep: true })

watch(() => props.open, (newValue) => {
  if (newValue) {
    localError.value = null
    emit('clear-error')
  }
})

function handleOpenChange(value: boolean) {
  emit('update:open', value)
}

function handleApply() {
  localError.value = null

  try {
    if (props.validate) {
      const validationError = props.validate(localFilters.value)
      if (validationError) {
        localError.value = validationError
        return
      }
    }

    emit('update:filters', JSON.parse(JSON.stringify(localFilters.value)))
    emit('update:open', false)
  } catch (err: unknown) {
    localError.value = err instanceof Error ? err.message : 'Failed to apply filters. Please try again.'
  }
}

function handleClear() {
  localError.value = null

  try {
    localFilters.value = JSON.parse(JSON.stringify(props.initialFilters))
    emit('update:filters', JSON.parse(JSON.stringify(localFilters.value)))
    emit('update:open', false)
  } catch (err: unknown) {
    localError.value = err instanceof Error ? err.message : 'Failed to clear filters. Please try again.'
  }
}
</script>
