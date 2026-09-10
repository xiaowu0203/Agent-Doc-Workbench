<template>
  <div class="workbench-layout">
    <AppSidebar :collapsed="sidebarCollapsed" />
    <div class="workbench-layout__main">
      <AppTopbar :collapsed="sidebarCollapsed" @toggle-sidebar="appStore.toggleSidebar" />
      <main class="workbench-layout__content">
        <RouterView />
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterView } from 'vue-router'

import AppSidebar from '@/shared/components/AppSidebar.vue'
import AppTopbar from '@/shared/components/AppTopbar.vue'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()
const narrowScreen = ref(false)
const sidebarCollapsed = computed(() => appStore.sidebarCollapsed || narrowScreen.value)
let narrowScreenQuery: MediaQueryList | null = null

function updateNarrowScreen(event: MediaQueryListEvent | MediaQueryList): void {
  narrowScreen.value = event.matches
}

onMounted(() => {
  narrowScreenQuery = window.matchMedia('(max-width: 720px)')
  updateNarrowScreen(narrowScreenQuery)
  narrowScreenQuery.addEventListener('change', updateNarrowScreen)
})

onBeforeUnmount(() => narrowScreenQuery?.removeEventListener('change', updateNarrowScreen))
</script>

<style scoped>
.workbench-layout {
  display: flex;
  height: 100vh;
  min-height: 0;
  overflow: hidden;
  background: var(--adw-page-background);
}

.workbench-layout__main {
  display: flex;
  height: 100%;
  min-width: 0;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.workbench-layout__content {
  min-height: 0;
  width: 100%;
  max-width: var(--adw-content-max-width);
  flex: 1;
  margin: 0 auto;
  overflow-y: auto;
  padding: var(--adw-space-7);
}

@media (max-width: 720px) {
  .workbench-layout__content {
    padding: var(--adw-space-5);
  }
}
</style>
