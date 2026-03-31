/** Composable for http tool create, update, delete, and test operations with error and loading state. */
import type { Ref } from 'vue'
import { httpToolService } from '@/services/httpTool'
import type {
  HttpTool,
  CreateHttpToolRequest,
  UpdateHttpToolRequest,
  TestHttpToolRequest,
  HttpToolTestResult,
} from '@/types/http-tool'

export interface UseHttpToolCrudOptions {
  editingToolId: Ref<string | undefined>
  deletingToolId: Ref<string | undefined>
  testingToolId: Ref<string | undefined>
  createDialogOpen: Ref<boolean>
  createError: Ref<string | undefined>
  createSubmitting: Ref<boolean>
  editDialogOpen: Ref<boolean>
  editError: Ref<string | undefined>
  editSubmitting: Ref<boolean>
  deleteDialogOpen: Ref<boolean>
  deleteError: Ref<string | undefined>
  deleteSubmitting: Ref<boolean>
  testError: Ref<string | undefined>
  testSubmitting: Ref<boolean>
  testResult: Ref<HttpToolTestResult | undefined>
  expandedRows: Ref<Map<string, HttpTool | undefined>>
  onDataChanged: () => void
}

export function useHttpToolCrud(options: UseHttpToolCrudOptions) {
  async function handleCreate(data: CreateHttpToolRequest) {
    options.createSubmitting.value = true
    options.createError.value = undefined

    try {
      await httpToolService.createHttpTool(data)
      options.createDialogOpen.value = false
      options.onDataChanged()
    } catch (err) {
      options.createError.value = err instanceof Error ? err.message : 'Failed to create tool'
    } finally {
      options.createSubmitting.value = false
    }
  }

  async function handleEdit(data: UpdateHttpToolRequest) {
    const toolId = options.editingToolId.value
    if (!toolId) {
      options.editError.value = 'No tool selected for editing'
      return
    }

    options.editSubmitting.value = true
    options.editError.value = undefined

    try {
      await httpToolService.updateHttpTool(toolId, data)
      options.editDialogOpen.value = false

      const newMap = new Map(options.expandedRows.value)
      newMap.delete(toolId)
      options.expandedRows.value = newMap

      options.onDataChanged()
    } catch (err) {
      options.editError.value = err instanceof Error ? err.message : 'Failed to update tool'
    } finally {
      options.editSubmitting.value = false
    }
  }

  async function handleDelete() {
    const toolId = options.deletingToolId.value
    if (!toolId) {
      options.deleteError.value = 'No tool selected for deletion'
      return
    }

    options.deleteSubmitting.value = true
    options.deleteError.value = undefined

    try {
      await httpToolService.deleteHttpTool(toolId)
      options.deleteDialogOpen.value = false

      const newMap = new Map(options.expandedRows.value)
      newMap.delete(toolId)
      options.expandedRows.value = newMap

      options.onDataChanged()
    } catch (err) {
      options.deleteError.value = err instanceof Error ? err.message : 'Failed to delete tool'
    } finally {
      options.deleteSubmitting.value = false
    }
  }

  async function handleTest(data: TestHttpToolRequest) {
    const toolId = options.testingToolId.value
    if (!toolId) {
      options.testError.value = 'No tool selected for testing'
      return
    }

    options.testSubmitting.value = true
    options.testError.value = undefined
    options.testResult.value = undefined

    try {
      options.testResult.value = await httpToolService.testHttpTool(toolId, data)
    } catch (err) {
      options.testError.value = err instanceof Error ? err.message : 'Failed to test tool'
    } finally {
      options.testSubmitting.value = false
    }
  }

  return { handleCreate, handleEdit, handleDelete, handleTest }
}
