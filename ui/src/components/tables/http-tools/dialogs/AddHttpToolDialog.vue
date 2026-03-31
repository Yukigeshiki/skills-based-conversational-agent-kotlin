<template>
  <Dialog :open="props.open" @update:open="handleOpenChange">
    <DialogContent class="sm:max-w-4xl max-h-[85vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <DialogTitle>Create HTTP Tool</DialogTitle>
        <DialogDescription>Configure a new HTTP-based tool for the agent.</DialogDescription>
      </DialogHeader>
      <form @submit.prevent="handleSubmit" class="flex flex-col min-h-0 flex-1">
        <div class="flex-1 overflow-y-auto px-1 py-2">
          <HttpToolDetailsForm
            ref="formRef"
            v-model="form"
            @valid="(v: boolean) => isFormValid = v"
          />
        </div>

        <div v-if="error" class="text-sm text-destructive mt-2">{{ error }}</div>

        <DialogFooter class="pt-4">
          <Button type="button" variant="outline" @click="handleOpenChange(false)" class="cursor-pointer">
            Cancel
          </Button>
          <Button type="submit" :disabled="submitting || !isFormValid" class="cursor-pointer">
            {{ submitting ? 'Creating...' : 'Create Tool' }}
          </Button>
        </DialogFooter>
      </form>
    </DialogContent>
  </Dialog>
</template>

/** Single-form dialog for creating a new http tool. */
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
import HttpToolDetailsForm from '../HttpToolDetailsForm.vue'
import type { HttpToolFormData, CreateHttpToolRequest } from '@/types/http-tool'

interface Props {
  open: boolean
  error?: string
  submitting: boolean
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  'create': [data: CreateHttpToolRequest]
}>()

function defaultForm(): HttpToolFormData {
  return {
    name: '',
    description: '',
    endpointUrl: '',
    httpMethod: 'GET',
    headers: {},
    parameters: [],
    timeoutSeconds: 30,
    maxResponseLength: 8000,
  }
}

const form = ref<HttpToolFormData>(defaultForm())
const formRef = ref<InstanceType<typeof HttpToolDetailsForm> | null>(null)
const isFormValid = ref(false)

watch(() => props.open, (isOpen) => {
  if (isOpen) {
    form.value = defaultForm()
    isFormValid.value = false
  }
})

function handleOpenChange(value: boolean) {
  emit('update:open', value)
}

function handleSubmit() {
  if (!isFormValid.value || props.submitting) return

  const data: CreateHttpToolRequest = {
    name: form.value.name,
    description: form.value.description,
    endpointUrl: form.value.endpointUrl,
    httpMethod: form.value.httpMethod,
    headers: formRef.value?.getHeaders(),
    parameters: form.value.parameters.length > 0 ? [...form.value.parameters] : undefined,
    timeoutSeconds: form.value.timeoutSeconds,
    maxResponseLength: form.value.maxResponseLength,
  }

  emit('create', data)
}
</script>
