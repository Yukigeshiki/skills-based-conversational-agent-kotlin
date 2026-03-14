<!--
/**
 * Multi-step wizard dialog for creating a new skill.
 *
 * Step 1: Skill details — name, description, tools, system prompt, response template.
 * Step 2: References — optionally attach reference documents for RAG retrieval.
 * Step 3: Review — summary of all entered data with markdown-rendered prompts and references.
 */
-->
<template>
  <Dialog :open="props.open" @update:open="handleOpenChange">
    <DialogContent class="sm:max-w-4xl h-165 overflow-hidden p-0">
      <div class="flex h-full min-h-0">
        <!-- Left sidebar with stepper -->
        <div class="w-52 border-r bg-muted/30 p-4 overflow-y-auto shrink-0">
          <div class="mb-4">
            <h2 class="text-lg font-semibold">Create Skill</h2>
            <p class="text-sm text-muted-foreground">Complete each section</p>
          </div>
          <WizardStepper
            :steps="steps"
            :current-step-index="currentStepIndex"
            :highest-step-reached="highestStepReached"
            :allow-navigation="true"
            @navigate="goToStep"
          />
        </div>

        <!-- Right content area -->
        <div class="flex-1 flex flex-col min-h-0 min-w-0">
          <DialogHeader class="px-6 pt-6 pb-4 border-b shrink-0">
            <DialogTitle>{{ currentStepTitle }}</DialogTitle>
            <DialogDescription>{{ currentStepDescription }}</DialogDescription>
          </DialogHeader>

          <div class="flex-1 overflow-y-auto px-6 py-4">
            <!-- Step 1: Skill Details -->
            <SkillDetailsForm
              v-if="currentStepIndex === 0"
              ref="skillDetailsFormRef"
              v-model="skillForm"
              :system-prompt-rows="8"
              :response-template-rows="5"
              @valid="(v: boolean) => isStep1Valid = v"
            />

            <!-- Step 2: References -->
            <div v-else-if="currentStepIndex === 1" class="space-y-4">
              <div v-if="pendingReferences.length === 0 && !showReferenceForm" class="text-sm text-muted-foreground italic">
                No references added yet. References are optional and can also be added later.
              </div>
              <div v-if="pendingReferences.length > 0" class="space-y-2">
                <div
                  v-for="(pendingRef, i) in pendingReferences"
                  :key="i"
                  class="flex items-center justify-between rounded border px-3 py-2"
                >
                  <span class="text-sm">{{ pendingRef.name }}</span>
                  <div class="flex items-center gap-0.5 ml-3 shrink-0">
                    <Button size="icon" variant="ghost" class="h-7 w-7 cursor-pointer text-muted-foreground/40 hover:text-foreground" @click="editReference(i)">
                      <Pencil class="h-3.5 w-3.5" />
                    </Button>
                    <Button size="icon" variant="ghost" class="h-7 w-7 cursor-pointer text-muted-foreground/40 hover:text-destructive" @click="removeReference(i)">
                      <Trash2 class="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              </div>

              <!-- Inline reference form -->
              <div v-if="showReferenceForm" class="space-y-3 border rounded-lg p-4">
                <div class="space-y-2">
                  <Label for="ref-name">Name</Label>
                  <Input
                    id="ref-name"
                    v-model="referenceForm.name"
                    placeholder="e.g. product-documentation"
                  />
                </div>
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <Label for="ref-content">Content (markdown supported)</Label>
                    <PreviewToggleButton :previewing="refContentPreview" @toggle="refContentPreview = !refContentPreview" />
                  </div>
                  <Textarea
                    v-show="!refContentPreview"
                    id="ref-content"
                    v-model="referenceForm.content"
                    placeholder="Paste reference content here..."
                    rows="10"
                  />
                  <div v-show="refContentPreview" class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="min-height: 15rem; max-height: 15rem;" v-html="renderedRefContent" />
                </div>
                <div v-if="referenceFormError" class="text-sm text-destructive">{{ referenceFormError }}</div>
                <div class="flex gap-2">
                  <Button type="button" size="sm" variant="outline" @click="cancelReferenceForm" class="cursor-pointer">
                    Cancel
                  </Button>
                  <Button type="button" size="sm" @click="confirmReference" class="cursor-pointer">
                    {{ editingReferenceIndex !== null ? 'Update' : 'Add' }}
                  </Button>
                </div>
              </div>

              <TooltipProvider v-if="!showReferenceForm && pendingReferences.length < MAX_REFERENCES">
                <Tooltip>
                  <TooltipTrigger as-child>
                    <Button type="button" size="icon" variant="outline" @click="openReferenceForm" class="h-8 w-8 cursor-pointer">
                      <Plus class="h-4 w-4" />
                    </Button>
                  </TooltipTrigger>
                  <TooltipContent>Add Reference</TooltipContent>
                </Tooltip>
              </TooltipProvider>
              <p v-if="pendingReferences.length >= MAX_REFERENCES" class="text-sm text-muted-foreground">
                Maximum of {{ MAX_REFERENCES }} references reached.
              </p>
            </div>

            <!-- Step 3: Review -->
            <div v-else-if="currentStepIndex === 2" class="space-y-6">
              <!-- Skill Details -->
              <div class="space-y-2">
                <h3 class="text-base font-semibold border-b pb-2">Skill Details</h3>
                <div class="space-y-3 text-sm">
                  <div>
                    <span class="text-muted-foreground">Name:</span>
                    <span class="ml-2 font-medium">{{ skillForm.name || '—' }}</span>
                  </div>
                  <div>
                    <span class="text-muted-foreground">Description:</span>
                    <span class="ml-2 font-medium">{{ skillForm.description || '—' }}</span>
                  </div>
                  <div v-if="skillForm.toolNames.length > 0">
                    <span class="text-muted-foreground">Tools:</span>
                    <span class="ml-2">
                      <span
                        v-for="tool in skillForm.toolNames"
                        :key="tool"
                        class="inline-block bg-muted px-2 py-0.5 rounded text-xs mr-1 mb-1"
                      >
                        {{ tool }}
                      </span>
                    </span>
                  </div>
                </div>
              </div>

              <!-- System Prompt -->
              <div class="space-y-2">
                <h3 class="text-base font-semibold border-b pb-2">System Prompt</h3>
                <div class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="max-height: 16rem;" v-html="renderedReviewSystemPrompt" />
              </div>

              <!-- Response Template -->
              <div v-if="skillForm.responseTemplate.trim()" class="space-y-2">
                <h3 class="text-base font-semibold border-b pb-2">Response Template</h3>
                <div class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="max-height: 12rem;" v-html="renderedReviewResponseTemplate" />
              </div>

              <!-- References -->
              <div class="space-y-2">
                <h3 class="text-base font-semibold border-b pb-2">
                  References
                  <span class="text-sm font-normal text-muted-foreground ml-1">({{ pendingReferences.length }})</span>
                </h3>
                <div v-if="pendingReferences.length === 0" class="text-sm text-muted-foreground italic">
                  No references attached.
                </div>
                <div v-else class="space-y-4">
                  <div
                    v-for="(pendingRef, i) in pendingReferences"
                    :key="i"
                    class="space-y-2"
                  >
                    <h4 class="text-sm font-medium">{{ pendingRef.name }}</h4>
                    <div class="prose prose-sm dark:prose-invert max-w-none overflow-y-auto rounded-md border p-3" style="max-height: 12rem;" v-html="renderedReferenceContents[i] ?? ''" />
                  </div>
                </div>
              </div>
            </div>

            <div v-if="validationError" class="text-sm text-destructive mt-4">{{ validationError }}</div>
            <div v-if="error" class="text-sm text-destructive mt-4">{{ error }}</div>
          </div>

          <div class="px-6 py-4 border-t flex justify-between items-center shrink-0">
            <Button
              type="button"
              variant="ghost"
              @click="handleCancel"
              class="cursor-pointer"
            >
              Cancel
            </Button>
            <div class="flex gap-2">
              <Button
                v-if="currentStepIndex > 0"
                type="button"
                variant="outline"
                @click="goToPreviousStep"
                class="cursor-pointer"
              >
                Back
              </Button>
              <Button
                v-if="currentStepIndex < steps.length - 1"
                type="button"
                @click="goToNextStep"
                :disabled="!isCurrentStepValid"
                class="cursor-pointer"
              >
                Next
              </Button>
              <Button
                v-else
                type="button"
                @click="handleSubmit"
                :disabled="submitting"
                class="cursor-pointer"
              >
                {{ submitting ? 'Creating...' : 'Create Skill' }}
              </Button>
            </div>
          </div>
        </div>
      </div>
    </DialogContent>
  </Dialog>
</template>

/**
 * Multi-step wizard dialog for creating a new skill with optional references.
 * Step 1 collects skill details, step 2 allows attaching reference documents,
 * and step 3 shows a review with markdown-rendered prompts and references.
 * On submit, the skill is created first, then any references are added.
 */
<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { Pencil, Plus, Trash2 } from 'lucide-vue-next'
import { renderMarkdown, useRenderedMarkdown } from '@/composables/ui'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { WizardStepper } from '@/components/ui/wizard-stepper'
import { PreviewToggleButton } from '@/components/common'
import SkillDetailsForm from '../SkillDetailsForm.vue'
import type { CreateSkillRequest, SkillFormData } from '@/types/skill'
import type { CreateSkillReferenceRequest } from '@/types/reference'

interface Props {
  open: boolean
  error?: string
  submitting: boolean
}

interface WizardStepConfig {
  id: string
  title: string
  description: string
  optional?: boolean
}

const MAX_REFERENCES = 10

const props = defineProps<Props>()
const emit = defineEmits<{
  'update:open': [value: boolean]
  'create': [data: { skill: CreateSkillRequest; references: CreateSkillReferenceRequest[] }]
}>()

const steps: WizardStepConfig[] = [
  { id: 'details', title: 'Skill Details', description: 'Configure the skill name, description, and prompts.' },
  { id: 'references', title: 'References', description: 'Attach reference documents for RAG retrieval.', optional: true },
  { id: 'review', title: 'Review', description: 'Review skill details before creating.' },
]

const currentStepIndex = ref(0)
const highestStepReached = ref(0)

const currentStepTitle = computed(() => steps[currentStepIndex.value]?.title)
const currentStepDescription = computed(() => steps[currentStepIndex.value]?.description)

// Step 1: Skill form
const skillDetailsFormRef = ref<InstanceType<typeof SkillDetailsForm> | null>(null)
const skillForm = ref<SkillFormData>({
  name: '',
  description: '',
  systemPrompt: '',
  responseTemplate: '',
  toolNames: [],
})

const isStep1Valid = ref(false)
const validationError = ref('')

// Step 2: References
const pendingReferences = ref<CreateSkillReferenceRequest[]>([])
const showReferenceForm = ref(false)
const editingReferenceIndex = ref<number | null>(null)
const referenceForm = reactive({ name: '', content: '' })
const refContentPreview = ref(false)
const referenceFormError = ref('')

// Async markdown rendering
const renderedRefContent = useRenderedMarkdown(() => referenceForm.content)
const renderedReviewSystemPrompt = useRenderedMarkdown(() => skillForm.value.systemPrompt)
const renderedReviewResponseTemplate = useRenderedMarkdown(() => skillForm.value.responseTemplate)
const renderedReferenceContents = ref<string[]>([])

watch(pendingReferences, async (refs) => {
  renderedReferenceContents.value = await Promise.all(
    refs.map((r) => renderMarkdown(r.content))
  )
}, { deep: true })

// Step validity
const isCurrentStepValid = computed(() => {
  if (currentStepIndex.value === 0) return isStep1Valid.value
  return true // Steps 2 and 3 are always valid
})

// Navigation
function goToStep(index: number) {
  currentStepIndex.value = index
}

function goToNextStep() {
  if (currentStepIndex.value === 0 && !isStep1Valid.value) {
    validationError.value = 'Name, description, and system prompt are required.'
    return
  }
  validationError.value = ''

  if (currentStepIndex.value < steps.length - 1) {
    currentStepIndex.value++
    if (currentStepIndex.value > highestStepReached.value) {
      highestStepReached.value = currentStepIndex.value
    }
  }
}

function goToPreviousStep() {
  if (currentStepIndex.value > 0) {
    currentStepIndex.value--
  }
}

// Reference form management
function openReferenceForm() {
  referenceForm.name = ''
  referenceForm.content = ''
  refContentPreview.value = false
  referenceFormError.value = ''
  editingReferenceIndex.value = null
  showReferenceForm.value = true
}

function editReference(index: number) {
  const entry = pendingReferences.value[index]
  if (!entry) return
  referenceForm.name = entry.name
  referenceForm.content = entry.content
  refContentPreview.value = false
  referenceFormError.value = ''
  editingReferenceIndex.value = index
  showReferenceForm.value = true
}

function cancelReferenceForm() {
  showReferenceForm.value = false
  editingReferenceIndex.value = null
  referenceFormError.value = ''
}

function confirmReference() {
  if (!referenceForm.name.trim() || !referenceForm.content.trim()) {
    referenceFormError.value = 'Name and content are required.'
    return
  }

  const isDuplicate = pendingReferences.value.some(
    (r, i) => r.name === referenceForm.name.trim() && i !== editingReferenceIndex.value
  )
  if (isDuplicate) {
    referenceFormError.value = 'A reference with this name already exists.'
    return
  }

  const entry: CreateSkillReferenceRequest = {
    name: referenceForm.name.trim(),
    content: referenceForm.content,
  }

  if (editingReferenceIndex.value !== null) {
    pendingReferences.value[editingReferenceIndex.value] = entry
  } else {
    pendingReferences.value.push(entry)
  }

  showReferenceForm.value = false
  editingReferenceIndex.value = null
  referenceFormError.value = ''
}

function removeReference(index: number) {
  if (editingReferenceIndex.value === index) {
    cancelReferenceForm()
  } else if (editingReferenceIndex.value !== null && editingReferenceIndex.value > index) {
    editingReferenceIndex.value--
  }
  pendingReferences.value.splice(index, 1)
}

// Dialog management
function handleOpenChange(value: boolean) {
  if (!value) {
    resetWizard()
  }
  emit('update:open', value)
}

function handleCancel() {
  emit('update:open', false)
}

function resetWizard() {
  currentStepIndex.value = 0
  highestStepReached.value = 0
  skillForm.value = { name: '', description: '', systemPrompt: '', responseTemplate: '', toolNames: [] }
  skillDetailsFormRef.value?.resetPreviews()
  isStep1Valid.value = false
  validationError.value = ''
  pendingReferences.value = []
  showReferenceForm.value = false
  editingReferenceIndex.value = null
  referenceFormError.value = ''
}

watch(() => props.open, (value) => {
  if (value) {
    resetWizard()
  }
})

function handleSubmit() {
  if (props.submitting) return

  if (!isStep1Valid.value) {
    validationError.value = 'Name, description, and system prompt are required.'
    currentStepIndex.value = 0
    return
  }
  validationError.value = ''

  const skill: CreateSkillRequest = {
    name: skillForm.value.name,
    description: skillForm.value.description,
    systemPrompt: skillForm.value.systemPrompt,
    responseTemplate: skillForm.value.responseTemplate.trim() || undefined,
    toolNames: [...skillForm.value.toolNames],
  }

  emit('create', { skill, references: [...pendingReferences.value] })
}
</script>
