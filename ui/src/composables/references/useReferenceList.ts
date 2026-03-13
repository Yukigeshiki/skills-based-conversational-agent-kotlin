/** Composable for loading and managing a list of skill references. */
import { ref, type Ref } from 'vue'
import { referenceService } from '@/services'
import type { SkillReference } from '@/types/reference'

/**
 * Manages loading references for a given skill.
 *
 * @param skillId - Reactive ref to the skill's unique identifier.
 * @returns Reactive state for the reference list, loading flag, and a load function.
 */
export function useReferenceList(skillId: Ref<string>) {
  const references = ref<SkillReference[]>([])
  const loading = ref(false)

  async function loadReferences() {
    loading.value = true
    try {
      references.value = await referenceService.getReferences(skillId.value)
    } catch {
      references.value = []
    } finally {
      loading.value = false
    }
  }

  return {
    references,
    loading,
    loadReferences,
  }
}
