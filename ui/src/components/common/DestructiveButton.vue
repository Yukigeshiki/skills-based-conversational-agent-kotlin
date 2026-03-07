<template>
  <Button
    :type="type"
    variant="outline"
    :size="size"
    :disabled="disabled"
    @click="handleClick"
    class="cursor-pointer border-destructive/60 text-destructive/70 bg-transparent hover:bg-destructive/10 hover:border-destructive hover:text-destructive disabled:cursor-not-allowed"
  >
    <slot>{{ label }}</slot>
  </Button>
</template>

/** Outline button styled with destructive colours for dangerous actions (delete, remove). */
<script setup lang="ts">
import { Button } from '@/components/ui/button'

interface Props {
  /** HTML button type attribute. */
  type?: 'button' | 'submit' | 'reset'
  /** Button size variant. */
  size?: 'default' | 'sm' | 'lg' | 'icon'
  /** Whether the button is disabled. */
  disabled?: boolean
  /** Fallback label text when no slot content is provided. */
  label?: string
}

interface Emits {
  /**
   * Emitted on button click.
   *
   * @param event - The native mouse event.
   */
  (e: 'click', event: MouseEvent): void
}

withDefaults(defineProps<Props>(), {
  type: 'button',
  size: 'default',
  disabled: false,
  label: '',
})

const emit = defineEmits<Emits>()

/**
 * Forwards the click event to the parent.
 *
 * @param event - The native mouse event.
 */
function handleClick(event: MouseEvent) {
  emit('click', event)
}
</script>
