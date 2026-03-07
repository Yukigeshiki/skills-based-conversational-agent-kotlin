<template>
  <div v-bind="$attrs">
    <!-- Loading state -->
    <div v-if="loading" class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
      <Skeleton
        v-for="n in skeletonCount"
        :key="n"
        class="h-40 rounded-xl"
      />
    </div>

    <!-- Error state -->
    <div v-else-if="error" class="rounded-md border p-8 text-center text-destructive">
      {{ error }}
    </div>

    <!-- Empty state -->
    <div v-else-if="isEmpty" class="rounded-md border p-8 text-center text-muted-foreground">
      {{ emptyMessage }}
    </div>

    <!-- Card grid with expansion support -->
    <div v-else class="space-y-4">
      <template v-for="(row, rowIndex) in cardRows" :key="rowIndex">
        <!-- Card row -->
        <div class="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4">
          <template v-for="item in row" :key="getItemId(item)">
            <div
              class="card-wrapper"
              :class="{ 'card-dimmed': expandedItemInRow(row) && !isExpanded(getItemId(item)) }"
            >
              <slot name="card" :item="item" />
            </div>
          </template>
        </div>

        <!-- Expansion panel -->
        <Transition
          :css="false"
          @before-enter="onBeforeEnter"
          @enter="onEnter"
          @after-enter="onAfterEnter"
          @leave="onLeave"
        >
          <div
            v-if="expandedItemInRow(row)"
            class="expanded-panel"
            :style="expansionPanelStyle(row)"
          >
            <slot name="expanded-content" :item="expandedItemInRow(row) as unknown" />
          </div>
        </Transition>
      </template>
    </div>
  </div>
</template>

/**
 * Responsive card grid with animated expand/collapse panels.
 * Renders loading skeletons, error/empty states, and data cards with
 * per-row expansion panels that auto-position beneath the expanded card.
 */
<script setup lang="ts">
import { computed, provide } from 'vue'
import { Skeleton } from '@/components/ui/skeleton'
import { useMediaQuery, useWindowSize } from '@vueuse/core'
import { EXPANDED_CONTENT_EMBEDDED_KEY } from '@/components/common/expandedContentKey'

defineOptions({
  inheritAttrs: false,
})

provide(EXPANDED_CONTENT_EMBEDDED_KEY, true)

interface Props {
  /** The data items to render as cards. */
  items: unknown[]
  /**
   * Returns a unique ID for a given item.
   *
   * @param item - The data item.
   * @returns The unique identifier string.
   */
  getItemId: (item: unknown) => string
  /**
   * Returns whether a given item ID is currently expanded.
   *
   * @param id - The item ID to check.
   * @returns True if the item is expanded.
   */
  isExpanded: (id: string) => boolean
  /** Whether data is currently loading. */
  loading: boolean
  /** Error message to display, if any. */
  error?: string
  /** Whether the data set is empty. */
  isEmpty: boolean
  /** Message shown when the data set is empty. */
  emptyMessage?: string
  /** Number of skeleton placeholders to show while loading. */
  skeletonCount?: number
}

const props = withDefaults(defineProps<Props>(), {
  error: undefined,
  emptyMessage: 'No records found',
  skeletonCount: 8,
})

const { width } = useWindowSize()

/** Number of grid columns based on the current viewport width. */
const columnCount = computed(() => {
  if (width.value >= 1280) return 4
  if (width.value >= 768) return 2
  return 1
})

/** Number of columns the expansion panel should span (max 2). */
const panelSpan = computed(() => Math.min(2, columnCount.value))

/** Splits items into rows of `columnCount` items for grid rendering. */
const cardRows = computed(() => {
  const rows: unknown[][] = []
  const items = props.items
  for (let i = 0; i < items.length; i += columnCount.value) {
    rows.push(items.slice(i, i + columnCount.value))
  }
  return rows
})

/**
 * Finds the expanded item within a row, if any.
 *
 * @param row - The row of items to search.
 * @returns The expanded item, or undefined if none is expanded.
 */
function expandedItemInRow(row: unknown[]): unknown | undefined {
  return row.find(item => props.isExpanded(props.getItemId(item)))
}

/**
 * Computes inline styles to position the expansion panel centred beneath the expanded card.
 *
 * @param row - The row containing the expanded item.
 * @returns CSS properties for width and margin-left positioning.
 */
function expansionPanelStyle(row: unknown[]): Record<string, string> {
  const expandedItem = expandedItemInRow(row)
  if (!expandedItem) return {}

  const index = row.indexOf(expandedItem)
  const cols = columnCount.value
  const span = panelSpan.value

  if (cols <= span) {
    return { width: '100%' }
  }

  const totalGaps = cols - 1
  const panelGaps = span - 1

  const panelWidth = `calc(${span} * (100% - ${totalGaps}rem) / ${cols} + ${panelGaps}rem)`
  const cardCentre = `${index} * (100% + 1rem) / ${cols} + (100% - ${totalGaps}rem) / ${2 * cols}`
  const idealLeft = `calc(${cardCentre} - (${span} * (100% - ${totalGaps}rem) / ${cols} + ${panelGaps}rem) / 2)`
  const maxLeft = `calc(100% - (${span} * (100% - ${totalGaps}rem) / ${cols} + ${panelGaps}rem))`

  return {
    width: panelWidth,
    marginLeft: `clamp(0px, ${idealLeft}, ${maxLeft})`,
  }
}

const TRANSITION_DURATION = 250
const prefersReducedMotion = useMediaQuery('(prefers-reduced-motion: reduce)')
const TRANSITION_CSS = `max-height ${TRANSITION_DURATION}ms ease, opacity ${TRANSITION_DURATION}ms ease, transform ${TRANSITION_DURATION}ms ease`

/**
 * Transition hook: sets initial collapsed state before the enter animation.
 *
 * @param el - The DOM element being transitioned.
 */
function onBeforeEnter(el: Element) {
  const htmlEl = el as HTMLElement
  htmlEl.style.overflow = 'hidden'
  htmlEl.style.maxHeight = '0'
  htmlEl.style.opacity = '0'
  htmlEl.style.transform = 'translateY(-1rem)'
}

/**
 * Transition hook: animates the element from collapsed to expanded state.
 *
 * @param el - The DOM element being transitioned.
 * @param done - Callback to signal animation completion.
 */
function onEnter(el: Element, done: () => void) {
  const htmlEl = el as HTMLElement

  if (prefersReducedMotion.value) {
    htmlEl.style.overflow = ''
    htmlEl.style.maxHeight = ''
    htmlEl.style.opacity = ''
    htmlEl.style.transform = ''
    done()
    return
  }

  requestAnimationFrame(() => {
    htmlEl.style.transition = TRANSITION_CSS
    htmlEl.style.maxHeight = `${htmlEl.scrollHeight}px`
    htmlEl.style.opacity = '1'
    htmlEl.style.transform = 'translateY(0)'
    setTimeout(done, TRANSITION_DURATION)
  })
}

/**
 * Transition hook: cleans up inline styles after the enter animation completes.
 *
 * @param el - The DOM element that was transitioned.
 */
function onAfterEnter(el: Element) {
  const htmlEl = el as HTMLElement
  htmlEl.style.overflow = ''
  htmlEl.style.maxHeight = ''
  htmlEl.style.opacity = ''
  htmlEl.style.transform = ''
  htmlEl.style.transition = ''
}

/**
 * Transition hook: animates the element from expanded to collapsed state.
 *
 * @param el - The DOM element being transitioned.
 * @param done - Callback to signal animation completion.
 */
function onLeave(el: Element, done: () => void) {
  const htmlEl = el as HTMLElement

  if (prefersReducedMotion.value) {
    done()
    return
  }

  htmlEl.style.overflow = 'hidden'
  htmlEl.style.maxHeight = `${htmlEl.scrollHeight}px`
  htmlEl.style.opacity = '1'
  htmlEl.style.transform = 'translateY(0)'
  htmlEl.style.transition = TRANSITION_CSS

  void htmlEl.offsetHeight

  htmlEl.style.maxHeight = '0'
  htmlEl.style.opacity = '0'
  htmlEl.style.transform = 'translateY(-1rem)'
  setTimeout(done, TRANSITION_DURATION)
}
</script>

<style scoped>
.card-wrapper {
  transition: opacity 250ms ease;
}

.card-dimmed {
  opacity: 0.4;
}

.expanded-panel {
  border: 1px solid var(--border);
  border-radius: 0.75rem;
  background: color-mix(in oklch, var(--accent) 50%, transparent);
  padding: 0.5rem;
}
</style>
