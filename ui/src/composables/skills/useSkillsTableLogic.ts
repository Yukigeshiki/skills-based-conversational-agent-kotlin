/**
 * Facade composable that wires together dialog state, CRUD handlers, and formatters
 * for the skills table. Composes {@link useSkillDialogState}, {@link useSkillCrud},
 * and {@link useSkillFormatters} into a single return object.
 */
import type { Ref } from 'vue'
import type { Skill } from '@/types/skill'
import { useSkillDialogState } from './useSkillDialogState'
import { useSkillCrud } from './useSkillCrud'
import { useSkillFormatters } from './useSkillFormatters'

/** Configuration for {@link useSkillsTableLogic}. */
export interface UseSkillsTableLogicOptions {
  /** Reactive map of expanded row IDs to their loaded skill data. */
  expandedRows: Ref<Map<string, Skill | undefined>>
  /** Callback invoked after any create, update, or delete to refresh the table data. */
  onDataChanged: () => void
}

/**
 * Composes all skills table logic into a single return object.
 *
 * @param options - Expanded rows ref and data-changed callback.
 * @returns Combined dialog state, CRUD handlers, and formatters.
 */
export function useSkillsTableLogic(options: UseSkillsTableLogicOptions) {
  const dialogState = useSkillDialogState()
  const formatters = useSkillFormatters()

  const crud = useSkillCrud({
    ...dialogState,
    expandedRows: options.expandedRows,
    onDataChanged: options.onDataChanged,
  })

  return {
    ...dialogState,
    ...formatters,
    ...crud,
  }
}
