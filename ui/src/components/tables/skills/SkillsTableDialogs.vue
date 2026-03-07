<template>
  <!-- Create Skill Dialog -->
  <Dialog v-model:open="createDialogOpen">
    <DialogContent class="sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>Create Skill</DialogTitle>
        <DialogDescription>Add a new skill to the agent.</DialogDescription>
      </DialogHeader>
      <form @submit.prevent="handleCreateSubmit" class="space-y-4">
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
          <Label for="create-system-prompt">System Prompt</Label>
          <Textarea
            id="create-system-prompt"
            v-model="createForm.systemPrompt"
            placeholder="LLM instructions for this skill"
            required
            rows="5"
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
        <div class="space-y-2">
          <Label for="create-planning-prompt">Planning Prompt (optional)</Label>
          <Textarea
            id="create-planning-prompt"
            v-model="createForm.planningPrompt"
            placeholder="Multi-step task decomposition instructions"
            rows="3"
          />
        </div>

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
    <DialogContent class="sm:max-w-lg">
      <DialogHeader>
        <DialogTitle>Edit Skill</DialogTitle>
        <DialogDescription>Update skill configuration.</DialogDescription>
      </DialogHeader>
      <form @submit.prevent="handleEditSubmit" class="space-y-4">
        <div class="space-y-2">
          <Label for="edit-name">Name</Label>
          <Input id="edit-name" v-model="editForm.name" />
        </div>
        <div class="space-y-2">
          <Label for="edit-description">Description</Label>
          <Textarea id="edit-description" v-model="editForm.description" rows="3" />
        </div>
        <div class="space-y-2">
          <Label for="edit-system-prompt">System Prompt</Label>
          <Textarea id="edit-system-prompt" v-model="editForm.systemPrompt" rows="5" />
        </div>
        <div class="space-y-2">
          <Label for="edit-tool-names">Tool Names (comma-separated)</Label>
          <Input id="edit-tool-names" v-model="editToolNamesInput" />
        </div>
        <div class="space-y-2">
          <Label for="edit-planning-prompt">Planning Prompt</Label>
          <Textarea id="edit-planning-prompt" v-model="editForm.planningPrompt" rows="3" />
        </div>

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

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { DestructiveButton } from '@/components/common'
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
  createDialogOpen: boolean
  createError?: string
  createSubmitting: boolean
  editDialogOpen: boolean
  editError?: string
  editSubmitting: boolean
  editingSkillId?: string
  deleteDialogOpen: boolean
  deleteError?: string
  deleteSubmitting: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:createDialogOpen': [value: boolean]
  'update:editDialogOpen': [value: boolean]
  'update:deleteDialogOpen': [value: boolean]
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

// Reset create form when dialog opens
watch(() => props.createDialogOpen, (open) => {
  if (open) {
    createForm.name = ''
    createForm.description = ''
    createForm.systemPrompt = ''
    createForm.planningPrompt = ''
    createToolNamesInput.value = ''
  }
})

const editLoading = ref(false)

// Fetch skill data independently when edit dialog opens
watch(() => props.editDialogOpen, async (open) => {
  if (open && props.editingSkillId) {
    editLoading.value = true
    try {
      const skill = await skillService.getSkillById(props.editingSkillId)
      editForm.name = skill.name
      editForm.description = skill.description
      editForm.systemPrompt = skill.systemPrompt
      editForm.planningPrompt = skill.planningPrompt || ''
      editToolNamesInput.value = skill.toolNames.join(', ')
    } catch {
      emit('update:editDialogOpen', false)
    } finally {
      editLoading.value = false
    }
  }
})

function parseToolNames(input: string): string[] {
  return input.split(',').map(t => t.trim()).filter(t => t.length > 0)
}

function handleCreateSubmit() {
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

function handleEditSubmit() {
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
