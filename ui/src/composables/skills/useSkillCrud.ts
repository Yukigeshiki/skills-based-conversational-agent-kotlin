/** Composable for skill create, update, and delete operations with error and loading state. */
import type { Ref } from 'vue'
import { skillService, referenceService } from '@/services'
import type { Skill, CreateSkillRequest, UpdateSkillRequest } from '@/types/skill'
import type { CreateSkillReferenceRequest } from '@/types/reference'

/** Reactive refs for dialog state, error messages, and submission flags needed by CRUD handlers. */
export interface UseSkillCrudOptions {
  editingSkillId: Ref<string | undefined>
  deletingSkillId: Ref<string | undefined>
  createDialogOpen: Ref<boolean>
  createError: Ref<string | undefined>
  createSubmitting: Ref<boolean>
  editDialogOpen: Ref<boolean>
  editError: Ref<string | undefined>
  editSubmitting: Ref<boolean>
  deleteDialogOpen: Ref<boolean>
  deleteError: Ref<string | undefined>
  deleteSubmitting: Ref<boolean>
  expandedRows: Ref<Map<string, Skill | undefined>>
  onDataChanged: () => void
}

/** Payload emitted by AddSkillDialog containing skill data and optional references. */
export interface CreateSkillWithReferences {
  skill: CreateSkillRequest
  references: CreateSkillReferenceRequest[]
}

/**
 * Provides async handlers for skill CRUD operations.
 *
 * @param options - Reactive refs for dialog state and a callback to refresh data after mutations.
 * @returns An object containing {@link handleCreate}, {@link handleEdit}, and {@link handleDelete}.
 */
export function useSkillCrud(options: UseSkillCrudOptions) {
  /**
   * Creates a new skill and any attached references, closes the dialog on success,
   * and triggers a data refresh.
   *
   * @param data - The skill creation payload with optional references.
   */
  async function handleCreate(data: CreateSkillWithReferences) {
    options.createSubmitting.value = true
    options.createError.value = undefined

    try {
      const createdSkill = await skillService.createSkill(data.skill)

      // Skill created — attempt to add references. If some fail, still close the dialog
      // since the skill exists, and inform the user which references need to be re-added.
      const failedReferences: string[] = []
      for (const ref of data.references) {
        try {
          await referenceService.createReference(createdSkill.id, ref)
        } catch {
          failedReferences.push(ref.name)
        }
      }

      options.createDialogOpen.value = false
      options.onDataChanged()

      if (failedReferences.length > 0) {
        options.createError.value =
          `Skill created, but ${failedReferences.length} of ${data.references.length} reference(s) failed to save: ${failedReferences.join(', ')}. Please re-add them via the edit flow.`
      }
    } catch (err) {
      options.createError.value = err instanceof Error ? err.message : 'Failed to create skill'
    } finally {
      options.createSubmitting.value = false
    }
  }

  /**
   * Updates the currently selected skill, invalidates its expanded row cache, and refreshes data.
   *
   * @param data - The fields to update on the skill.
   */
  async function handleEdit(data: UpdateSkillRequest) {
    const skillId = options.editingSkillId.value
    if (!skillId) {
      options.editError.value = 'No skill selected for editing'
      return
    }

    options.editSubmitting.value = true
    options.editError.value = undefined

    try {
      await skillService.updateSkill(skillId, data)
      options.editDialogOpen.value = false

      // Clear expanded row cache so it reloads
      const newMap = new Map(options.expandedRows.value)
      newMap.delete(skillId)
      options.expandedRows.value = newMap

      options.onDataChanged()
    } catch (err) {
      options.editError.value = err instanceof Error ? err.message : 'Failed to update skill'
    } finally {
      options.editSubmitting.value = false
    }
  }

  /** Deletes the currently selected skill, removes it from expanded rows, and refreshes data. */
  async function handleDelete() {
    const skillId = options.deletingSkillId.value
    if (!skillId) {
      options.deleteError.value = 'No skill selected for deletion'
      return
    }

    options.deleteSubmitting.value = true
    options.deleteError.value = undefined

    try {
      await skillService.deleteSkill(skillId)
      options.deleteDialogOpen.value = false

      // Remove from expanded rows
      const newMap = new Map(options.expandedRows.value)
      newMap.delete(skillId)
      options.expandedRows.value = newMap

      options.onDataChanged()
    } catch (err) {
      options.deleteError.value = err instanceof Error ? err.message : 'Failed to delete skill'
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
