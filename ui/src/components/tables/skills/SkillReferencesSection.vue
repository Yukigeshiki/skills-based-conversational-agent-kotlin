<template>
  <div class="space-y-3">
    <div v-if="loading" class="text-sm text-muted-foreground">Loading references...</div>
    <div v-else-if="references.length === 0" class="text-sm text-muted-foreground italic">
      No references attached. Add reference documents to provide RAG context for this skill.
    </div>
    <div v-else class="max-h-30 overflow-y-auto space-y-1">
      <div
        v-for="ref in references"
        :key="ref.id"
        class="flex items-center justify-between rounded border px-3 py-1.5"
      >
        <div class="text-sm">{{ ref.name }}</div>
        <div v-if="!isProtected" class="flex items-center gap-0.5 ml-3 shrink-0">
          <Button size="icon" variant="ghost" class="h-7 w-7 cursor-pointer text-muted-foreground/40 hover:text-foreground" @click="onEdit(ref.id)">
            <Pencil class="h-3.5 w-3.5" />
          </Button>
          <Button size="icon" variant="ghost" class="h-7 w-7 cursor-pointer text-muted-foreground/40 hover:text-destructive" @click="onDelete(ref.id)">
            <Trash2 class="h-3.5 w-3.5" />
          </Button>
        </div>
      </div>
    </div>

    <div v-if="!isProtected">
      <TooltipProvider>
        <Tooltip>
          <TooltipTrigger as-child>
            <Button size="icon" variant="outline" @click="createDialogOpen = true" class="h-8 w-8 cursor-pointer">
              <Plus class="h-4 w-4" />
            </Button>
          </TooltipTrigger>
          <TooltipContent>Add Reference</TooltipContent>
        </Tooltip>
      </TooltipProvider>
    </div>

    <!-- Create/Edit Dialog -->
    <SaveReferenceDialog
      v-model:open="createDialogOpen"
      :skill-id="skillId"
      :error="createError"
      :submitting="createSubmitting"
      @create="handleCreate"
    />
    <SaveReferenceDialog
      v-model:open="editDialogOpen"
      :skill-id="skillId"
      :reference-id="editingReferenceId"
      :error="editError"
      :submitting="editSubmitting"
      @edit="handleEdit"
    />
    <DeleteReferenceDialog
      v-model:open="deleteDialogOpen"
      :error="deleteError"
      :submitting="deleteSubmitting"
      @confirm="handleDelete"
    />
  </div>
</template>

/**
 * Section component that displays and manages skill references within a skill's expanded content.
 * Provides inline list display with add/edit/delete capabilities.
 */
<script setup lang="ts">
import { ref, toRef, watch, onMounted } from 'vue'
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import SaveReferenceDialog from './SaveReferenceDialog.vue'
import DeleteReferenceDialog from './DeleteReferenceDialog.vue'
import { useReferenceList, useReferenceCrud } from '@/composables/references'

interface Props {
  /** The skill's unique identifier. */
  skillId: string
  /** Whether the skill is protected (disables mutations). */
  isProtected: boolean
}

const props = defineProps<Props>()

const skillIdRef = toRef(props, 'skillId')
const { references, loading, loadReferences } = useReferenceList(skillIdRef)

const createDialogOpen = ref(false)
const createError = ref<string | undefined>()
const createSubmitting = ref(false)
const editDialogOpen = ref(false)
const editingReferenceId = ref<string | undefined>()
const editError = ref<string | undefined>()
const editSubmitting = ref(false)
const deleteDialogOpen = ref(false)
const deletingReferenceId = ref<string | undefined>()
const deleteError = ref<string | undefined>()
const deleteSubmitting = ref(false)

const { handleCreate, handleEdit, handleDelete } = useReferenceCrud({
  skillId: skillIdRef,
  editingReferenceId,
  createDialogOpen,
  createError,
  createSubmitting,
  editDialogOpen,
  editError,
  editSubmitting,
  deleteDialogOpen,
  deletingReferenceId,
  deleteError,
  deleteSubmitting,
  onDataChanged: loadReferences,
})

function onEdit(referenceId: string) {
  editingReferenceId.value = referenceId
  editDialogOpen.value = true
}

function onDelete(referenceId: string) {
  deletingReferenceId.value = referenceId
  deleteDialogOpen.value = true
}

watch(skillIdRef, loadReferences)
onMounted(loadReferences)
</script>
