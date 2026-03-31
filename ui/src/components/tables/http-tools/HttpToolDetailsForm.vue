<!--
/**
 * Shared form fields for http tool creation and editing.
 *
 * Renders name, description, endpoint URL, HTTP method, headers, parameters,
 * timeout, and max response length. Emits validity status on change.
 */
-->
<template>
  <div class="space-y-4">
    <div class="space-y-2">
      <Label for="tool-name">Name <span class="text-destructive">*</span></Label>
      <Input
        id="tool-name"
        v-model="model.name"
        placeholder="e.g. weatherLookup (camelCase, no hyphens or underscores)"
        required
      />
    </div>
    <div class="space-y-2">
      <Label for="tool-description">Description <span class="text-destructive">*</span></Label>
      <Textarea
        id="tool-description"
        v-model="model.description"
        placeholder="Describe what this tool does"
        required
        rows="3"
      />
    </div>
    <div class="space-y-2">
      <Label for="tool-endpoint-url">Endpoint URL <span class="text-destructive">*</span></Label>
      <Input
        id="tool-endpoint-url"
        v-model="model.endpointUrl"
        placeholder="https://api.example.com/endpoint"
        required
      />
    </div>
    <div class="space-y-2">
      <Label for="tool-http-method">HTTP Method</Label>
      <Select v-model="model.httpMethod">
        <SelectTrigger class="w-full"><SelectValue /></SelectTrigger>
        <SelectContent>
          <SelectItem value="GET">GET</SelectItem>
          <SelectItem value="POST">POST</SelectItem>
          <SelectItem value="PUT">PUT</SelectItem>
          <SelectItem value="PATCH">PATCH</SelectItem>
          <SelectItem value="DELETE">DELETE</SelectItem>
        </SelectContent>
      </Select>
    </div>

    <!-- Headers -->
    <div class="space-y-2">
      <Label>Headers</Label>
      <p class="text-xs text-muted-foreground">Use <code v-pre>{{ENV_VAR}}</code> placeholders for sensitive values.</p>
      <div v-for="(entry, index) in headerEntries" :key="entry.id" class="flex items-center gap-2">
        <Input
          v-model="entry.key"
          placeholder="Header name"
          class="flex-1"
        />
        <Input
          v-model="entry.value"
          placeholder="Header value"
          class="flex-1"
        />
        <Button
          type="button"
          variant="ghost"
          size="icon"
          class="shrink-0 cursor-pointer"
          @click="removeHeader(index)"
        >
          <X class="h-4 w-4" />
        </Button>
      </div>
      <Button
        v-if="headerEntries.length < 20"
        type="button"
        variant="outline"
        size="sm"
        class="cursor-pointer"
        @click="addHeader"
      >
        <Plus class="h-4 w-4 mr-1" />
        Add Header
      </Button>
    </div>

    <!-- Parameters -->
    <div class="space-y-2">
      <Label>Parameters</Label>
      <div v-for="(param, index) in model.parameters" :key="`${param.name}-${index}`" class="flex items-center gap-2 border rounded-md p-2">
        <Input
          v-model="param.name"
          placeholder="Name"
          class="flex-1"
        />
        <Select v-model="param.type" class="w-32">
          <SelectTrigger class="w-32"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="string">string</SelectItem>
            <SelectItem value="integer">integer</SelectItem>
            <SelectItem value="number">number</SelectItem>
            <SelectItem value="boolean">boolean</SelectItem>
          </SelectContent>
        </Select>
        <Input
          v-model="param.description"
          placeholder="Description"
          class="flex-1"
        />
        <div class="flex items-center gap-1 shrink-0">
          <Switch
            :checked="param.required"
            @update:checked="param.required = $event"
          />
          <span class="text-xs text-muted-foreground">Req</span>
        </div>
        <Button
          type="button"
          variant="ghost"
          size="icon"
          class="shrink-0 cursor-pointer"
          @click="removeParameter(index)"
        >
          <X class="h-4 w-4" />
        </Button>
      </div>
      <div>
        <Button
          v-if="model.parameters.length < 20"
          type="button"
          variant="outline"
          size="sm"
          class="cursor-pointer"
          @click="addParameter"
        >
          <Plus class="h-4 w-4 mr-1" />
          Add Parameter
        </Button>
      </div>
    </div>

    <div class="space-y-2">
      <Label for="tool-timeout">Timeout (seconds)</Label>
      <Input
        id="tool-timeout"
        v-model.number="model.timeoutSeconds"
        type="number"
        :min="1"
        :max="120"
      />
    </div>
    <div class="space-y-2">
      <Label for="tool-max-response">Max Response Length</Label>
      <Input
        id="tool-max-response"
        v-model.number="model.maxResponseLength"
        type="number"
        :min="100"
        :max="16000"
      />
    </div>
  </div>
</template>

/**
 * Reusable http tool details form used by both AddHttpToolDialog and SaveHttpToolDialog.
 * Accepts a v-model for the form data and emits validity status.
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { Plus, X } from 'lucide-vue-next'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import { Button } from '@/components/ui/button'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import type { HttpToolFormData } from '@/types/http-tool'

const model = defineModel<HttpToolFormData>({ required: true })

const emit = defineEmits<{
  valid: [value: boolean]
}>()

interface HeaderEntry {
  id: string
  key: string
  value: string
}

function toHeaderEntries(headers: Record<string, string>): HeaderEntry[] {
  return Object.entries(headers).map(([key, value]) => ({ id: crypto.randomUUID(), key, value }))
}

const headerEntries = ref<HeaderEntry[]>(toHeaderEntries(model.value.headers))

/** Re-populates headerEntries when the model is replaced externally (e.g. edit dialog load). */
watch(
  () => model.value.headers,
  (headers) => {
    headerEntries.value = toHeaderEntries(headers)
  },
)

/** Builds a clean headers Record from the editing entries, filtering out empty keys. */
function getHeaders(): Record<string, string> {
  const headers: Record<string, string> = {}
  for (const entry of headerEntries.value) {
    if (entry.key.trim()) {
      headers[entry.key.trim()] = entry.value
    }
  }
  return headers
}

defineExpose({ getHeaders })

function addHeader() {
  if (headerEntries.value.length < 20) {
    headerEntries.value.push({ id: crypto.randomUUID(), key: '', value: '' })
  }
}

function removeHeader(index: number) {
  headerEntries.value.splice(index, 1)
}

function addParameter() {
  if (model.value.parameters.length < 20) {
    model.value.parameters.push({
      name: '',
      type: 'string',
      description: '',
      required: false,
    })
  }
}

function removeParameter(index: number) {
  model.value.parameters.splice(index, 1)
}

const isValid = computed(() =>
  !!model.value.name.trim() && !!model.value.description.trim() && !!model.value.endpointUrl.trim()
)

watch(isValid, (value) => emit('valid', value), { immediate: true })
</script>
