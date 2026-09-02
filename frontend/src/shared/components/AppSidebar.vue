<template>
  <aside class="app-sidebar" :class="{ 'app-sidebar--collapsed': collapsed }">
    <div class="app-sidebar__brand">
      <BrandMark :compact="collapsed" stacked />
    </div>

    <div v-if="!collapsed" class="app-sidebar__space">
      <el-icon :size="18"><Briefcase /></el-icon>
      <el-select
        v-model="selectedSpaceId"
        aria-label="切换空间"
        class="app-sidebar__space-select"
        size="large"
        @change="switchSpace"
      >
        <el-option
          v-for="space in workspaceStore.spaces"
          :key="space.id"
          :label="space.name"
          :value="space.id"
        />
      </el-select>
    </div>

    <nav class="app-sidebar__navigation" aria-label="空间导航">
      <template v-for="(item, index) in visibleMenuItems" :key="item.label">
        <div
          v-if="!collapsed && item.group && visibleMenuItems[index - 1]?.group !== item.group"
          class="app-sidebar__group-label"
        >
          {{ item.group }}
        </div>
        <RouterLink v-if="item.path" class="app-sidebar__link" :to="item.path">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <span v-if="!collapsed">{{ item.label }}</span>
        </RouterLink>
        <button v-else class="app-sidebar__link app-sidebar__link--disabled" type="button" disabled>
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <span v-if="!collapsed">{{ item.label }}</span>
        </button>
      </template>
    </nav>

    <div class="app-sidebar__footer">
      <span class="app-sidebar__avatar">{{ initials }}</span>
      <div v-if="!collapsed">
        <strong>{{ authStore.user?.nickname || authStore.user?.username || '当前用户' }}</strong>
        <span>{{ workspaceStore.currentSpace?.role?.displayName || '空间成员' }}</span>
      </div>
    </div>
  </aside>
</template>

<script setup lang="ts">
import {
  Aim,
  Briefcase,
  Checked,
  DataAnalysis,
  Document,
  Grid,
  Lock,
  Operation,
  SetUp,
  Tickets,
} from '@element-plus/icons-vue'
import { ElIcon, ElOption, ElSelect } from 'element-plus'
import type { Component } from 'vue'
import { computed, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'

import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import type { EntityId } from '@/features/workspace/types'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'

defineProps<{ collapsed: boolean }>()

interface MenuItem {
  label: string
  icon: Component
  permission: (typeof SPACE_PERMISSIONS)[keyof typeof SPACE_PERMISSIONS]
  path: string | null
  group?: string
}

const authStore = useAuthStore()
const workspaceStore = useWorkspaceStore()
const router = useRouter()
const selectedSpaceId = ref<EntityId | null>(workspaceStore.currentSpaceId)

const menuItems: MenuItem[] = [
  { label: '总览', icon: Grid, permission: SPACE_PERMISSIONS.SPACE_READ, path: 'overview' },
  { label: '文档', icon: Document, permission: SPACE_PERMISSIONS.DOCUMENT_READ, path: 'documents' },
  { label: '任务', icon: Tickets, permission: SPACE_PERMISSIONS.TASK_READ, path: null },
  {
    label: '变更审批',
    icon: Checked,
    permission: SPACE_PERMISSIONS.CHANGE_REQUEST_READ,
    path: null,
  },
  { label: 'Agent', icon: Aim, permission: SPACE_PERMISSIONS.AGENT_READ, path: null },
  { label: 'Skill', icon: SetUp, permission: SPACE_PERMISSIONS.SKILL_READ, path: 'skills' },
  { label: 'MCP 服务', icon: Operation, permission: SPACE_PERMISSIONS.MCP_READ, path: null },
  { label: '用量与审计', icon: DataAnalysis, permission: SPACE_PERMISSIONS.USAGE_READ, path: null },
  {
    label: '角色与权限',
    icon: Lock,
    permission: SPACE_PERMISSIONS.ROLE_READ,
    path: 'access/roles',
    group: '组织与权限',
  },
  {
    label: '成员管理',
    icon: Briefcase,
    permission: SPACE_PERMISSIONS.MEMBER_READ,
    path: 'access/members',
    group: '组织与权限',
  },
]

const visibleMenuItems = computed(() =>
  menuItems
    .filter((item) => workspaceStore.hasPermission(item.permission))
    .map((item) => ({
      ...item,
      path:
        item.path && workspaceStore.currentSpaceId
          ? `/spaces/${workspaceStore.currentSpaceId}/${item.path}`
          : null,
    })),
)

const initials = computed(() => {
  const name = authStore.user?.nickname || authStore.user?.username || 'AD'
  return name.slice(0, 2).toUpperCase()
})

watch(
  () => workspaceStore.currentSpaceId,
  (spaceId) => {
    selectedSpaceId.value = spaceId
  },
)

async function switchSpace(spaceId: EntityId): Promise<void> {
  if (String(spaceId) === String(workspaceStore.currentSpaceId)) return
  await router.push(`/spaces/${spaceId}/overview`)
}
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

.app-sidebar__space {
  display: flex;
  align-items: center;
  gap: var(--adw-space-2);
  margin: var(--adw-space-4) var(--adw-space-3) var(--adw-space-2);
  padding: var(--adw-space-2) var(--adw-space-3);
  border: 1px solid rgb(255 255 255 / 18%);
  border-radius: var(--adw-radius-sm);
}

.app-sidebar__space-select {
  min-width: 0;
  flex: 1;
}

.app-sidebar__space-select :deep(.el-input__wrapper) {
  padding: 0;
  background: transparent;
  box-shadow: none;
}

.app-sidebar__space-select :deep(.el-input__inner) {
  color: #ffffff;
}

.app-sidebar__space-select :deep(.el-select__caret) {
  color: rgb(255 255 255 / 80%);
}

.app-sidebar__navigation {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: var(--adw-space-2);
  padding: var(--adw-space-5) var(--adw-space-3);
}

.app-sidebar__group-label {
  margin: var(--adw-space-5) var(--adw-space-4) var(--adw-space-1);
  color: rgb(255 255 255 / 56%);
  font-size: 12px;
  font-weight: 600;
}

.app-sidebar__link {
  display: flex;
  min-height: 46px;
  align-items: center;
  gap: var(--adw-space-3);
  padding: 0 var(--adw-space-4);
  border: 0;
  border-radius: var(--adw-radius-sm);
  color: rgb(255 255 255 / 78%);
  background: transparent;
  font: inherit;
  text-decoration: none;
}

.app-sidebar__link:hover,
.app-sidebar__link.router-link-exact-active {
  color: #ffffff;
  background: var(--adw-sidebar-active-background);
}

.app-sidebar__link--disabled {
  cursor: not-allowed;
  opacity: 0.65;
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
  display: inline-flex;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #ffffff;
  background: var(--adw-color-primary);
  font-size: 12px;
}

.app-sidebar__footer div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.app-sidebar__footer strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-sidebar__footer span:not(.app-sidebar__avatar) {
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
