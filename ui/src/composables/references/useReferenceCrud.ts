/** Composable for skill reference create, update, and delete operations with error and loading state. */
import type { Ref } from 'vue'
import { referenceService } from '@/services'
import type { CreateSkillReferenceRequest, UpdateSkillReferenceRequest } from '@/types/reference'

/** Reactive refs for dialog state, error messages, and submission flags needed by reference CRUD handlers. */
export interface UseReferenceCrudOptions {
  skillId: Ref<string>
  editingReferenceId: Ref<string | undefined>
  createDialogOpen: Ref<boolean>
  createError: Ref<string | undefined>
  createSubmitting: Ref<boolean>
  editDialogOpen: Ref<boolean>
  editError: Ref<string | undefined>
  editSubmitting: Ref<boolean>
  deleteDialogOpen: Ref<boolean>
  deletingReferenceId: Ref<string | undefined>
  deleteError: Ref<string | undefined>
  deleteSubmitting: Ref<boolean>
  onDataChanged: () => void
}

/**
 * Provides async handlers for skill reference CRUD operations.
 *
 * @param options - Reactive refs for dialog state and a callback to refresh data after mutations.
 * @returns An object containing {@link handleCreate}, {@link handleEdit}, and {@link handleDelete}.
 */
export function useReferenceCrud(options: UseReferenceCrudOptions) {
  async function handleCreate(data: CreateSkillReferenceRequest) {
    options.createSubmitting.value = true
    options.createError.value = undefined

    try {
      await referenceService.createReference(options.skillId.value, data)
      options.createDialogOpen.value = false
      options.onDataChanged()
    } catch (err) {
      options.createError.value = err instanceof Error ? err.message : 'Failed to create reference'
    } finally {
      options.createSubmitting.value = false
    }
  }

  async function handleEdit(data: UpdateSkillReferenceRequest) {
    const referenceId = options.editingReferenceId.value
    if (!referenceId) {
      options.editError.value = 'No reference selected for editing'
      return
    }

    options.editSubmitting.value = true
    options.editError.value = undefined

    try {
      await referenceService.updateReference(options.skillId.value, referenceId, data)
      options.editDialogOpen.value = false
      options.onDataChanged()
    } catch (err) {
      options.editError.value = err instanceof Error ? err.message : 'Failed to update reference'
    } finally {
      options.editSubmitting.value = false
    }
  }

  async function handleDelete() {
    const referenceId = options.deletingReferenceId.value
    if (!referenceId) {
      options.deleteError.value = 'No reference selected for deletion'
      return
    }

    options.deleteSubmitting.value = true
    options.deleteError.value = undefined

    try {
      await referenceService.deleteReference(options.skillId.value, referenceId)
      options.deleteDialogOpen.value = false
      options.onDataChanged()
    } catch (err) {
      options.deleteError.value = err instanceof Error ? err.message : 'Failed to delete reference'
    } finally {
      options.deleteSubmitting.value = false
    }
  }

  return {
    handleCreate,
    handleEdit,
    handleDelete,
  }
}
