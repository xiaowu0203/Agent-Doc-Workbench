import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SpaceOverviewView from './SpaceOverviewView.vue'

import * as overviewApi from '@/features/workspace/api/space-overview-api'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/features/workspace/api/space-overview-api', () => ({
  getAgentOverviewStats: vi.fn(),
  getDocumentStats: vi.fn(),
  getMonthlyTokenBudget: vi.fn(),
  getPendingChangeStats: vi.fn(),
  getTaskStats: vi.fn(),
  listRecentDocuments: vi.fn(),
  listPendingChanges: vi.fn(),
  listTaskActivities: vi.fn(),
}))

const allPermissions = Object.values(SPACE_PERMISSIONS)

async function mountOverview(permissions = allPermissions) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspaceStore = useWorkspaceStore()
  workspaceStore.spaces = [
    {
      id: 7,
      name: '产品研发空间',
      description: '文档协作空间',
      ownerId: 1,
      tokenBudget: null,
      status: 'ACTIVE',
      role: null,
      platformSuperAdmin: false,
      createdAt: '2026-08-31T00:00:00Z',
    },
  ]
  workspaceStore.setCurrentSpace(7)
  workspaceStore.setEffectivePermissions({
    spaceId: 7,
    platformSuperAdmin: false,
    role: null,
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/spaces/:spaceId/overview', name: 'space-overview', component: SpaceOverviewView },
      {
        path: '/spaces/:spaceId/documents/:documentId?',
        name: 'space-documents',
        component: SpaceOverviewView,
      },
      { path: '/spaces/:spaceId/tasks', name: 'space-tasks', component: SpaceOverviewView },
      {
        path: '/spaces/:spaceId/approvals',
        name: 'space-approvals',
        component: SpaceOverviewView,
      },
      { path: '/spaces/:spaceId/usage', name: 'space-usage', component: SpaceOverviewView },
      {
        path: '/spaces/:spaceId/tasks/:taskId',
        name: 'space-task-detail',
        component: SpaceOverviewView,
      },
      { path: '/spaces/:spaceId/agents', name: 'space-agents', component: SpaceOverviewView },
      { path: '/spaces/:spaceId/skills', name: 'space-skills', component: SpaceOverviewView },
      {
        path: '/spaces/:spaceId/mcp-servers',
        name: 'space-mcp-servers',
        component: SpaceOverviewView,
      },
    ],
  })
  await router.push('/spaces/7/overview')
  await router.isReady()
  const wrapper = mount(SpaceOverviewView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.mocked(overviewApi.listRecentDocuments).mockResolvedValue({
    records: [
      {
        id: 1,
        title: '产品方案',
        docType: 'FORMAL',
        updatedAt: '2026-08-31T10:00:00Z',
        updatedByName: '张三',
      },
    ],
    total: 1,
    pageNum: 1,
    pageSize: 5,
  })
  vi.mocked(overviewApi.getDocumentStats).mockResolvedValue({
    totalCount: 5,
    countAsOfLastMonth: 3,
  })
  vi.mocked(overviewApi.getTaskStats).mockResolvedValue({ totalCount: 8, countAsOfYesterday: 6 })
  vi.mocked(overviewApi.getPendingChangeStats).mockResolvedValue({
    pendingCount: 2,
    pendingCountAsOfYesterday: 1,
  })
  vi.mocked(overviewApi.listTaskActivities).mockResolvedValue({
    records: [
      {
        id: 11,
        name: '文档审计',
        agentId: 2,
        status: 'RUNNING',
        operatorName: '张三',
        activityAt: '2026-08-31T14:32:00Z',
      },
    ],
    total: 1,
    pageNum: 1,
    pageSize: 6,
  })
  vi.mocked(overviewApi.listPendingChanges).mockResolvedValue({
    records: [],
    total: 1,
    pageNum: 1,
    pageSize: 3,
  })
  vi.mocked(overviewApi.getMonthlyTokenBudget).mockResolvedValue({
    usedTokens: 150,
    monthlyTokenBudget: 1000,
  })
  vi.mocked(overviewApi.getAgentOverviewStats).mockResolvedValue({
    activeAgentCount: 1,
    activeSkillCount: 3,
    enabledMcpCount: 2,
  })
})

describe('SpaceOverviewView', () => {
  it('renders only data allowed by the current space permissions', async () => {
    const wrapper = await mountOverview([
      SPACE_PERMISSIONS.SPACE_READ,
      SPACE_PERMISSIONS.DOCUMENT_READ,
    ])

    expect(wrapper.text()).toContain('最近文档')
    expect(wrapper.text()).toContain('查看全部')
    expect(wrapper.text()).toContain('产品方案')
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).not.toContain('最近任务')
    expect(wrapper.text()).not.toContain('本月 Token')
  })

  it('renders real summary totals and avoids placeholder numbers', async () => {
    const wrapper = await mountOverview()

    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('较上月 +2')
    expect(wrapper.text()).toContain('较昨日 +2')
    expect(wrapper.text()).toContain('较昨日 +1')
    expect(wrapper.text()).toContain('本月 Token')
    expect(wrapper.text()).toContain('150')
    expect(wrapper.text()).toContain('待审批变更')
    expect(wrapper.text()).toContain('执行动态')
    expect(wrapper.text()).toContain('文档审计')
  })

  it('links summary cards to the corresponding space pages', async () => {
    const wrapper = await mountOverview()
    const cards = wrapper.findAll('.stat-card')

    expect(cards).toHaveLength(4)
    expect(cards.map((card) => card.attributes('href'))).toEqual([
      '/spaces/7/documents',
      '/spaces/7/tasks',
      '/spaces/7/approvals',
      '/spaces/7/usage',
    ])
  })

  it('links overview sections and records to their detail pages', async () => {
    const wrapper = await mountOverview()

    expect(
      wrapper.findAll('.overview-panel__view-all').map((link) => link.attributes('href')),
    ).toEqual(['/spaces/7/documents', '/spaces/7/tasks'])
    expect(wrapper.find('.document-row:not(.document-row--header)').attributes('href')).toBe(
      '/spaces/7/documents/1',
    )
    expect(wrapper.find('.task-row').attributes('href')).toBe('/spaces/7/tasks/11')
    expect(wrapper.findAll('.ability-card').map((link) => link.attributes('href'))).toEqual([
      '/spaces/7/agents',
      '/spaces/7/skills',
      '/spaces/7/mcp-servers',
    ])
    expect(wrapper.find('.pending-row--action').attributes('href')).toBe('/spaces/7/approvals')
    expect(wrapper.text()).not.toMatch(/Agent 能力概览[\s\S]*查看全部/)
    expect(wrapper.text()).not.toMatch(/待处理事项[\s\S]*查看全部/)
  })
})
