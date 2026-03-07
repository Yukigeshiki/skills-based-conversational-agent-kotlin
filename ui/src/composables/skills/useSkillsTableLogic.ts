import type { Ref } from 'vue'
import type { Skill } from '@/types/skill'
import { useSkillDialogState } from './useSkillDialogState'
import { useSkillCrud } from './useSkillCrud'
import { useSkillFormatters } from './useSkillFormatters'

export interface UseSkillsTableLogicOptions {
  expandedRows: Ref<Map<string, Skill | undefined>>
  onDataChanged: () => void
}

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
