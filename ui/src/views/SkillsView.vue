<template>
  <AppLayout>
    <h1 class="text-4xl font-bold text-foreground mb-4">Skills</h1>
    <p class="text-muted-foreground mb-6">Manage agent skills and capabilities.</p>
    <SkillsTable />
  </AppLayout>
</template>

/** Skills management page — renders the skills table within the app layout. */
<script setup lang="ts">
import { AppLayout } from '@/layouts'
import { SkillsTable } from '@/components/tables/skills'
</script>