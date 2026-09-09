import type { RouteRecordRaw } from 'vue-router'

import WorkbenchLayout from '@/layouts/WorkbenchLayout.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { PLATFORM_ROLES } from '@/shared/constants/platform-roles'
import ForbiddenView from '@/views/ForbiddenView.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import SpaceOverviewView from '@/views/SpaceOverviewView.vue'
import AccessControlView from '@/views/AccessControlView.vue'
import DocumentEditorView from '@/views/DocumentEditorView.vue'
import DocumentVersionHistoryView from '@/views/DocumentVersionHistoryView.vue'
import SkillManagementView from '@/views/SkillManagementView.vue'
import McpManagementView from '@/views/McpManagementView.vue'
import AgentManagementView from '@/views/AgentManagementView.vue'
import ModelManagementView from '@/views/ModelManagementView.vue'
import PlatformDepartmentManagementView from '@/views/PlatformDepartmentManagementView.vue'
import PlatformUserManagementView from '@/views/PlatformUserManagementView.vue'
import TaskCreateView from '@/views/TaskCreateView.vue'
import TaskListView from '@/views/TaskListView.vue'
import TaskDetailView from '@/views/TaskDetailView.vue'
import ChangeRequestReviewView from '@/views/ChangeRequestReviewView.vue'
import UsageAuditView from '@/views/UsageAuditView.vue'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { guestOnly: true },
  },
  {
    path: '/',
    component: WorkbenchLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'home',
        component: HomeView,
      },
      {
        path: 'spaces/:spaceId/overview',
        name: 'space-overview',
        component: SpaceOverviewView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.SPACE_READ,
        },
      },
      {
        path: 'spaces/:spaceId/documents/:documentId/versions',
        name: 'document-version-history',
        component: DocumentVersionHistoryView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.DOCUMENT_READ,
        },
      },
      {
        path: 'spaces/:spaceId/documents/:documentId?',
        name: 'space-documents',
        component: DocumentEditorView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.DOCUMENT_READ,
        },
      },
      {
        path: 'spaces/:spaceId/tasks',
        name: 'space-tasks',
        component: TaskListView,
        meta: { requiresSpace: true, permission: SPACE_PERMISSIONS.TASK_READ },
      },
      {
        path: 'spaces/:spaceId/tasks/new',
        name: 'space-task-create',
        component: TaskCreateView,
        meta: { requiresSpace: true, permission: SPACE_PERMISSIONS.TASK_CREATE },
      },
      {
        path: 'spaces/:spaceId/tasks/:taskId',
        name: 'space-task-detail',
        component: TaskDetailView,
        meta: { requiresSpace: true, permission: SPACE_PERMISSIONS.TASK_READ },
      },
      {
        path: 'spaces/:spaceId/approvals',
        name: 'space-approvals',
        component: ChangeRequestReviewView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.CHANGE_REQUEST_READ,
        },
      },
      {
        path: 'spaces/:spaceId/usage',
        name: 'space-usage',
        component: UsageAuditView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.USAGE_READ,
        },
      },
      {
        path: 'spaces/:spaceId/access/roles',
        name: 'space-access-roles',
        component: AccessControlView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.ROLE_READ,
        },
      },
      {
        path: 'spaces/:spaceId/agents',
        name: 'space-agents',
        component: AgentManagementView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.AGENT_READ,
        },
      },
      {
        path: 'spaces/:spaceId/skills',
        name: 'space-skills',
        component: SkillManagementView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.SKILL_READ,
        },
      },
      {
        path: 'spaces/:spaceId/mcp-servers',
        name: 'space-mcp-servers',
        component: McpManagementView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.MCP_READ,
        },
      },
      {
        path: 'spaces/:spaceId/access/members',
        name: 'space-access-members',
        component: AccessControlView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.MEMBER_READ,
        },
      },
      {
        path: 'system/models',
        name: 'system-models',
        component: ModelManagementView,
        meta: { platformRole: PLATFORM_ROLES.SUPER_ADMIN },
      },
      {
        path: 'system/users',
        name: 'system-users',
        component: PlatformUserManagementView,
        meta: { platformRole: PLATFORM_ROLES.SUPER_ADMIN },
      },
      {
        path: 'system/departments',
        name: 'system-departments',
        component: PlatformDepartmentManagementView,
        meta: { platformRole: PLATFORM_ROLES.SUPER_ADMIN },
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: ForbiddenView,
      },
    ],
  },
]
