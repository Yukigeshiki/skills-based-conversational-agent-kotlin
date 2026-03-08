<template>
  <Dialog v-model:open="open">
    <DialogContent class="sm:max-w-md">
      <DialogHeader>
        <DialogTitle>Delete Skill</DialogTitle>
        <DialogDescription>
          Are you sure you want to delete this skill? This action cannot be undone.
        </DialogDescription>
      </DialogHeader>

      <div v-if="error" class="text-sm text-destructive">{{ error }}</div>

      <DialogFooter>
        <Button type="button" variant="outline" @click="open = false" class="cursor-pointer">
          Cancel
        </Button>
        <DestructiveButton :disabled="submitting" @click="$emit('confirm')">
          {{ submitting ? 'Deleting...' : 'Delete' }}
        </DestructiveButton>
      </DialogFooter>
    </DialogContent>
  </Dialog>
</template>

/** Confirmation dialog for deleting a skill. */
<script setup lang="ts">
import { Button } from '@/components/ui/button'
import { DestructiveButton } from '@/components/common'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'

interface Props {
  /** Error message from the delete operation, if any. */
  error?: string
  /** Whether a delete request is in flight. */
  submitting: boolean
}

defineProps<Props>()
defineEmits<{ 'confirm': [] }>()

const open = defineModel<boolean>('open', { required: true })
</script>
