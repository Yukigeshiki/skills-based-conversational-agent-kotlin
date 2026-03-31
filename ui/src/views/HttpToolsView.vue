<template>
  <AppLayout>
    <h1 class="text-4xl font-bold text-foreground mb-4">HTTP Tools</h1>
    <p class="text-muted-foreground mb-6">Manage HTTP-based tools for the agent.</p>
    <HttpToolsTable />
  </AppLayout>
</template>

/** HTTP tools management page — renders the http tools table within the app layout. */
<script setup lang="ts">
import { AppLayout } from '@/layouts'
import { HttpToolsTable } from '@/components/tables/http-tools'
</script>
