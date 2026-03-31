/** Composable for managing open/close state, errors, and submission flags for HTTP tool CRUD and test dialogs. */
import { ref } from 'vue'
import type { HttpToolTestResult } from '@/types/http-tool'

export function useHttpToolDialogState() {
  const editingToolId = ref<string | undefined>(undefined)
  const deletingToolId = ref<string | undefined>(undefined)
  const testingToolId = ref<string | undefined>(undefined)

  // Create dialog
  const createDialogOpen = ref(false)
  const createError = ref<string | undefined>(undefined)
  const createSubmitting = ref(false)

  // Edit dialog
  const editDialogOpen = ref(false)
  const editError = ref<string | undefined>(undefined)
  const editSubmitting = ref(false)

  // Delete dialog
  const deleteDialogOpen = ref(false)
  const deleteError = ref<string | undefined>(undefined)
  const deleteSubmitting = ref(false)

  // Test dialog
  const testDialogOpen = ref(false)
  const testError = ref<string | undefined>(undefined)
  const testSubmitting = ref(false)
  const testResult = ref<HttpToolTestResult | undefined>(undefined)

  function openCreate() {
    createDialogOpen.value = true
    createError.value = undefined
  }

  function openEdit(toolId: string) {
    editingToolId.value = toolId
    editDialogOpen.value = true
    editError.value = undefined
  }

  function openDelete(toolId: string) {
    deletingToolId.value = toolId
    deleteDialogOpen.value = true
    deleteError.value = undefined
  }

  /** Opens the test dialog for the given tool ID and resets its state. */
  function openTest(toolId: string) {
    testingToolId.value = toolId
    testDialogOpen.value = true
    testError.value = undefined
    testResult.value = undefined
  }

  return {
    editingToolId,
    deletingToolId,
    testingToolId,
    createDialogOpen,
    createError,
    createSubmitting,
    editDialogOpen,
    editError,
    editSubmitting,
    deleteDialogOpen,
    deleteError,
    deleteSubmitting,
    testDialogOpen,
    testError,
    testSubmitting,
    testResult,
    openCreate,
    openEdit,
    openDelete,
    openTest,
  }
}
