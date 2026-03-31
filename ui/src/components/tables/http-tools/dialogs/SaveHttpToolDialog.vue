<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-4xl max-h-[85vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <DialogTitle>Edit HTTP Tool</DialogTitle>
        <DialogDescription>Update tool configuration.</DialogDescription>
      </DialogHeader>
      <div v-if="loading" class="flex items-center justify-center py-8">
        <span class="text-sm text-muted-foreground">Loading tool...</span>
      </div>
      <form v-else @submit.prevent="handleSubmit" class="flex flex-col min-h-0 flex-1">
        <div class="flex-1 overflow-y-auto px-1 py-2">
          <HttpToolDetailsForm
            ref="formRef"
            v-model="form"
            @valid="(v: boolean) => isFormValid = v"
          />
        </div>

        <div v-if="validationError" class="text-sm text-destructive mt-2">{{ validationError }}</div>
        <div v-if="error" class="text-sm text-destructive mt-2">{{ error }}</div>

        <DialogFooter class="pt-4">
          <Button type="button" variant="outline" @click="open = false" class="cursor-pointer">
            Cancel
          </Button>
          <Button type="submit" :disabled="submitting || !isFormValid" class="cursor-pointer">
            {{ submitting ? 'Saving...' : 'Save' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

/**
 * Edit-only http tool form dialog. Fetches existing tool data on open
 * and emits an edit event with the updated fields on submit.
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
import { httpToolService } from '@/services/httpTool'
import HttpToolDetailsForm from '../HttpToolDetailsForm.vue'
import type { HttpToolFormData, UpdateHttpToolRequest } from '@/types/http-tool'

interface Props {
  /** The ID of the tool to edit. */
  toolId: string
  /** Error message from the submit operation, if any. */
  error?: string
  /** Whether a submit request is in flight. */
  submitting: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'edit': [data: UpdateHttpToolRequest]
}>()

const open = defineModel<boolean>('open', { required: true })

const form = ref<HttpToolFormData>({
  name: '',
  description: '',
  endpointUrl: '',
  httpMethod: 'GET',
  headers: {},
  parameters: [],
  timeoutSeconds: 30,
  maxResponseLength: 8000,
})

const formRef = ref<InstanceType<typeof HttpToolDetailsForm> | null>(null)
const isFormValid = ref(false)
const validationError = ref('')
const loading = ref(false)

watch(open, async (isOpen) => {
  if (!isOpen) return

  validationError.value = ''
  loading.value = true
  try {
    const tool = await httpToolService.getHttpToolById(props.toolId)
    form.value = {
      name: tool.name,
      description: tool.description,
      endpointUrl: tool.endpointUrl,
      httpMethod: tool.httpMethod,
      headers: { ...tool.headers },
      parameters: tool.parameters.map((p) => ({ ...p })),
      timeoutSeconds: tool.timeoutSeconds,
      maxResponseLength: tool.maxResponseLength,
    }
  } catch {
    open.value = false
  } finally {
    loading.value = false
  }
}, { immediate: true })

function handleSubmit() {
  if (!isFormValid.value) {
    validationError.value = 'Name, description, and endpoint URL are required.'
    return
  }
  validationError.value = ''

  const data: UpdateHttpToolRequest = {
    name: form.value.name,
    description: form.value.description,
    endpointUrl: form.value.endpointUrl,
    httpMethod: form.value.httpMethod,
    headers: formRef.value?.getHeaders() ?? {},
    parameters: [...form.value.parameters],
    timeoutSeconds: form.value.timeoutSeconds,
    maxResponseLength: form.value.maxResponseLength,
  }

  emit('edit', data)
}
</script>
