<template>
  <div class="space-y-4">
    <!-- Filter controls + View toggle -->
    <div class="flex items-center gap-3">
      <!-- Action buttons (before view toggle) -->
      <slot name="actions" />

      <!-- View mode toggle (left) - only show if #cards slot is provided -->
      <div class="shrink-0">
        <TooltipProvider v-if="hasCardSlot">
          <div class="flex items-center border rounded-full h-9 overflow-hidden">
            <Tooltip>
              <TooltipTrigger as-child>
                <button
                  class="h-9 w-9 flex items-center justify-center rounded-full transition-colors cursor-pointer"
                  :class="effectiveIsCardView ? 'bg-accent text-foreground' : 'text-muted-foreground hover:text-foreground'"
                  aria-label="Card view"
                  @click="setCardView"
                >
                  <LayoutGrid class="h-4 w-4" />
                </button>
              </TooltipTrigger>
              <TooltipContent>Card view</TooltipContent>
            </Tooltip>
            <Tooltip>
              <TooltipTrigger as-child>
                <button
                  class="h-9 w-9 flex items-center justify-center rounded-full transition-colors cursor-pointer"
                  :class="effectiveIsTableView ? 'bg-accent text-foreground' : 'text-muted-foreground hover:text-foreground'"
                  aria-label="Table view"
                  @click="setTableView"
                >
                  <List class="h-4 w-4" />
                </button>
              </TooltipTrigger>
              <TooltipContent>Table view</TooltipContent>
            </Tooltip>
          </div>
        </TooltipProvider>
      </div>

      <!-- Active filter badges -->
      <ActiveTableFilters
        :filters="activeFilters"
        @remove="(key) => $emit('remove-filter', key)"
        @clear-all="$emit('clear-all-filters')"
      />

      <!-- Spacer pushes dialogs to the right -->
      <div class="ml-auto flex items-center gap-3">
        <slot name="filter-prefix" />
        <div class="shrink-0">
          <slot name="filter-dialog" />
        </div>
      </div>
    </div>

    <!-- Table View -->
    <div v-if="effectiveIsTableView" :class="['rounded-md border overflow-hidden', tableClass]">
      <Table>
        <TableHeader>
          <TableRow>
            <slot
              name="headers"
              :sort-column="sortColumn"
              :sort-direction="sortDirection"
              :handle-sort="handleSort"
            />
          </TableRow>
        </TableHeader>
        <TableBody>
          <!-- Loading state -->
          <TableRow v-if="loading">
            <TableCell :colspan="columnCount" class="py-32">
              <div class="flex items-center justify-center text-muted-foreground">
                <Loader2 class="h-10 w-10 animate-spin" />
              </div>
            </TableCell>
          </TableRow>

          <!-- Error state -->
          <TableRow v-else-if="error">
            <TableCell :colspan="columnCount" class="text-center py-32 text-destructive">
              {{ error }}
            </TableCell>
          </TableRow>

          <!-- Empty state -->
          <TableRow v-else-if="data?.empty">
            <TableCell :colspan="columnCount" class="text-center py-8 text-muted-foreground">
              {{ emptyMessage }}
            </TableCell>
          </TableRow>

          <!-- Data rows -->
          <template v-else>
            <slot name="rows" :data="(data?.content || []) as unknown[]" />
          </template>
        </TableBody>
      </Table>
    </div>

    <!-- Card View -->
    <ExpandableCardGrid
      v-else
      :items="(data?.content || []) as unknown[]"
      :get-item-id="getItemId"
      :is-expanded="isItemExpanded"

      :loading="loading"
      :error="error"
      :is-empty="data?.empty === true"
      :empty-message="emptyMessage"
      :class="tableClass"
    >
      <template #card="{ item }">
        <slot name="card" :item="item" />
      </template>
      <template #expanded-content="{ item }">
        <slot name="expanded-content" :item="item" />
      </template>
    </ExpandableCardGrid>

    <!-- Pagination (shared) -->
    <TablePagination
      :current-page="currentPage"
      :total-pages="data?.totalPages || 1"
      :page-size="pageSize"
      :total-elements="data?.totalElements || 0"
      :is-first="!data || data.first === true"
      :is-last="!data || data.last === true"
      @update:page="(page) => $emit('update:page', page)"
      @update:page-size="(size) => $emit('update:page-size', size)"
    />
  </div>
</template>

<script setup lang="ts" generic="TData extends { content?: unknown[]; empty?: boolean; totalPages?: number; totalElements?: number; first?: boolean; last?: boolean }">
import { computed, onMounted, useSlots } from 'vue'
import { Table, TableBody, TableCell, TableHeader, TableRow } from '@/components/ui/table'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { LayoutGrid, List, Loader2 } from 'lucide-vue-next'
import { ActiveTableFilters, ExpandableCardGrid, TablePagination } from '@/components/common'
import type { ActiveFilter } from '@/composables/tables'
import { useViewMode, type ViewMode } from '@/composables/tables'

interface Props {
  loading: boolean
  error?: string
  data?: TData
  activeFilters: ActiveFilter[]
  currentPage: number
  pageSize: string
  sortColumn: string
  sortDirection: 'asc' | 'desc'
  columnCount: number
  tableId: string
  loadingMessage?: string
  emptyMessage?: string
  tableClass?: string
  getItemId?: (item: unknown) => string
  isItemExpanded?: (id: string) => boolean
  defaultViewMode?: ViewMode
}

interface Emits {
  (e: 'remove-filter', key: string): void
  (e: 'clear-all-filters'): void
  (e: 'update:page', page: number): void
  (e: 'update:page-size', size: string): void
  (e: 'sort', column: string): void
}

const props = withDefaults(defineProps<Props>(), {
  error: undefined,
  loadingMessage: 'Loading...',
  emptyMessage: 'No records found',
  tableClass: undefined,
  getItemId: undefined,
  isItemExpanded: undefined,
  defaultViewMode: undefined,
})
const emit = defineEmits<Emits>()

const slots = useSlots()
const hasCardSlot = computed(() => !!slots.card)

const getItemId = computed(() => props.getItemId ?? (() => ''))
const isItemExpanded = computed(() => props.isItemExpanded ?? (() => false))

if (import.meta.env.DEV) {
  onMounted(() => {
    if (hasCardSlot.value && !props.getItemId) {
      console.warn(
        `[BaseDataView] Table "${props.tableId}" has a #card slot but no getItemId prop. ` +
        'All cards will share the same ID, causing expansion and keying issues.',
      )
    }
  })
}

const { isCardView, isTableView, setViewMode } = useViewMode({
  tableId: props.tableId,
  defaultMode: props.defaultViewMode,
})

const effectiveIsCardView = computed(() => hasCardSlot.value && isCardView.value)
const effectiveIsTableView = computed(() => !hasCardSlot.value || isTableView.value)

function setCardView() {
  setViewMode('card')
}

function setTableView() {
  setViewMode('table')
}

function handleSort(column: string) {
  emit('sort', column)
}
</script>
