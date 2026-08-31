<template>
  <aside class="app-sidebar" :class="{ 'app-sidebar--collapsed': collapsed }">
    <div class="app-sidebar__brand">
      <BrandMark :compact="collapsed" stacked />
    </div>

    <nav class="app-sidebar__navigation" aria-label="主导航">
      <RouterLink class="app-sidebar__link" to="/">
        <el-icon :size="20"><Grid /></el-icon>
        <span v-if="!collapsed">基础层</span>
      </RouterLink>
    </nav>

    <div class="app-sidebar__footer">
      <span class="app-sidebar__avatar">AD</span>
      <div v-if="!collapsed">
        <strong>Agent Doc</strong>
        <span>Phase 6</span>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import { Grid } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import { RouterLink } from 'vue-router'

import BrandMark from './BrandMark.vue'

defineProps<{
  collapsed: boolean
}>()
</script>

<style scoped>
.app-sidebar {
  display: flex;
  width: var(--adw-sidebar-width);
  min-height: 100vh;
  flex-direction: column;
  color: #ffffff;
  background: var(--adw-sidebar-background);
  transition: width 180ms ease;
}

.app-sidebar--collapsed {
  width: var(--adw-sidebar-collapsed-width);
}

.app-sidebar__brand {
  display: flex;
  height: var(--adw-topbar-height);
  align-items: center;
  gap: var(--adw-space-3);
  padding: 0 var(--adw-space-5);
  border-bottom: 1px solid rgb(255 255 255 / 10%);
}

.app-sidebar__avatar {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  color: #ffffff;
  background: var(--adw-color-primary);
}

.app-sidebar__navigation {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--adw-space-2);
  padding: var(--adw-space-6) var(--adw-space-3);
}

.app-sidebar__link {
  display: flex;
  min-height: 48px;
  align-items: center;
  gap: var(--adw-space-3);
  padding: 0 var(--adw-space-4);
  border-radius: var(--adw-radius-sm);
  color: rgb(255 255 255 / 78%);
  text-decoration: none;
}

.app-sidebar__link:hover,
.app-sidebar__link.router-link-exact-active {
  color: #ffffff;
  background: var(--adw-sidebar-active-background);
}

.app-sidebar__footer {
  display: flex;
  min-height: 78px;
  align-items: center;
  gap: var(--adw-space-3);
  padding: var(--adw-space-4) var(--adw-space-5);
  border-top: 1px solid rgb(255 255 255 / 10%);
}

.app-sidebar__avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  font-size: 12px;
}

.app-sidebar__footer div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.app-sidebar__footer span {
  color: rgb(255 255 255 / 60%);
  font-size: 12px;
}

.app-sidebar--collapsed .app-sidebar__brand,
.app-sidebar--collapsed .app-sidebar__footer {
  justify-content: center;
  padding-inline: 0;
}

.app-sidebar--collapsed .app-sidebar__link {
  justify-content: center;
  padding-inline: 0;
}
</style>
