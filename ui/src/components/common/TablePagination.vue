<template>
  <div class="flex items-center justify-between">
    <div class="flex items-center gap-4">
      <div class="flex items-center gap-2">
        <span class="text-sm text-muted-foreground">Rows per page:</span>
        <Select :model-value="pageSize" @update:model-value="handlePageSizeChange">
          <SelectTrigger class="w-[70px]">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="20">20</SelectItem>
            <SelectItem value="50">50</SelectItem>
          </SelectContent>
        </Select>
      </div>
      <span class="text-sm text-muted-foreground">
        Showing {{ startRecord }}-{{ endRecord }} of {{ totalElements }} results
      </span>
    </div>

    <div class="flex items-center gap-2">
      <Button
        variant="outline"
        size="sm"
        :disabled="isFirst"
        @click="handlePageChange(currentPage - 1)"
        class="cursor-pointer disabled:cursor-not-allowed"
      >
        Previous
      </Button>

      <div class="flex items-center gap-2">
        <span class="text-sm text-muted-foreground">Page</span>
        <Input
          v-model="pageInput"
          type="number"
          min="1"
          :max="totalPages"
          class="w-[50px] h-8 text-center [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none"
          @keydown.enter="handlePageInputChange"
          @blur="handlePageInputChange"
        />
        <span class="text-sm text-muted-foreground">of {{ totalPages }}</span>
      </div>

      <Button
        variant="outline"
        size="sm"
        :disabled="isLast"
        @click="handlePageChange(currentPage + 1)"
        class="cursor-pointer disabled:cursor-not-allowed"
      >
        Next
      </Button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

interface Props {
  currentPage: number
  totalPages: number
  pageSize: string
  totalElements: number
  isFirst: boolean
  isLast: boolean
}

interface Emits {
  (e: 'update:page', page: number): void
  (e: 'update:page-size', size: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const pageInput = ref<string>((props.currentPage + 1).toString())

watch(() => props.currentPage, (newPage) => {
  pageInput.value = (newPage + 1).toString()
})

function handlePageSizeChange(value: unknown) {
  emit('update:page-size', String(value))
}

function handlePageChange(page: number) {
  emit('update:page', page)
}

function handlePageInputChange() {
  const pageNumber = parseInt(pageInput.value)

  if (isNaN(pageNumber) || pageNumber < 1 || pageNumber > props.totalPages) {
    pageInput.value = (props.currentPage + 1).toString()
    return
  }

  if (pageNumber - 1 !== props.currentPage) {
    handlePageChange(pageNumber - 1)
  }
}

const startRecord = computed(() => {
  if (props.totalElements === 0) return 0
  return props.currentPage * parseInt(props.pageSize) + 1
})

const endRecord = computed(() => {
  if (props.totalElements === 0) return 0
  return Math.min((props.currentPage + 1) * parseInt(props.pageSize), props.totalElements)
})
</script>
