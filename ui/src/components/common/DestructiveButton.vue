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

<script setup lang="ts">
import { Button } from '@/components/ui/button'

interface Props {
  type?: 'button' | 'submit' | 'reset'
  size?: 'default' | 'sm' | 'lg' | 'icon'
  disabled?: boolean
  label?: string
}

interface Emits {
  (e: 'click', event: MouseEvent): void
}

withDefaults(defineProps<Props>(), {
  type: 'button',
  size: 'default',
  disabled: false,
  label: '',
})

const emit = defineEmits<Emits>()

function handleClick(event: MouseEvent) {
  emit('click', event)
}
</script>
