<template>
  <div v-if="loading" class="space-y-4">
    <Skeleton class="h-8 w-48" />
    <Skeleton class="h-48 w-full" />
  </div>

  <div v-else-if="error" class="bg-card border rounded-lg p-8 shadow-sm">
    <div class="text-center text-destructive">{{ error }}</div>
  </div>

  <div v-else class="space-y-4">
    <div class="flex items-center justify-end">
      <PreviewToggleButton :previewing="previewing" @toggle="previewing = !previewing" />
    </div>
    <Textarea
      v-show="!previewing"
      v-model="systemPrompt"
      placeholder="Enter a system prompt to define the agent's personality..."
      class="min-h-125 font-mono text-sm"
    />
    <!-- eslint-disable-next-line vue/no-v-html -- sanitised by DOMPurify via useRenderedMarkdown -->
    <div v-show="previewing" class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3 min-h-125 max-h-125" v-html="renderedPrompt" />
    <div class="flex items-center gap-3">
      <Button :disabled="!isDirty || saving" @click="save">
        <Loader2 v-if="saving" class="h-4 w-4 animate-spin" />
        Save
      </Button>
      <span v-if="saveSuccess" class="text-sm text-green-600 dark:text-green-400">
        Saved successfully
      </span>
      <span v-if="saveError" class="text-sm text-destructive">
        {{ saveError }}
      </span>
    </div>
  </div>
</template>

/** Editor for the identity system prompt with loading, saving, and dirty tracking. */
<script setup lang="ts">
import { onMounted, onUnmounted, ref, computed } from 'vue'
import { useRenderedMarkdown } from '@/composables/ui'
import { identityService } from '@/services/identity'
import { Textarea } from '@/components/ui/textarea'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { PreviewToggleButton } from '@/components/common'
import { Loader2 } from 'lucide-vue-next'

const loading = ref(true)
const previewing = ref(true)
const error = ref<string | null>(null)
const systemPrompt = ref('')
const savedPrompt = ref('')
const saving = ref(false)
const saveSuccess = ref(false)
const saveError = ref<string | null>(null)

let successTimeout: ReturnType<typeof setTimeout> | null = null

const isDirty = computed(() => systemPrompt.value !== savedPrompt.value)
const renderedPrompt = useRenderedMarkdown(() => systemPrompt.value)

async function load() {
  loading.value = true
  error.value = null

  try {
    const identity = await identityService.getIdentity()
    systemPrompt.value = identity.systemPrompt
    savedPrompt.value = identity.systemPrompt
  } catch (err) {
    console.error('Failed to load identity configuration:', err)
    error.value = err instanceof Error ? err.message : 'Failed to load identity configuration'
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  saveSuccess.value = false
  saveError.value = null

  try {
    const updated = await identityService.updateIdentity({ systemPrompt: systemPrompt.value })
    savedPrompt.value = updated.systemPrompt
    saveSuccess.value = true
    successTimeout = setTimeout(() => { saveSuccess.value = false }, 3000)
  } catch (err) {
    console.error('Failed to save identity configuration:', err)
    saveError.value = err instanceof Error ? err.message : 'Failed to save'
  } finally {
    saving.value = false
  }
}

onMounted(load)
onUnmounted(() => {
  if (successTimeout) clearTimeout(successTimeout)
})
</script>
