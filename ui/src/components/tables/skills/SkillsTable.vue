<template>
  <div class="space-y-4">
    <BaseDataView
      :loading="loading"
      :error="error"
      :data="data"
      :active-filters="activeFilters"
      :current-page="currentPage"
      :page-size="pageSize"
      :sort-column="sortColumn"
      :sort-direction="sortDirection"
      :column-count="6"
      table-id="skills"
      empty-message="No skills found"
      :get-item-id="(item) => asSkillSummary(item).id"
      :is-item-expanded="isRowExpanded"
      @update:page="(page) => goToPage(page, fetchData)"
      @update:page-size="(size) => { updatePageSize(size); fetchData() }"
      @sort="(column) => handleSort(column, fetchData)"
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
              <TooltipContent>Add Skill</TooltipContent>
            </Tooltip>
          </TooltipProvider>
          <SkillFiltersDialog
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
        <TableHead>Tools</TableHead>
        <SortableTableHead column="createdAt" :sort-column="slotSortColumn" :sort-direction="slotSortDirection" @sort="slotHandleSort">
          Created
        </SortableTableHead>
        <SortableTableHead column="updatedAt" :sort-column="slotSortColumn" :sort-direction="slotSortDirection" @sort="slotHandleSort">
          Updated
        </SortableTableHead>
      </template>

      <template #rows="{ data: rowsData }">
        <template v-for="skill in asSkillSummaries(rowsData)" :key="skill.id">
          <SkillsTableRow
            :skill="skill"
            :is-expanded="isRowExpanded(skill.id)"
            :is-loading="isRowLoading(skill.id)"
            @toggle="(e) => toggleRowExpansion(skill.id, e, skillService.getSkillById)"
            @hover="setHoveredRow"
          />
          <BaseTableExpandedRow v-if="isRowExpanded(skill.id)" :colspan="6">
            <SkillsExpandedContent
              :skill="expandedRows.get(skill.id)"
              :is-loading="isRowLoading(skill.id)"
              @edit="openEdit"
              @delete="openDelete"
            />
          </BaseTableExpandedRow>
        </template>
      </template>

      <template #card="{ item }">
        <SkillsTableCard
          :skill="asSkillSummary(item)"
          :is-expanded="isRowExpanded(asSkillSummary(item).id)"
          :is-loading="isRowLoading(asSkillSummary(item).id)"
          @toggle="(e) => toggleRowExpansion(asSkillSummary(item).id, e, skillService.getSkillById)"
        />
      </template>

      <template #expanded-content="{ item }">
        <SkillsExpandedContent
          :skill="expandedRows.get(asSkillSummary(item).id)"
          :is-loading="isRowLoading(asSkillSummary(item).id)"
          @edit="openEdit"
          @delete="openDelete"
        />
      </template>
    </BaseDataView>

    <!-- Dialogs -->
    <AddSkillDialog
      :open="createDialogOpen"
      :error="createError"
      :submitting="createSubmitting"
      @update:open="createDialogOpen = $event"
      @create="handleCreate"
    />
    <SaveSkillDialog
      v-if="editingSkillId"
      v-model:open="editDialogOpen"
      :skill-id="editingSkillId"
      :error="editError"
      :submitting="editSubmitting"
      @edit="handleEdit"
    />
    <DeleteSkillDialog
      v-model:open="deleteDialogOpen"
      :error="deleteError"
      :submitting="deleteSubmitting"
      @confirm="handleDelete"
    />
  </div>
</template>

/**
 * Skills management table with card/table view toggle, server-side sorting
 * and pagination, expandable row details, and CRUD dialogs.
 */
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { TableHead } from '@/components/ui/table'
import { Plus } from 'lucide-vue-next'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { BaseDataView, BaseTableExpandedRow, SortableTableHead } from '@/components/common'
import SkillsTableRow from './SkillsTableRow.vue'
import SkillsTableCard from './SkillsTableCard.vue'
import SkillsExpandedContent from './SkillsExpandedContent.vue'
import AddSkillDialog from './dialogs/AddSkillDialog.vue'
import SaveSkillDialog from './dialogs/SaveSkillDialog.vue'
import DeleteSkillDialog from './dialogs/DeleteSkillDialog.vue'
import SkillFiltersDialog from './dialogs/SkillFiltersDialog.vue'
import { useTableBase } from '@/composables/tables'
import { useExpandableRows } from '@/composables/tables'
import { useSkillsTableLogic } from '@/composables/skills'
import { skillService } from '@/services'
import type { Skill, SkillSummary, GetSkillsParams } from '@/types/skill'
import type { ActiveFilter } from '@/composables/tables'
import type { PagedResponse } from '@/types/common'

const {
  currentPage,
  pageSize,
  sortColumn,
  sortDirection,
  sortString,
  handleSort,
  goToPage,
  updatePageSize,
  removeFilter,
  clearAllFilters,
  applyFilters,
} = useTableBase({ defaultSortColumn: 'createdAt', defaultSortDirection: 'desc' })

const filters = ref<GetSkillsParams>({})
const filterDialogOpen = ref(false)

function onApplyFilters(newFilters: GetSkillsParams) {
  applyFilters(newFilters, filters, (f) => { filters.value = f }, fetchData)
}

function onRemoveFilter(key: string) {
  removeFilter(key, filters, fetchData)
}

function onClearAllFilters() {
  clearAllFilters(filters, fetchData)
}

const activeFilters = computed<ActiveFilter[]>(() => {
  const entries: ActiveFilter[] = []
  if (filters.value.search) {
    entries.push({ key: 'search', label: 'Search', value: filters.value.search })
  }
  if (filters.value.tools?.length) {
    entries.push({ key: 'tools', label: 'Tools', value: filters.value.tools.join(', ') })
  }
  return entries
})

const {
  expandedRows,
  toggleRowExpansion,
  setHoveredRow,
  isRowExpanded,
  isRowLoading,
} = useExpandableRows<Skill>({ accordion: true })

const loading = ref(false)
const error = ref<string | undefined>(undefined)
const data = ref<PagedResponse<SkillSummary> | undefined>(undefined)

const {
  createDialogOpen,
  createError,
  createSubmitting,
  editDialogOpen,
  editError,
  editSubmitting,
  editingSkillId,
  deleteDialogOpen,
  deleteError,
  deleteSubmitting,
  openCreate,
  openEdit,
  openDelete,
  handleCreate,
  handleEdit,
  handleDelete,
} = useSkillsTableLogic({
  expandedRows,
  onDataChanged: fetchData,
})

/** Fetches a page of skills from the API with current filters, sort, and pagination. */
async function fetchData() {
  loading.value = true
  error.value = undefined
  try {
    const params: GetSkillsParams = {
      page: currentPage.value,
      size: parseInt(pageSize.value),
      sort: sortString.value,
    }
    if (filters.value.search) params.search = filters.value.search
    if (filters.value.tools?.length) params.tools = filters.value.tools
    data.value = await skillService.getAllSkills(params)
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load skills'
  } finally {
    loading.value = false
  }
}

/**
 * Type assertion helper for a single item.
 *
 * @param item - The unknown item from the generic data view.
 * @returns The item typed as {@link SkillSummary}.
 */
function asSkillSummary(item: unknown): SkillSummary {
  return item as SkillSummary
}

/**
 * Type assertion helper for an array of items.
 *
 * @param items - The unknown items array from the generic data view.
 * @returns The items typed as {@link SkillSummary}[].
 */
function asSkillSummaries(items: unknown[]): SkillSummary[] {
  return items as SkillSummary[]
}

onMounted(fetchData)
</script>
