<template>
  <BaseExpandedContent
    :is-loading="isLoading"
    :has-data="!!skill"
  >
    <div v-if="skill" class="space-y-4">
      <div class="max-w-4xl space-y-4">
        <!-- Details section -->
        <CollapsibleSection title="Details" :default-expanded="true" :show-edit="!skill.isProtected" @edit="$emit('edit', skill.id)">
          <dl class="space-y-1 text-sm">
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Name:</dt>
              <dd>{{ skill.name }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Description:</dt>
              <dd>{{ skill.description }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Created:</dt>
              <dd>{{ formatDate(skill.createdAt) }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Last Updated:</dt>
              <dd>{{ formatDate(skill.updatedAt) }}</dd>
            </div>
          </dl>
        </CollapsibleSection>

        <!-- System Prompt section -->
        <CollapsibleSection title="System Prompt" :default-expanded="false">
          <div class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded border p-4 max-h-64" v-html="renderedSystemPrompt" />
        </CollapsibleSection>

        <!-- Response Template section -->
        <CollapsibleSection v-if="skill.responseTemplate" title="Response Template" :default-expanded="false">
          <div class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded border p-4 max-h-64" v-html="renderedResponseTemplate" />
        </CollapsibleSection>

        <!-- References section -->
        <CollapsibleSection title="References" :default-expanded="false">
          <div class="space-y-3">
            <div v-if="referencesLoading" class="text-sm text-muted-foreground">Loading references...</div>
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
                <div v-if="!skill.isProtected" class="flex items-center gap-0.5 ml-3 shrink-0">
                  <Button size="icon" variant="ghost" class="h-7 w-7 cursor-pointer text-muted-foreground/40 hover:text-foreground" @click="onEditReference(ref.id)">
                    <Pencil class="h-3.5 w-3.5" />
                  </Button>
                  <Button size="icon" variant="ghost" class="h-7 w-7 cursor-pointer text-muted-foreground/40 hover:text-destructive" @click="onDeleteReference(ref.id)">
                    <Trash2 class="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
            </div>

            <div v-if="!skill.isProtected">
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
              :skill-id="skill.id"
              :error="createError"
              :submitting="createSubmitting"
              @create="handleCreate"
            />
            <SaveReferenceDialog
              v-model:open="editDialogOpen"
              :skill-id="skill.id"
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
        </CollapsibleSection>

        <!-- Tools section -->
        <CollapsibleSection v-if="skill.toolNames.length" title="Tools" :default-expanded="true">
          <div class="flex flex-wrap gap-2">
            <Badge v-for="tool in skill.toolNames" :key="tool" variant="outline">
              {{ tool }}
            </Badge>
          </div>
        </CollapsibleSection>
        <div v-else>
          <h3 class="font-semibold mb-2">Tools</h3>
          <div class="text-sm text-muted-foreground italic">Not configured</div>
        </div>

        <!-- Metadata section -->
        <CollapsibleSection title="Metadata" :default-expanded="false">
          <dl class="space-y-1 text-sm">
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">ID:</dt>
              <dd class="font-mono text-xs">{{ skill.id }}</dd>
            </div>
          </dl>
        </CollapsibleSection>
      </div>

      <!-- Danger Zone -->
      <div v-if="!skill.isProtected" class="relative pt-4 mt-4 border-t border-destructive/20">
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-semibold text-destructive">Danger Zone</h3>
            <p class="text-sm text-muted-foreground mt-1">Permanently remove this skill from the system.</p>
          </div>
          <DestructiveButton
            size="sm"
            @click="$emit('delete', skill.id)"
          >
            Delete Skill
          </DestructiveButton>
        </div>
      </div>
    </div>
  </BaseExpandedContent>
</template>

/**
 * Expanded content panel for a skill, showing details, system prompt,
 * references, tools, metadata, and a danger zone with a delete button.
 */
<script setup lang="ts">
import { ref, toRef, watch, onMounted } from 'vue'
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { BaseExpandedContent, CollapsibleSection, DestructiveButton } from '@/components/common'
import SaveReferenceDialog from './dialogs/SaveReferenceDialog.vue'
import DeleteReferenceDialog from './dialogs/DeleteReferenceDialog.vue'
import { useRenderedMarkdown } from '@/composables/ui'
import { useSkillFormatters } from '@/composables/skills'
import { useReferenceList, useReferenceCrud } from '@/composables/references'
import type { Skill } from '@/types/skill'

interface Props {
  /** The full skill data to display, or undefined while loading. */
  skill: Skill | undefined
  /** Whether the skill data is currently loading. */
  isLoading: boolean
}

const props = defineProps<Props>()
defineEmits<{
  /**
   * Emitted when the edit button is clicked.
   *
   * @param skillId - The ID of the skill to edit.
   */
  edit: [skillId: string]
  /**
   * Emitted when the delete button is clicked.
   *
   * @param skillId - The ID of the skill to delete.
   */
  delete: [skillId: string]
}>()

const { formatDate } = useSkillFormatters()

const renderedSystemPrompt = useRenderedMarkdown(() => props.skill?.systemPrompt ?? '')
const renderedResponseTemplate = useRenderedMarkdown(() => props.skill?.responseTemplate ?? '')

// --- References ---

const skillIdRef = toRef(() => props.skill?.id ?? '')

const { references, loading: referencesLoading, loadReferences } = useReferenceList(skillIdRef)

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

function onEditReference(referenceId: string) {
  editingReferenceId.value = referenceId
  editDialogOpen.value = true
}

function onDeleteReference(referenceId: string) {
  deletingReferenceId.value = referenceId
  deleteDialogOpen.value = true
}

watch(skillIdRef, loadReferences)
onMounted(loadReferences)
</script>