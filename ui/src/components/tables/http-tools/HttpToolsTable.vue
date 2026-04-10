<template>
  <div class="space-y-4">
    <BaseDataView
      :loading="loading"
      :error="error"
      :data="pagedData"
      :active-filters="activeFilters"
      :current-page="currentPage"
      :page-size="pageSize"
      :sort-column="sortColumn"
      :sort-direction="sortDirection"
      :column-count="6"
      table-id="http-tools"
      empty-message="No http tools found"
      :get-item-id="(item) => asHttpTool(item).id"
      :is-item-expanded="isRowExpanded"
      @update:page="(page) => goToPage(page, () => {})"
      @update:page-size="(size) => updatePageSize(size)"
      @sort="(column) => handleSort(column, () => {})"
      @remove-filter="onRemoveFilter"
      @clear-all-filters="onClearAllFilters"
    >
      <template #filter-controls>
        <div class="flex items-center gap-1">
          <TooltipProvider>
            <Tooltip>
              <TooltipTrigger as-child>
                <button
                  class="h-9 w-9 flex items-center justify-center border rounded-full transition-colors cursor-pointer hover:bg-accent"
                  @click="openCreate"
                >
                  <Plus class="h-4 w-4 text-foreground" />
                </button>
              </TooltipTrigger>
              <TooltipContent>Add HTTP Tool</TooltipContent>
            </Tooltip>
          </TooltipProvider>
          <HttpToolFiltersDialog
            :open="filterDialogOpen"
            :filters="filters"
            @update:open="filterDialogOpen = $event"
            @update:filters="onApplyFilters"
          />
        </div>
      </template>

      <template #headers="{ sortColumn: slotSortColumn, sortDirection: slotSortDirection, handleSort: slotHandleSort }">
        <TableHead class="w-8" />
        <SortableTableHead column="name" :sort-column="slotSortColumn" :sort-direction="slotSortDirection" @sort="slotHandleSort">
          Name
        </SortableTableHead>
        <TableHead>Description</TableHead>
        <TableHead>Method</TableHead>
        <TableHead>Parameters</TableHead>
        <SortableTableHead column="createdAt" :sort-column="slotSortColumn" :sort-direction="slotSortDirection" @sort="slotHandleSort">
          Created
        </SortableTableHead>
      </template>

      <template #rows="{ data: rowsData }">
        <template v-for="tool in asHttpTools(rowsData)" :key="tool.id">
          <HttpToolsTableRow
            :tool="tool"
            :is-expanded="isRowExpanded(tool.id)"
            :is-loading="isRowLoading(tool.id)"
            @toggle="(e) => toggleRowExpansion(tool.id, e, httpToolService.getHttpToolById)"
            @hover="setHoveredRow"
          />
          <BaseTableExpandedRow v-if="isRowExpanded(tool.id)" :colspan="6">
            <HttpToolsExpandedContent
              :tool="expandedRows.get(tool.id)"
              :is-loading="isRowLoading(tool.id)"
              @edit="openEdit"
              @delete="openDelete"
              @test="openTest"
            />
          </BaseTableExpandedRow>
        </template>
      </template>

      <template #card="{ item }">
        <HttpToolsTableCard
          :tool="asHttpTool(item)"
          :is-expanded="isRowExpanded(asHttpTool(item).id)"
          :is-loading="isRowLoading(asHttpTool(item).id)"
          @toggle="(e) => toggleRowExpansion(asHttpTool(item).id, e, httpToolService.getHttpToolById)"
        />
      </template>

      <template #expanded-content="{ item }">
        <HttpToolsExpandedContent
          :tool="expandedRows.get(asHttpTool(item).id)"
          :is-loading="isRowLoading(asHttpTool(item).id)"
          @edit="openEdit"
          @delete="openDelete"
          @test="openTest"
        />
      </template>
    </BaseDataView>

    <!-- Dialogs -->
    <AddHttpToolDialog
      :open="createDialogOpen"
      :error="createError"
      :submitting="createSubmitting"
      @update:open="createDialogOpen = $event"
      @create="handleCreate"
    />
    <SaveHttpToolDialog
      v-if="editingToolId"
      v-model:open="editDialogOpen"
      :tool-id="editingToolId"
      :error="editError"
      :submitting="editSubmitting"
      @edit="handleEdit"
    />
    <DeleteHttpToolDialog
      v-model:open="deleteDialogOpen"
      :error="deleteError"
      :submitting="deleteSubmitting"
      @confirm="handleDelete"
    />
    <TestHttpToolDialog
      v-model:open="testDialogOpen"
      :tool-id="testingToolId"
      :error="testError"
      :submitting="testSubmitting"
      :test-result="testResult"
      @test="handleTest"
    />
  </div>
</template>

/**
 * HTTP tools management table with card/table view toggle, client-side
 * filtering and sorting, expandable row details, and CRUD + test dialogs.
 */
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { TableHead } from '@/components/ui/table'
import { Plus } from 'lucide-vue-next'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { BaseDataView, BaseTableExpandedRow, SortableTableHead } from '@/components/common'
import HttpToolsTableRow from './HttpToolsTableRow.vue'
import HttpToolsTableCard from './HttpToolsTableCard.vue'
import HttpToolsExpandedContent from './HttpToolsExpandedContent.vue'
import AddHttpToolDialog from './dialogs/AddHttpToolDialog.vue'
import SaveHttpToolDialog from './dialogs/SaveHttpToolDialog.vue'
import DeleteHttpToolDialog from './dialogs/DeleteHttpToolDialog.vue'
import TestHttpToolDialog from './dialogs/TestHttpToolDialog.vue'
import HttpToolFiltersDialog from './dialogs/HttpToolFiltersDialog.vue'
import { useTableBase } from '@/composables/tables'
import { useExpandableRows } from '@/composables/tables'
import { useHttpToolsTableLogic } from '@/composables/http-tools'
import { httpToolService } from '@/services/httpTool'
import type { HttpTool, HttpToolFilters } from '@/types/http-tool'
import type { ActiveFilter } from '@/composables/tables'
import type { PagedResponse } from '@/types/common'

const {
  currentPage,
  pageSize,
  sortColumn,
  sortDirection,
  handleSort,
  goToPage,
  updatePageSize,
  removeFilter,
  clearAllFilters,
  applyFilters,
} = useTableBase({ defaultSortColumn: 'createdAt', defaultSortDirection: 'desc' })

const filters = ref<HttpToolFilters>({})
const filterDialogOpen = ref(false)

function onApplyFilters(newFilters: HttpToolFilters) {
  applyFilters(newFilters, filters, (f) => { filters.value = f }, () => {})
}

function onRemoveFilter(key: string) {
  removeFilter(key, filters, () => {})
}

function onClearAllFilters() {
  clearAllFilters(filters, () => {})
}

const activeFilters = computed<ActiveFilter[]>(() => {
  const entries: ActiveFilter[] = []
  if (filters.value.search) {
    entries.push({ key: 'search', label: 'Search', value: filters.value.search })
  }
  return entries
})

const {
  expandedRows,
  toggleRowExpansion,
  setHoveredRow,
  isRowExpanded,
  isRowLoading,
} = useExpandableRows<HttpTool>({ accordion: true })

const loading = ref(false)
const error = ref<string | undefined>(undefined)
const allTools = ref<HttpTool[]>([])

const {
  createDialogOpen,
  createError,
  createSubmitting,
  editDialogOpen,
  editError,
  editSubmitting,
  editingToolId,
  deleteDialogOpen,
  deleteError,
  deleteSubmitting,
  testDialogOpen,
  testError,
  testSubmitting,
  testingToolId,
  testResult,
  openCreate,
  openEdit,
  openDelete,
  openTest,
  handleCreate,
  handleEdit,
  handleDelete,
  handleTest,
} = useHttpToolsTableLogic({
  expandedRows,
  onDataChanged: fetchData,
})

const filteredTools = computed(() => {
  let result = allTools.value
  const search = filters.value.search?.toLowerCase()
  if (search) {
    result = result.filter(
      (t) => t.name.toLowerCase().includes(search) || t.description.toLowerCase().includes(search)
    )
  }
  return result
})

const sortedTools = computed(() => {
  const items = [...filteredTools.value]
  const col = sortColumn.value
  const dir = sortDirection.value

  items.sort((a, b) => {
    let cmp = 0
    if (col === 'name') {
      cmp = a.name.localeCompare(b.name)
    } else if (col === 'createdAt') {
      cmp = new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    }
    return dir === 'desc' ? -cmp : cmp
  })

  return items
})

const pagedData = computed<PagedResponse<HttpTool> | undefined>(() => {
  if (!allTools.value.length && !loading.value && !error.value) {
    return { content: [], page: 0, size: 0, totalElements: 0, totalPages: 1, first: true, last: true, numberOfElements: 0, empty: true }
  }
  if (loading.value && allTools.value.length === 0) return undefined
  const sorted = sortedTools.value
  return {
    content: sorted,
    page: 0,
    size: sorted.length,
    totalElements: sorted.length,
    totalPages: 1,
    first: true,
    last: true,
    numberOfElements: sorted.length,
    empty: sorted.length === 0,
  }
})

/** Fetches all HTTP tools from the API. */
async function fetchData() {
  loading.value = true
  error.value = undefined
  try {
    allTools.value = await httpToolService.getAllHttpTools()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load HTTP tools'
  } finally {
    loading.value = false
  }
}

/** Type assertion helper that casts a single unknown item to an HttpTool. */
function asHttpTool(item: unknown): HttpTool {
  return item as HttpTool
}

/** Type assertion helper that casts an unknown array to HttpTool[]. */
function asHttpTools(items: unknown[]): HttpTool[] {
  return items as HttpTool[]
}

onMounted(fetchData)
</script>
