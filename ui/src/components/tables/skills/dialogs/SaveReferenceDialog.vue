<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-4xl">
      <DialogHeader>
        <DialogTitle>{{ isEditMode ? 'Edit Reference' : 'Add Reference' }}</DialogTitle>
        <DialogDescription>{{ isEditMode ? 'Update reference document.' : 'Add a reference document for RAG retrieval.' }}</DialogDescription>
      </DialogHeader>
      <div v-if="loading" class="flex items-center justify-center py-8">
        <span class="text-sm text-muted-foreground">Loading reference...</span>
      </div>
      <form v-else @submit.prevent="handleSubmit" class="space-y-4">
        <div class="space-y-2">
          <Label for="reference-name">Name</Label>
          <Input
            id="reference-name"
            v-model="form.name"
            placeholder="e.g. product-documentation"
            required
          />
        </div>
        <div class="space-y-2">
          <div class="flex items-center justify-between">
            <Label for="reference-content">Content (markdown supported)</Label>
            <PreviewToggleButton :previewing="contentPreview" @toggle="contentPreview = !contentPreview" />
          </div>
          <Textarea
            v-show="!contentPreview"
            id="reference-content"
            v-model="form.content"
            placeholder="Paste reference content here..."
            required
            rows="16"
          />
          <!-- eslint-disable-next-line vue/no-v-html -- sanitised by DOMPurify via useRenderedMarkdown -->
          <div v-show="contentPreview" class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="min-height: 24rem; max-height: 24rem;" v-html="renderedContent" />
        </div>

        <div v-if="validationError" class="text-sm text-destructive">{{ validationError }}</div>
        <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

        <DialogFooter>
          <Button type="button" variant="outline" @click="open = false" class="cursor-pointer">
            Cancel
          </Button>
          <Button type="submit" :disabled="submitting" class="cursor-pointer">
            {{ submitting ? (isEditMode ? 'Saving...' : 'Adding...') : (isEditMode ? 'Save' : 'Add') }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

/**
 * Dialog for creating and editing skill reference documents.
 * When referenceId is provided, operates in edit mode and fetches existing data on open.
 */
<script setup lang="ts">
import { reactive, ref, computed, watch } from 'vue'
import { useRenderedMarkdown } from '@/composables/ui'
import { Button } from '@/components/ui/button'
import { PreviewToggleButton } from '@/components/common'
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
import { referenceService } from '@/services'
import type { CreateSkillReferenceRequest, UpdateSkillReferenceRequest } from '@/types/reference'

interface Props {
  /** The skill this reference belongs to. */
  skillId: string
  /** The ID of the reference to edit. When absent, the dialog operates in create mode. */
  referenceId?: string
  /** Error message from the submit operation, if any. */
  error?: string
  /** Whether a submit request is in flight. */
  submitting: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'create': [data: CreateSkillReferenceRequest]
  'edit': [data: UpdateSkillReferenceRequest]
}>()

const open = defineModel<boolean>('open', { required: true })
const isEditMode = computed(() => !!props.referenceId)

const form = reactive({
  name: '',
  content: '',
})

const contentPreview = ref(false)
const validationError = ref('')
const loading = ref(false)

const renderedContent = useRenderedMarkdown(() => form.content)

function resetForm() {
  form.name = ''
  form.content = ''
  contentPreview.value = false
  validationError.value = ''
}

watch(open, async (isOpen) => {
  if (!isOpen) return

  if (props.referenceId) {
    contentPreview.value = false
    validationError.value = ''
    loading.value = true
    try {
      const reference = await referenceService.getReferenceById(props.skillId, props.referenceId)
      form.name = reference.name
      form.content = reference.content
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
  if (!form.content.trim()) {
    validationError.value = 'Content is required.'
    return
  }
  validationError.value = ''

  if (isEditMode.value) {
    const data: UpdateSkillReferenceRequest = {
      name: form.name,
      content: form.content,
    }
    emit('edit', data)
  } else {
    const data: CreateSkillReferenceRequest = {
      name: form.name,
      content: form.content,
    }
    emit('create', data)
  }
}
</script>
