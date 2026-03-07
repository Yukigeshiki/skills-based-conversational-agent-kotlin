<template>
  <AppLayout>
    <h1 class="text-4xl font-bold text-foreground mb-4">Skills</h1>
    <p class="text-muted-foreground mb-6">Manage agent skills and capabilities.</p>
    <SkillsTable />
  </AppLayout>
</template>

<script setup lang="ts">
import { AppLayout } from '@/layouts'
import { SkillsTable } from '@/components/tables/skills'
</script>