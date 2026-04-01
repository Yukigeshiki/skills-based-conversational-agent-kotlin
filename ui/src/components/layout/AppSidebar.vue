<template>
  <aside
    :class="[
      'fixed left-0 top-0 h-screen bg-card border-r border-border transition-all duration-300 ease-in-out z-40',
      isExpanded ? 'w-64' : 'w-16'
    ]"
  >
    <!-- Logo and toggle section -->
    <div class="flex flex-col items-center px-4 pt-4 pb-2 gap-4">
      <!-- Logo -->
      <div class="w-16 h-16 rounded-md flex items-center justify-center">
        <Bot class="h-8 w-8 text-primary" />
      </div>

      <!-- Toggle button -->
      <button
        @click="toggleSidebar"
        class="p-2 rounded-md hover:bg-accent transition-colors cursor-pointer"
        :aria-label="isExpanded ? 'Collapse sidebar' : 'Expand sidebar'"
      >
        <ChevronLeft v-if="isExpanded" class="h-5 w-5" />
        <ChevronRight v-else class="h-5 w-5" />
      </button>
    </div>

    <!-- Navigation items -->
    <nav class="flex flex-col gap-1 px-2 pt-4 pb-2">
      <router-link
        v-for="item in navItems"
        :key="item.label"
        :to="item.path"
        class="flex items-center gap-3 px-3 py-2 rounded-md hover:bg-accent transition-colors cursor-pointer"
        active-class="bg-accent"
      >
        <component :is="item.icon" class="h-5 w-5 shrink-0" />
        <span
          v-if="isExpanded"
          class="text-sm font-medium whitespace-nowrap"
        >
          {{ item.label }}
        </span>
      </router-link>
    </nav>
  </aside>
</template>

/** Collapsible sidebar with logo, toggle button, and navigation links. */
<script setup lang="ts">
import { type Component } from 'vue'
import { useSidebar } from '@/composables/ui'
import {
  Bot,
  ChevronLeft,
  ChevronRight,
  Ghost,
  MessageSquare,
  Cable,
  BookOpen,
} from 'lucide-vue-next'

/** A single navigation item rendered in the sidebar. */
interface NavItem {
  /** The route path to navigate to. */
  path: string
  /** Display label shown when the sidebar is expanded. */
  label: string
  /** Lucide icon component rendered alongside the label. */
  icon: Component
}

const { isExpanded, toggle } = useSidebar()

/** The static list of navigation items displayed in the sidebar. */
const navItems: NavItem[] = [
  {
    path: '/chat',
    label: 'Chat',
    icon: MessageSquare,
  },
  {
    path: '/skills',
    label: 'Skills',
    icon: BookOpen,
  },
  {
    path: '/http-tools',
    label: 'HTTP Tools',
    icon: Cable,
  },
  {
    path: '/identity',
    label: 'Identity',
    icon: Ghost,
  },
]

/** Toggles the sidebar between expanded and collapsed states. */
function toggleSidebar() {
  toggle()
}
</script>
