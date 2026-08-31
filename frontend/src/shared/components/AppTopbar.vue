<template>
  <header class="app-topbar">
    <el-button
      class="app-topbar__toggle"
      text
      aria-label="折叠侧栏"
      @click="$emit('toggle-sidebar')"
    >
      <el-icon :size="22"><Fold v-if="!collapsed" /><Expand v-else /></el-icon>
    </el-button>
    <div class="app-topbar__spacer" />
    <el-tag effect="plain" type="primary">Phase 6 · Foundation</el-tag>
  </header>
</template>

<script setup lang="ts">
import { Expand, Fold } from '@element-plus/icons-vue'
import { ElButton, ElIcon, ElTag } from 'element-plus'

defineProps<{
  collapsed: boolean
}>()

defineEmits<{
  'toggle-sidebar': []
}>()
</script>

<style scoped>
.app-topbar {
  display: flex;
  height: var(--adw-topbar-height);
  align-items: center;
  gap: var(--adw-space-4);
  padding: 0 var(--adw-space-6);
  border-bottom: 1px solid var(--adw-border-color);
  background: var(--adw-surface);
}

.app-topbar__toggle {
  color: var(--adw-text-primary);
}

.app-topbar__spacer {
  flex: 1;
}
</style>
