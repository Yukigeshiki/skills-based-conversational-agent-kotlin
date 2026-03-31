<template>
  <BaseExpandedContent
    :is-loading="isLoading"
    :has-data="!!tool"
  >
    <div v-if="tool" class="space-y-4">
      <div class="max-w-4xl space-y-4">
        <!-- Details section -->
        <CollapsibleSection title="Details" :default-expanded="true" :show-edit="true" @edit="$emit('edit', tool.id)">
          <template #edit-button="{ isHovered, forceShowEdit }">
            <button
              @click.stop="$emit('edit', tool.id)"
              class="p-1 rounded hover:bg-accent transition-all cursor-pointer"
              :class="isHovered || forceShowEdit ? 'opacity-100' : 'opacity-30'"
              aria-label="Edit details"
            >
              <Pencil class="h-3 w-3" />
            </button>
            <button
              @click.stop="$emit('test', tool.id)"
              class="p-1 rounded hover:bg-accent transition-all cursor-pointer"
              :class="isHovered || forceShowEdit ? 'opacity-100' : 'opacity-30'"
              aria-label="Test tool"
            >
              <Play class="h-3 w-3" />
            </button>
          </template>
          <dl class="space-y-1 text-sm">
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Name:</dt>
              <dd>{{ tool.name }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Description:</dt>
              <dd>{{ tool.description }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Endpoint URL:</dt>
              <dd class="font-mono text-xs break-all">{{ tool.endpointUrl }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">HTTP Method:</dt>
              <dd>{{ tool.httpMethod }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Timeout:</dt>
              <dd>{{ tool.timeoutSeconds }}s</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Max Response:</dt>
              <dd>{{ tool.maxResponseLength }} chars</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Created:</dt>
              <dd>{{ formatDate(tool.createdAt) }}</dd>
            </div>
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">Last Updated:</dt>
              <dd>{{ formatDate(tool.updatedAt) }}</dd>
            </div>
          </dl>
        </CollapsibleSection>

        <!-- Headers section -->
        <CollapsibleSection title="Headers" :default-expanded="false">
          <div v-if="headerEntries.length > 0" class="space-y-1 text-sm">
            <div v-for="[key, value] in headerEntries" :key="key" class="flex gap-1">
              <dt class="text-muted-foreground shrink-0 font-mono text-xs">{{ key }}:</dt>
              <dd class="font-mono text-xs break-all">{{ value }}</dd>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground italic">No headers configured</div>
        </CollapsibleSection>

        <!-- Parameters section -->
        <CollapsibleSection title="Parameters" :default-expanded="false">
          <div v-if="tool.parameters.length > 0" class="space-y-2">
            <div
              v-for="param in tool.parameters"
              :key="param.name"
              class="flex items-center gap-2 text-sm"
            >
              <span class="font-medium">{{ param.name }}</span>
              <Badge variant="outline" class="text-xs">{{ param.type }}</Badge>
              <span class="text-muted-foreground">{{ param.description }}</span>
              <Badge v-if="param.required" variant="default" class="text-xs">required</Badge>
            </div>
          </div>
          <div v-else class="text-sm text-muted-foreground italic">No parameters configured</div>
        </CollapsibleSection>

        <!-- Metadata section -->
        <CollapsibleSection title="Metadata" :default-expanded="false">
          <dl class="space-y-1 text-sm">
            <div class="flex gap-1">
              <dt class="text-muted-foreground w-32 shrink-0">ID:</dt>
              <dd class="font-mono text-xs">{{ tool.id }}</dd>
            </div>
          </dl>
        </CollapsibleSection>
      </div>

      <!-- Danger Zone -->
      <div class="relative pt-4 mt-4 border-t border-destructive/20">
        <div class="flex items-center justify-between">
          <div>
            <h3 class="font-semibold text-destructive">Danger Zone</h3>
            <p class="text-sm text-muted-foreground mt-1">Permanently remove this tool from the system.</p>
          </div>
          <DestructiveButton
            size="sm"
            @click="$emit('delete', tool.id)"
          >
            Delete Tool
          </DestructiveButton>
        </div>
      </div>
    </div>
  </BaseExpandedContent>
</template>

/**
 * Expanded content panel for an HTTP tool, showing details, headers,
 * parameters, metadata, and a danger zone with delete and test buttons.
 */
<script setup lang="ts">
import { computed } from 'vue'
import { Pencil, Play } from 'lucide-vue-next'
import { Badge } from '@/components/ui/badge'
import { BaseExpandedContent, CollapsibleSection, DestructiveButton } from '@/components/common'
import { useHttpToolFormatters } from '@/composables/http-tools'
import type { HttpTool } from '@/types/http-tool'

interface Props {
  /** The full http tool data to display, or undefined while loading. */
  tool: HttpTool | undefined
  /** Whether the tool data is currently loading. */
  isLoading: boolean
}

const props = defineProps<Props>()
defineEmits<{
  /**
   * Emitted when the edit button is clicked.
   *
   * @param toolId - The ID of the tool to edit.
   */
  edit: [toolId: string]
  /**
   * Emitted when the delete button is clicked.
   *
   * @param toolId - The ID of the tool to delete.
   */
  delete: [toolId: string]
  /**
   * Emitted when the test button is clicked.
   *
   * @param toolId - The ID of the tool to test.
   */
  test: [toolId: string]
}>()

const { formatDate } = useHttpToolFormatters()

const headerEntries = computed(() =>
  props.tool ? Object.entries(props.tool.headers) : []
)
</script>
