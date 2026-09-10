import type { RouteRecordRaw } from 'vue-router'

import WorkbenchLayout from '@/layouts/WorkbenchLayout.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { PLATFORM_ROLES } from '@/shared/constants/platform-roles'

const AccessControlView = () => import('@/views/AccessControlView.vue')
const AgentManagementView = () => import('@/views/AgentManagementView.vue')
const ChangeRequestReviewView = () => import('@/views/ChangeRequestReviewView.vue')
const DocumentEditorView = () => import('@/views/DocumentEditorView.vue')
const DocumentVersionHistoryView = () => import('@/views/DocumentVersionHistoryView.vue')
const ForbiddenView = () => import('@/views/ForbiddenView.vue')
const HomeView = () => import('@/views/HomeView.vue')
const LoginView = () => import('@/views/LoginView.vue')
const McpManagementView = () => import('@/views/McpManagementView.vue')
const ModelManagementView = () => import('@/views/ModelManagementView.vue')
const PlatformDepartmentManagementView = () =>
  import('@/views/PlatformDepartmentManagementView.vue')
const PlatformUserManagementView = () => import('@/views/PlatformUserManagementView.vue')
const SkillManagementView = () => import('@/views/SkillManagementView.vue')
const SpaceOverviewView = () => import('@/views/SpaceOverviewView.vue')
const TaskCreateView = () => import('@/views/TaskCreateView.vue')
const TaskDetailView = () => import('@/views/TaskDetailView.vue')
const TaskListView = () => import('@/views/TaskListView.vue')
const UsageAuditView = () => import('@/views/UsageAuditView.vue')

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
