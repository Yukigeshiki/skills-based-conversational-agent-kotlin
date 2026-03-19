<!--
/**
 * Shared form fields for skill creation and editing.
 *
 * Renders name, tool selector, description, system prompt (with markdown preview),
 * and response template (with markdown preview). Emits validity status on change.
 */
-->
<template>
  <div class="space-y-4">
    <div class="space-y-2">
      <Label for="skill-name">Name</Label>
      <Input
        id="skill-name"
        v-model="model.name"
        placeholder="e.g. general-assistant"
        required
      />
    </div>
    <ToolSelector
      v-model="model.toolNames"
      label="Tool Names"
    />
    <div class="flex items-center gap-3">
      <Switch
        :checked="model.requiresApproval"
        @update:checked="model.requiresApproval = $event"
      />
      <Label for="skill-requires-approval">Require approval before tool execution</Label>
    </div>
    <div class="space-y-2">
      <Label for="skill-description">Description (include query examples for better matching)</Label>
      <Textarea
        id="skill-description"
        v-model="model.description"
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
        v-model="model.systemPrompt"
        placeholder="LLM instructions for this skill"
        :rows="systemPromptRows"
      />
      <div
        v-show="systemPromptPreview"
        class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3"
        :style="{ minHeight: systemPromptPreviewHeight, maxHeight: systemPromptPreviewHeight }"
        v-html="renderedSystemPrompt"
      />
    </div>
    <div class="space-y-2">
      <div class="flex items-center justify-between">
        <Label for="skill-response-template">Response Template (optional, max 1000 tokens)</Label>
        <PreviewToggleButton :previewing="responseTemplatePreview" @toggle="responseTemplatePreview = !responseTemplatePreview" />
      </div>
      <Textarea
        v-show="!responseTemplatePreview"
        id="skill-response-template"
        v-model="model.responseTemplate"
        placeholder="Define a template for how this skill should structure responses"
        :rows="responseTemplateRows"
      />
      <div
        v-show="responseTemplatePreview"
        class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3"
        :style="{ minHeight: responseTemplatePreviewHeight, maxHeight: responseTemplatePreviewHeight }"
        v-html="renderedResponseTemplate"
      />
    </div>
  </div>
</template>

/**
 * Reusable skill details form used by both AddSkillDialog and SaveSkillDialog.
 * Accepts a v-model for the form data and configurable textarea row counts.
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRenderedMarkdown } from '@/composables/ui'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { PreviewToggleButton, ToolSelector } from '@/components/common'
import { Switch } from '@/components/ui/switch'
import type { SkillFormData } from '@/types/skill'

interface Props {
  /** Number of rows for the system prompt textarea. */
  systemPromptRows?: number
  /** Number of rows for the response template textarea. */
  responseTemplateRows?: number
}

const props = withDefaults(defineProps<Props>(), {
  systemPromptRows: 8,
  responseTemplateRows: 5,
})

const model = defineModel<SkillFormData>({ required: true })

const emit = defineEmits<{
  valid: [value: boolean]
}>()

const systemPromptPreview = ref(false)
const responseTemplatePreview = ref(false)

const renderedSystemPrompt = useRenderedMarkdown(() => model.value.systemPrompt)
const renderedResponseTemplate = useRenderedMarkdown(() => model.value.responseTemplate)

const systemPromptPreviewHeight = computed(() => `${props.systemPromptRows * 1.5}rem`)
const responseTemplatePreviewHeight = computed(() => `${props.responseTemplateRows * 1.5}rem`)

const isValid = computed(() =>
  !!model.value.name.trim() && !!model.value.description.trim() && !!model.value.systemPrompt.trim()
)

watch(isValid, (value) => emit('valid', value), { immediate: true })

/** Resets preview toggles to edit mode. Called by parent when resetting form state. */
function resetPreviews() {
  systemPromptPreview.value = false
  responseTemplatePreview.value = false
}

defineExpose({ resetPreviews })
</script>
