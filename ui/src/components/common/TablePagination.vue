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

/**
 * Pagination controls with page-size selector, record count display,
 * previous/next buttons, and a direct page-number input.
 */
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'

interface Props {
  /** Zero-based current page index. */
  currentPage: number
  /** Total number of pages. */
  totalPages: number
  /** Current page size as a string. */
  pageSize: string
  /** Total number of records across all pages. */
  totalElements: number
  /** Whether the current page is the first page. */
  isFirst: boolean
  /** Whether the current page is the last page. */
  isLast: boolean
}

interface Emits {
  /**
   * Emitted when the user navigates to a different page.
   *
   * @param page - The zero-based page index.
   */
  (e: 'update:page', page: number): void
  /**
   * Emitted when the user changes the page size.
   *
   * @param size - The new page size as a string.
   */
  (e: 'update:page-size', size: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

const pageInput = ref<string>((props.currentPage + 1).toString())

watch(() => props.currentPage, (newPage) => {
  pageInput.value = (newPage + 1).toString()
})

/**
 * Emits a page-size update.
 *
 * @param value - The selected page size value.
 */
function handlePageSizeChange(value: unknown) {
  emit('update:page-size', String(value))
}

/**
 * Emits a page navigation event.
 *
 * @param page - The zero-based page index to navigate to.
 */
function handlePageChange(page: number) {
  emit('update:page', page)
}

/** Validates the page input field and navigates if the value is a valid page number. */
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

/** The one-based index of the first record on the current page. */
const startRecord = computed(() => {
  if (props.totalElements === 0) return 0
  return props.currentPage * parseInt(props.pageSize) + 1
})

/** The one-based index of the last record on the current page. */
const endRecord = computed(() => {
  if (props.totalElements === 0) return 0
  return Math.min((props.currentPage + 1) * parseInt(props.pageSize), props.totalElements)
})
</script>
