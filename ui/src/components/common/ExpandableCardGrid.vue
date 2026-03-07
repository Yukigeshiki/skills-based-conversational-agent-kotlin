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
  items: unknown[]
  getItemId: (item: unknown) => string
  isExpanded: (id: string) => boolean
  loading: boolean
  error?: string
  isEmpty: boolean
  emptyMessage?: string
  skeletonCount?: number
}

const props = withDefaults(defineProps<Props>(), {
  error: undefined,
  emptyMessage: 'No records found',
  skeletonCount: 8,
})

const { width } = useWindowSize()

const columnCount = computed(() => {
  if (width.value >= 1280) return 4
  if (width.value >= 768) return 2
  return 1
})

const panelSpan = computed(() => Math.min(2, columnCount.value))

const cardRows = computed(() => {
  const rows: unknown[][] = []
  const items = props.items
  for (let i = 0; i < items.length; i += columnCount.value) {
    rows.push(items.slice(i, i + columnCount.value))
  }
  return rows
})

function expandedItemInRow(row: unknown[]): unknown | undefined {
  return row.find(item => props.isExpanded(props.getItemId(item)))
}

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

function onBeforeEnter(el: Element) {
  const htmlEl = el as HTMLElement
  htmlEl.style.overflow = 'hidden'
  htmlEl.style.maxHeight = '0'
  htmlEl.style.opacity = '0'
  htmlEl.style.transform = 'translateY(-1rem)'
}

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

function onAfterEnter(el: Element) {
  const htmlEl = el as HTMLElement
  htmlEl.style.overflow = ''
  htmlEl.style.maxHeight = ''
  htmlEl.style.opacity = ''
  htmlEl.style.transform = ''
  htmlEl.style.transition = ''
}

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
