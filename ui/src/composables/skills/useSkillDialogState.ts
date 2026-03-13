/** Composable for managing open/close state, errors, and submission flags for skill CRUD dialogs. */
import { ref } from 'vue'

export function useSkillDialogState() {
  const editingSkillId = ref<string | undefined>(undefined)
  const deletingSkillId = ref<string | undefined>(undefined)

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

  /** Opens the create dialog and resets its error state. */
  function openCreate() {
    createDialogOpen.value = true
    createError.value = undefined
  }

  /**
   * Opens the edit dialog for the given skill ID and resets its error state.
   *
   * @param skillId - The ID of the skill to edit.
   */
  function openEdit(skillId: string) {
    editingSkillId.value = skillId
    editDialogOpen.value = true
    editError.value = undefined
  }

  /**
   * Opens the delete confirmation dialog for the given skill ID.
   *
   * @param skillId - The ID of the skill to delete.
   */
  function openDelete(skillId: string) {
    deletingSkillId.value = skillId
    deleteDialogOpen.value = true
    deleteError.value = undefined
  }

  return {
    editingSkillId,
    deletingSkillId,
    createDialogOpen,
    createError,
    createSubmitting,
    editDialogOpen,
    editError,
    editSubmitting,
    deleteDialogOpen,
    deleteError,
    deleteSubmitting,
    openCreate,
    openEdit,
    openDelete,
  }
}
