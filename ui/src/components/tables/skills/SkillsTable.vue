<template>
  <div class="space-y-4">
    <BaseDataView
      :loading="loading"
      :error="error"
      :data="pagedData"
      :active-filters="[]"
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
    >
      <template #actions>
        <button
          @click="openCreate"
          class="h-9 w-9 flex items-center justify-center border rounded-full transition-colors cursor-pointer text-muted-foreground hover:text-foreground hover:bg-accent"
        >
          <Plus class="h-4 w-4" />
        </button>
      </template>

      <template #headers="{ sortColumn, sortDirection, handleSort }">
        <TableHead class="w-8" />
        <SortableTableHead column="name" :sort-column="sortColumn" :sort-direction="sortDirection" @sort="handleSort">
          Name
        </SortableTableHead>
        <TableHead>Description</TableHead>
        <TableHead>Tools</TableHead>
        <SortableTableHead column="createdAt" :sort-column="sortColumn" :sort-direction="sortDirection" @sort="handleSort">
          Created
        </SortableTableHead>
        <SortableTableHead column="updatedAt" :sort-column="sortColumn" :sort-direction="sortDirection" @sort="handleSort">
          Updated
        </SortableTableHead>
      </template>

      <template #rows="{ data }">
        <template v-for="skill in asSkillSummaries(data)" :key="skill.id">
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
    <SkillsTableDialogs
      v-model:create-dialog-open="createDialogOpen"
      v-model:edit-dialog-open="editDialogOpen"
      v-model:delete-dialog-open="deleteDialogOpen"
      :create-error="createError"
      :create-submitting="createSubmitting"
      :edit-error="editError"
      :edit-submitting="editSubmitting"
      :editing-skill-id="editingSkillId"
      :delete-error="deleteError"
      :delete-submitting="deleteSubmitting"
      @create="handleCreate"
      @edit="handleEdit"
      @confirm-delete="handleDelete"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { Button } from '@/components/ui/button'
import { TableHead } from '@/components/ui/table'
import { Plus } from 'lucide-vue-next'
import { BaseDataView, BaseTableExpandedRow, SortableTableHead } from '@/components/common'
import SkillsTableRow from './SkillsTableRow.vue'
import SkillsTableCard from './SkillsTableCard.vue'
import SkillsExpandedContent from './SkillsExpandedContent.vue'
import SkillsTableDialogs from './SkillsTableDialogs.vue'
import { useTableBase } from '@/composables/tables'
import { useExpandableRows } from '@/composables/tables'
import { useSkillsTableLogic } from '@/composables/skills'
import { skillService } from '@/services'
import type { Skill, SkillSummary } from '@/types/skill'
import type { PagedResponse } from '@/types/common'
import { ref } from 'vue'

const {
  currentPage,
  pageSize,
  sortColumn,
  sortDirection,
  handleSort,
  goToPage,
  updatePageSize,
} = useTableBase({ defaultSortColumn: 'createdAt', defaultSortDirection: 'desc' })

const {
  expandedRows,
  toggleRowExpansion,
  setHoveredRow,
  isRowExpanded,
  isRowLoading,
} = useExpandableRows<Skill>({ accordion: true })

const loading = ref(false)
const error = ref<string | undefined>(undefined)
const skills = ref<Skill[]>([])

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

const pagedData = computed<PagedResponse<SkillSummary> | undefined>(() => {
  if (!skills.value.length && !loading.value) {
    return { content: [], empty: true, totalPages: 1, totalElements: 0, first: true, last: true }
  }
  if (!skills.value.length) return undefined

  const page = currentPage.value
  const size = parseInt(pageSize.value)
  const sorted = [...skills.value].sort((a, b) => {
    const col = sortColumn.value as keyof Skill
    const aVal = String(a[col] ?? '')
    const bVal = String(b[col] ?? '')
    const cmp = aVal.localeCompare(bVal)
    return sortDirection.value === 'asc' ? cmp : -cmp
  })

  const start = page * size
  const content = sorted.slice(start, start + size).map(s => ({
    id: s.id,
    name: s.name,
    description: s.description,
    toolNames: s.toolNames,
    createdAt: s.createdAt,
    updatedAt: s.updatedAt,
  }))

  return {
    content,
    empty: sorted.length === 0,
    totalPages: Math.max(1, Math.ceil(sorted.length / size)),
    totalElements: sorted.length,
    first: page === 0,
    last: start + size >= sorted.length,
  }
})

async function fetchData() {
  loading.value = true
  error.value = undefined
  try {
    skills.value = await skillService.getAllSkills()
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to load skills'
  } finally {
    loading.value = false
  }
}

function asSkillSummary(item: unknown): SkillSummary {
  return item as SkillSummary
}

function asSkillSummaries(items: unknown[]): SkillSummary[] {
  return items as SkillSummary[]
}

onMounted(fetchData)
</script>
