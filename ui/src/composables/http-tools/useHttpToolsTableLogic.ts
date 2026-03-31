/**
 * Facade composable that wires together dialog state, CRUD handlers, and formatters
 * for the HTTP tools table. Composes {@link useHttpToolDialogState},
 * {@link useHttpToolCrud}, and {@link useHttpToolFormatters} into a single
 * return object.
 */
import type { Ref } from 'vue'
import type { HttpTool } from '@/types/http-tool'
import { useHttpToolDialogState } from './useHttpToolDialogState'
import { useHttpToolCrud } from './useHttpToolCrud'
import { useHttpToolFormatters } from './useHttpToolFormatters'

/** Configuration for {@link useHttpToolsTableLogic}. */
export interface UseHttpToolsTableLogicOptions {
  /** Reactive map of expanded row IDs to their loaded HTTP tool data. */
  expandedRows: Ref<Map<string, HttpTool | undefined>>
  /** Callback invoked after any create, update, or delete to refresh the table data. */
  onDataChanged: () => void
}

/** Composes all HTTP tools table logic into a single return object. */
export function useHttpToolsTableLogic(options: UseHttpToolsTableLogicOptions) {
  const dialogState = useHttpToolDialogState()
  const formatters = useHttpToolFormatters()

  const crud = useHttpToolCrud({
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
