<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-2xl max-h-[85vh] overflow-hidden flex flex-col">
      <DialogHeader>
        <DialogTitle>Test HTTP Tool</DialogTitle>
        <DialogDescription>Provide argument values and run the tool to see the response.</DialogDescription>
      </DialogHeader>

      <div v-if="loading" class="flex items-center justify-center py-8">
        <span class="text-sm text-muted-foreground">Loading tool parameters...</span>
      </div>

      <div v-else class="flex flex-col min-h-0 flex-1 space-y-4 overflow-y-auto">
        <!-- Parameter inputs -->
        <div v-if="parameterDefs.length > 0" class="space-y-3">
          <div v-for="param in parameterDefs" :key="param.name" class="space-y-1">
            <Label :for="`test-${param.name}`">
              {{ param.name }}
              <span v-if="param.required" class="text-destructive">*</span>
              <span class="text-xs text-muted-foreground ml-1">({{ param.type }})</span>
            </Label>
            <p v-if="param.description" class="text-xs text-muted-foreground">{{ param.description }}</p>
            <Switch
              v-if="param.type === 'boolean'"
              :id="`test-${param.name}`"
              :checked="!!testArgs[param.name]"
              @update:checked="testArgs[param.name] = $event"
            />
            <Input
              v-else-if="param.type === 'integer' || param.type === 'number'"
              :id="`test-${param.name}`"
              :model-value="String(testArgs[param.name] ?? '')"
              type="number"
              @update:model-value="testArgs[param.name] = Number($event)"
            />
            <Input
              v-else
              :id="`test-${param.name}`"
              :model-value="String(testArgs[param.name] ?? '')"
              @update:model-value="testArgs[param.name] = $event"
              type="text"
            />
          </div>
        </div>
        <div v-else class="text-sm text-muted-foreground italic">
          This tool has no parameters.
        </div>

        <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

        <Button
          type="button"
          :disabled="submitting"
          class="cursor-pointer w-fit"
          @click="handleTest"
        >
          {{ submitting ? 'Running...' : 'Run Test' }}
        </Button>

        <!-- Result panel -->
        <div v-if="testResult" class="space-y-2 border rounded-md p-4">
          <div class="flex items-center gap-2">
            <span class="text-sm font-medium">Status:</span>
            <Badge :variant="testResult.statusCode >= 400 ? 'destructive' : 'default'">
              {{ testResult.statusCode }}
            </Badge>
          </div>
          <div class="text-sm">
            <span class="font-medium">Duration:</span> {{ testResult.durationMs }}ms
          </div>
          <div v-if="testResult.truncated" class="text-sm text-yellow-600 dark:text-yellow-400">
            Response truncated
          </div>
          <pre class="text-xs bg-muted rounded-md p-3 max-h-64 overflow-auto whitespace-pre-wrap wrap-break-word">{{ testResult.body }}</pre>
        </div>
      </div>

      <DialogFooter class="pt-4">
        <Button type="button" variant="outline" @click="open = false" class="cursor-pointer">
          Close
        </Button>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

/** Dialog for testing an HTTP tool with sample arguments and viewing the response. */
<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { httpToolService } from '@/services/httpTool'
import type { HttpToolParameter, HttpToolTestResult, TestHttpToolRequest } from '@/types/http-tool'

interface Props {
  /** The ID of the tool to test, or undefined if no tool is selected. */
  toolId: string | undefined
  /** Error message from the test operation, if any. */
  error?: string
  /** Whether a test request is in flight. */
  submitting: boolean
  /** The result of the last test execution. */
  testResult?: HttpToolTestResult
}

const props = defineProps<Props>()
const emit = defineEmits<{
  'test': [data: TestHttpToolRequest]
}>()

const open = defineModel<boolean>('open', { required: true })

const loading = ref(false)
const parameterDefs = ref<HttpToolParameter[]>([])
const testArgs = reactive<Record<string, string | number | boolean>>({})

watch(open, async (isOpen) => {
  if (!isOpen || !props.toolId) return

  loading.value = true
  try {
    const tool = await httpToolService.getHttpToolById(props.toolId)
    parameterDefs.value = tool.parameters

    // Reset test args
    Object.keys(testArgs).forEach((key) => delete testArgs[key])
    for (const param of tool.parameters) {
      if (param.type === 'boolean') {
        testArgs[param.name] = false
      } else if (param.type === 'integer' || param.type === 'number') {
        testArgs[param.name] = 0
      } else {
        testArgs[param.name] = ''
      }
    }
  } catch {
    open.value = false
  } finally {
    loading.value = false
  }
})

function handleTest() {
  emit('test', { arguments: { ...testArgs } })
}
</script>
