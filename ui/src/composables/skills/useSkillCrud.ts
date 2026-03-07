import type { Ref } from 'vue'
import { skillService } from '@/services'
import type { Skill, CreateSkillRequest, UpdateSkillRequest } from '@/types/skill'

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

export function useSkillCrud(options: UseSkillCrudOptions) {
  async function handleCreate(data: CreateSkillRequest) {
    options.createSubmitting.value = true
    options.createError.value = undefined

    try {
      await skillService.createSkill(data)
      options.createDialogOpen.value = false
      options.onDataChanged()
    } catch (err) {
      options.createError.value = err instanceof Error ? err.message : 'Failed to create skill'
    } finally {
      options.createSubmitting.value = false
    }
  }

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
