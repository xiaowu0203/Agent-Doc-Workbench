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

    <div class="app-topbar__search" role="search" aria-label="全局搜索（尚未开放）">
      <el-icon><Search /></el-icon>
      <span>搜索文档、任务或 Agent</span>
      <kbd>⌘ K</kbd>
    </div>

    <div class="app-topbar__spacer" />
    <el-button
      v-if="workspaceStore.hasPermission(SPACE_PERMISSIONS.TASK_CREATE)"
      type="primary"
      @click="showComingSoon"
    >
      <el-icon><Plus /></el-icon>
      新建任务
    </el-button>
    <el-divider direction="vertical" />
    <div class="app-topbar__user" :title="authStore.user?.username">
      <span class="app-topbar__avatar">{{ initials }}</span>
      <span class="app-topbar__username">{{
        authStore.user?.nickname || authStore.user?.username || '当前用户'
      }}</span>
      <el-icon><ArrowDown /></el-icon>
    </div>
  </header>
</template>

<script setup lang="ts">
import { ArrowDown, Expand, Fold, Plus, Search } from '@element-plus/icons-vue'
import { ElButton, ElDivider, ElIcon, ElMessage } from 'element-plus'
import { computed } from 'vue'

import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'

defineProps<{ collapsed: boolean }>()
defineEmits<{ 'toggle-sidebar': [] }>()

const authStore = useAuthStore()
const workspaceStore = useWorkspaceStore()
const initials = computed(() => {
  const name = authStore.user?.nickname || authStore.user?.username || 'AD'
  return name.slice(0, 2).toUpperCase()
})

function showComingSoon(): void {
  ElMessage.info('任务创建页将在后续交付切片开放')
}
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

.app-topbar__search {
  display: flex;
  width: min(100%, 430px);
  height: 38px;
  align-items: center;
  gap: var(--adw-space-2);
  padding: 0 var(--adw-space-3);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  color: var(--adw-text-tertiary);
  background: var(--adw-surface-muted);
  font-size: var(--adw-font-size-body);
}

.app-topbar__search kbd {
  margin-left: auto;
  padding: 1px 6px;
  border: 1px solid var(--adw-border-color);
  border-radius: 4px;
  color: var(--adw-text-tertiary);
  background: var(--adw-surface);
  font-size: 11px;
}

.app-topbar__spacer {
  flex: 1;
}

.app-topbar :deep(.el-divider--vertical) {
  height: 24px;
  margin: 0;
}

.app-topbar__user {
  display: flex;
  align-items: center;
  gap: var(--adw-space-2);
  color: var(--adw-text-primary);
  white-space: nowrap;
}

.app-topbar__avatar {
  display: inline-flex;
  width: 34px;
  height: 34px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #ffffff;
  background: var(--adw-color-primary);
  font-size: 12px;
}

@media (max-width: 720px) {
  .app-topbar {
    padding-inline: var(--adw-space-4);
  }

  .app-topbar__search {
    display: none;
  }

  .app-topbar__username {
    display: none;
  }
}
</style>
