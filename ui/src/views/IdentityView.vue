<template>
  <AppLayout>
    <h1 class="text-4xl font-bold text-foreground mb-4">Identity</h1>
    <p class="text-muted-foreground mb-6">Configure your agent's personality with an Identity prompt.</p>
    <IdentityEditor />
  </AppLayout>
</template>

/** Identity configuration page — edits the global agent personality. */
<script setup lang="ts">
import { AppLayout } from '@/layouts'
import { IdentityEditor } from '@/components/identity'
</script>
