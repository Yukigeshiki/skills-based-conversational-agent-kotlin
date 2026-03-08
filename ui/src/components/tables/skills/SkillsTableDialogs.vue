<template>
  <!-- Create Skill Dialog -->
  <Dialog v-model:open="createDialogOpen">
    <DialogContent class="sm:max-w-7xl">
      <DialogHeader>
        <DialogTitle>Create Skill</DialogTitle>
        <DialogDescription>Add a new skill to the agent.</DialogDescription>
      </DialogHeader>
      <form @submit.prevent="handleCreateSubmit" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <Label for="create-name">Name</Label>
            <Input
              id="create-name"
              v-model="createForm.name"
              placeholder="e.g. general-assistant"
              required
            />
          </div>
          <div class="space-y-2">
            <Label for="create-tool-names">Tool Names (comma-separated)</Label>
            <Input
              id="create-tool-names"
              v-model="createToolNamesInput"
              placeholder="e.g. dateTimeTool, searchTool"
            />
          </div>
        </div>
        <div class="space-y-2">
          <Label for="create-description">Description</Label>
          <Textarea
            id="create-description"
            v-model="createForm.description"
            placeholder="Describe what this skill does (used for routing)"
            required
            rows="3"
          />
        </div>
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <Label for="create-system-prompt">System Prompt</Label>
            <PreviewToggleButton :previewing="createSystemPromptPreview" @toggle="createSystemPromptPreview = !createSystemPromptPreview" />
          </div>
          <Textarea
            v-show="!createSystemPromptPreview"
            id="create-system-prompt"
            v-model="createForm.systemPrompt"
            placeholder="LLM instructions for this skill"
            rows="16"
          />
          <div v-show="createSystemPromptPreview" class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="min-height: 24rem; max-height: 24rem;" v-html="renderMarkdown(createForm.systemPrompt)" />
        </div>
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <Label for="create-planning-prompt">Planning Prompt (optional)</Label>
            <PreviewToggleButton :previewing="createPlanningPromptPreview" @toggle="createPlanningPromptPreview = !createPlanningPromptPreview" />
          </div>
          <Textarea
            v-show="!createPlanningPromptPreview"
            id="create-planning-prompt"
            v-model="createForm.planningPrompt"
            placeholder="Multi-step task decomposition instructions"
            rows="6"
          />
          <div v-show="createPlanningPromptPreview" class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="min-height: 9rem; max-height: 9rem;" v-html="renderMarkdown(createForm.planningPrompt)" />
        </div>

        <div v-if="createValidationError" class="text-sm text-destructive">{{ createValidationError }}</div>
        <div v-if="createError" class="text-sm text-destructive">{{ createError }}</div>

        <DialogFooter>
          <Button type="button" variant="outline" @click="createDialogOpen = false" class="cursor-pointer">
            Cancel
          </Button>
          <Button type="submit" :disabled="createSubmitting" class="cursor-pointer">
            {{ createSubmitting ? 'Creating...' : 'Create' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>

  <!-- Edit Skill Dialog -->
  <Dialog v-model:open="editDialogOpen">
    <DialogContent class="sm:max-w-7xl">
      <DialogHeader>
        <DialogTitle>Edit Skill</DialogTitle>
        <DialogDescription>Update skill configuration.</DialogDescription>
      </DialogHeader>
      <form @submit.prevent="handleEditSubmit" class="space-y-4">
        <div class="grid grid-cols-2 gap-4">
          <div class="space-y-2">
            <Label for="edit-name">Name</Label>
            <Input id="edit-name" v-model="editForm.name" />
          </div>
          <div class="space-y-2">
            <Label for="edit-tool-names">Tool Names (comma-separated)</Label>
            <Input id="edit-tool-names" v-model="editToolNamesInput" />
          </div>
        </div>
        <div class="space-y-2">
          <Label for="edit-description">Description</Label>
          <Textarea id="edit-description" v-model="editForm.description" rows="3" />
        </div>
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <Label for="edit-system-prompt">System Prompt</Label>
            <PreviewToggleButton :previewing="editSystemPromptPreview" @toggle="editSystemPromptPreview = !editSystemPromptPreview" />
          </div>
          <Textarea v-show="!editSystemPromptPreview" id="edit-system-prompt" v-model="editForm.systemPrompt" rows="16" />
          <div v-show="editSystemPromptPreview" class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="min-height: 24rem; max-height: 24rem;" v-html="renderMarkdown(editForm.systemPrompt)" />
        </div>
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <Label for="edit-planning-prompt">Planning Prompt</Label>
            <PreviewToggleButton :previewing="editPlanningPromptPreview" @toggle="editPlanningPromptPreview = !editPlanningPromptPreview" />
          </div>
          <Textarea v-show="!editPlanningPromptPreview" id="edit-planning-prompt" v-model="editForm.planningPrompt" rows="6" />
          <div v-show="editPlanningPromptPreview" class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="min-height: 9rem; max-height: 9rem;" v-html="renderMarkdown(editForm.planningPrompt)" />
        </div>

        <div v-if="editValidationError" class="text-sm text-destructive">{{ editValidationError }}</div>
        <div v-if="editError" class="text-sm text-destructive">{{ editError }}</div>

        <DialogFooter>
          <Button type="button" variant="outline" @click="editDialogOpen = false" class="cursor-pointer">
            Cancel
          </Button>
          <Button type="submit" :disabled="editSubmitting" class="cursor-pointer">
            {{ editSubmitting ? 'Saving...' : 'Save' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>

  <!-- Delete Skill Dialog -->
  <Dialog v-model:open="deleteDialogOpen">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>Delete Skill</DialogTitle>
        <DialogDescription>
          Are you sure you want to delete this skill? This action cannot be undone.
        </DialogDescription>
      </DialogHeader>

      <div v-if="deleteError" class="text-sm text-destructive">{{ deleteError }}</div>

      <DialogFooter>
        <Button type="button" variant="outline" @click="deleteDialogOpen = false" class="cursor-pointer">
          Cancel
        </Button>
        <DestructiveButton :disabled="deleteSubmitting" @click="$emit('confirm-delete')">
          {{ deleteSubmitting ? 'Deleting...' : 'Delete' }}
        </DestructiveButton>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

/**
 * Skill CRUD dialogs (create, edit, delete) with form state management.
 * Fetches existing skill data when the edit dialog opens and resets forms on close.
 */
<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { renderMarkdown } from '@/composables/ui'
import { Button } from '@/components/ui/button'
import { DestructiveButton, PreviewToggleButton } from '@/components/common'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { skillService } from '@/services'
import type { CreateSkillRequest, UpdateSkillRequest } from '@/types/skill'

interface Props {
  /** Error message from the create operation, if any. */
  createError?: string
  /** Whether a create request is in flight. */
  createSubmitting: boolean
  /** Error message from the edit operation, if any. */
  editError?: string
  /** Whether an edit request is in flight. */
  editSubmitting: boolean
  /** The ID of the skill being edited. */
  editingSkillId?: string
  /** Error message from the delete operation, if any. */
  deleteError?: string
  /** Whether a delete request is in flight. */
  deleteSubmitting: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'create': [data: CreateSkillRequest]
  'edit': [data: UpdateSkillRequest]
  'confirm-delete': []
}>()

const createDialogOpen = defineModel<boolean>('createDialogOpen')
const editDialogOpen = defineModel<boolean>('editDialogOpen')
const deleteDialogOpen = defineModel<boolean>('deleteDialogOpen')

const createForm = reactive({
  name: '',
  description: '',
  systemPrompt: '',
  planningPrompt: '',
})
const createToolNamesInput = ref('')

const editForm = reactive({
  name: '',
  description: '',
  systemPrompt: '',
  planningPrompt: '',
})
const editToolNamesInput = ref('')

const createSystemPromptPreview = ref(false)
const createPlanningPromptPreview = ref(false)
const editSystemPromptPreview = ref(false)
const editPlanningPromptPreview = ref(false)

// Reset create form when dialog opens
watch(createDialogOpen, (open) => {
  if (open) {
    createForm.name = ''
    createForm.description = ''
    createForm.systemPrompt = ''
    createForm.planningPrompt = ''
    createToolNamesInput.value = ''
    createSystemPromptPreview.value = false
    createPlanningPromptPreview.value = false
  }
})

const editLoading = ref(false)

// Fetch skill data independently when edit dialog opens
watch(editDialogOpen, async (open) => {
  if (open && props.editingSkillId) {
    editSystemPromptPreview.value = false
    editPlanningPromptPreview.value = false
    editLoading.value = true
    try {
      const skill = await skillService.getSkillById(props.editingSkillId)
      editForm.name = skill.name
      editForm.description = skill.description
      editForm.systemPrompt = skill.systemPrompt
      editForm.planningPrompt = skill.planningPrompt || ''
      editToolNamesInput.value = skill.toolNames.join(', ')
    } catch {
      editDialogOpen.value = false
    } finally {
      editLoading.value = false
    }
  }
})

/**
 * Parses a comma-separated string of tool names into a trimmed array.
 *
 * @param input - The raw comma-separated input.
 * @returns An array of non-empty tool name strings.
 */
function parseToolNames(input: string): string[] {
  return input.split(',').map(t => t.trim()).filter(t => t.length > 0)
}

const createValidationError = ref('')
const editValidationError = ref('')

/** Builds a {@link CreateSkillRequest} from the form state and emits a `create` event. */
function handleCreateSubmit() {
  if (!createForm.systemPrompt.trim()) {
    createValidationError.value = 'System prompt is required.'
    createSystemPromptPreview.value = false
    return
  }
  createValidationError.value = ''
  const data: CreateSkillRequest = {
    name: createForm.name,
    description: createForm.description,
    systemPrompt: createForm.systemPrompt,
    toolNames: parseToolNames(createToolNamesInput.value),
  }
  if (createForm.planningPrompt.trim()) {
    data.planningPrompt = createForm.planningPrompt
  }
  emit('create', data)
}

/** Builds an {@link UpdateSkillRequest} from the form state and emits an `edit` event. */
function handleEditSubmit() {
  if (!editForm.systemPrompt.trim()) {
    editValidationError.value = 'System prompt is required.'
    editSystemPromptPreview.value = false
    return
  }
  editValidationError.value = ''
  const data: UpdateSkillRequest = {
    name: editForm.name,
    description: editForm.description,
    systemPrompt: editForm.systemPrompt,
    toolNames: parseToolNames(editToolNamesInput.value),
  }
  if (editForm.planningPrompt.trim()) {
    data.planningPrompt = editForm.planningPrompt
  }
  emit('edit', data)
}
</script>
