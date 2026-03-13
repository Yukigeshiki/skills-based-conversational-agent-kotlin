<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-7xl">
      <DialogHeader>
        <DialogTitle>Edit Skill</DialogTitle>
        <DialogDescription>Update skill configuration.</DialogDescription>
      </DialogHeader>
      <div v-if="loading" class="flex items-center justify-center py-8">
        <span class="text-sm text-muted-foreground">Loading skill...</span>
      </div>
      <form v-else @submit.prevent="handleSubmit" class="space-y-4">
        <SkillDetailsForm
          v-model="form"
          :system-prompt-rows="12"
          :response-template-rows="8"
          @valid="(v: boolean) => isFormValid = v"
        />

        <div v-if="validationError" class="text-sm text-destructive">{{ validationError }}</div>
        <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

        <DialogFooter>
          <Button type="button" variant="outline" @click="open = false" class="cursor-pointer">
            Cancel
          </Button>
          <Button type="submit" :disabled="submitting" class="cursor-pointer">
            {{ submitting ? 'Saving...' : 'Save' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

/**
 * Edit-only skill form dialog. Fetches existing skill data on open and emits
 * an edit event with the updated fields on submit.
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { skillService } from '@/services'
import SkillDetailsForm from './SkillDetailsForm.vue'
import type { SkillFormData, UpdateSkillRequest } from '@/types/skill'

interface Props {
  /** The ID of the skill to edit. */
  skillId: string
  /** Error message from the submit operation, if any. */
  error?: string
  /** Whether a submit request is in flight. */
  submitting: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'edit': [data: UpdateSkillRequest]
}>()

const open = defineModel<boolean>('open', { required: true })

const form = ref<SkillFormData>({
  name: '',
  description: '',
  systemPrompt: '',
  responseTemplate: '',
  toolNames: [],
})

const isFormValid = ref(false)
const validationError = ref('')
const loading = ref(false)

watch(open, async (isOpen) => {
  if (!isOpen) return

  validationError.value = ''
  loading.value = true
  try {
    const skill = await skillService.getSkillById(props.skillId)
    form.value = {
      name: skill.name,
      description: skill.description,
      systemPrompt: skill.systemPrompt,
      responseTemplate: skill.responseTemplate ?? '',
      toolNames: [...skill.toolNames],
    }
  } catch {
    open.value = false
  } finally {
    loading.value = false
  }
})

function handleSubmit() {
  if (!isFormValid.value) {
    validationError.value = 'System prompt is required.'
    return
  }
  validationError.value = ''

  const data: UpdateSkillRequest = {
    name: form.value.name,
    description: form.value.description,
    systemPrompt: form.value.systemPrompt,
    responseTemplate: form.value.responseTemplate.trim() || undefined,
    toolNames: [...form.value.toolNames],
  }

  emit('edit', data)
}
</script>
