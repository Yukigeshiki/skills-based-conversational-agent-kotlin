<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-7xl">
      <DialogHeader>
        <DialogTitle>{{ isEditMode ? 'Edit Skill' : 'Create Skill' }}</DialogTitle>
        <DialogDescription>{{ isEditMode ? 'Update skill configuration.' : 'Add a new skill to the agent.' }}</DialogDescription>
      </DialogHeader>
      <div v-if="loading" class="flex items-center justify-center py-8">
        <span class="text-sm text-muted-foreground">Loading skill...</span>
      </div>
      <form v-else @submit.prevent="handleSubmit" class="space-y-4">
        <div class="space-y-2">
          <Label for="skill-name">Name</Label>
          <Input
            id="skill-name"
            v-model="form.name"
            placeholder="e.g. general-assistant"
            required
          />
        </div>
        <ToolSelector
          v-model="form.toolNames"
          label="Tool Names"
        />
        <div class="space-y-2">
          <Label for="skill-description">Description (include query examples for better matching)</Label>
          <Textarea
            id="skill-description"
            v-model="form.description"
            placeholder="Describe what this skill does (used for routing)"
            required
            rows="3"
          />
        </div>
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <Label for="skill-system-prompt">System Prompt (max 1000 tokens)</Label>
            <PreviewToggleButton :previewing="systemPromptPreview" @toggle="systemPromptPreview = !systemPromptPreview" />
          </div>
          <Textarea
            v-show="!systemPromptPreview"
            id="skill-system-prompt"
            v-model="form.systemPrompt"
            placeholder="LLM instructions for this skill"
            rows="12"
          />
          <div v-show="systemPromptPreview" class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="min-height: 18rem; max-height: 18rem;" v-html="renderMarkdown(form.systemPrompt)" />
        </div>
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <Label for="skill-response-template">Response Template (optional, max 1000 tokens)</Label>
            <PreviewToggleButton :previewing="responseTemplatePreview" @toggle="responseTemplatePreview = !responseTemplatePreview" />
          </div>
          <Textarea
            v-show="!responseTemplatePreview"
            id="skill-response-template"
            v-model="form.responseTemplate"
            placeholder="Define a template for how this skill should structure responses"
            rows="8"
          />
          <div v-show="responseTemplatePreview" class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="min-height: 12rem; max-height: 12rem;" v-html="renderMarkdown(form.responseTemplate)" />
        </div>

        <div v-if="validationError" class="text-sm text-destructive">{{ validationError }}</div>
        <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

        <DialogFooter>
          <Button type="button" variant="outline" @click="open = false" class="cursor-pointer">
            Cancel
          </Button>
          <Button type="submit" :disabled="submitting" class="cursor-pointer">
            {{ submitting ? (isEditMode ? 'Saving...' : 'Creating...') : (isEditMode ? 'Save' : 'Create') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

/**
 * Unified skill form dialog for creating and editing skills.
 * When skillId is provided, operates in edit mode and fetches existing data on open.
 */
<script setup lang="ts">
import { reactive, ref, computed, watch } from 'vue'
import { renderMarkdown } from '@/composables/ui'
import { Button } from '@/components/ui/button'
import { PreviewToggleButton, ToolSelector } from '@/components/common'
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
  /** The ID of the skill to edit. When absent, the dialog operates in create mode. */
  skillId?: string
  /** Error message from the submit operation, if any. */
  error?: string
  /** Whether a submit request is in flight. */
  submitting: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'create': [data: CreateSkillRequest]
  'edit': [data: UpdateSkillRequest]
}>()

const open = defineModel<boolean>('open', { required: true })
const isEditMode = computed(() => !!props.skillId)

const form = reactive({
  name: '',
  description: '',
  systemPrompt: '',
  responseTemplate: '',
  toolNames: [] as string[],
})

const systemPromptPreview = ref(false)
const responseTemplatePreview = ref(false)
const validationError = ref('')
const loading = ref(false)

function resetForm() {
  form.name = ''
  form.description = ''
  form.systemPrompt = ''
  form.responseTemplate = ''
  form.toolNames = []
  systemPromptPreview.value = false
  responseTemplatePreview.value = false
  validationError.value = ''
}

watch(open, async (isOpen) => {
  if (!isOpen) return

  if (props.skillId) {
    systemPromptPreview.value = false
    validationError.value = ''
    loading.value = true
    try {
      const skill = await skillService.getSkillById(props.skillId)
      form.name = skill.name
      form.description = skill.description
      form.systemPrompt = skill.systemPrompt
      form.responseTemplate = skill.responseTemplate ?? ''
      form.toolNames = [...skill.toolNames]
    } catch {
      open.value = false
    } finally {
      loading.value = false
    }
  } else {
    resetForm()
  }
})

function handleSubmit() {
  if (!form.systemPrompt.trim()) {
    validationError.value = 'System prompt is required.'
    systemPromptPreview.value = false
    return
  }
  validationError.value = ''

  const data: CreateSkillRequest = {
    name: form.name,
    description: form.description,
    systemPrompt: form.systemPrompt,
    responseTemplate: form.responseTemplate.trim() || undefined,
    toolNames: [...form.toolNames],
  }

  if (isEditMode.value) {
    emit('edit', data)
  } else {
    emit('create', data)
  }
}
</script>
