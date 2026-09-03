import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import AgentManagementView from './AgentManagementView.vue'

import * as agentApi from '@/features/agent/api/agent-api'
import type { AgentCard } from '@/features/agent/types'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/features/agent/api/agent-api', () => ({
  createAgent: vi.fn(),
  deleteAgent: vi.fn(),
  getAgent: vi.fn(),
  getAgentOverviewStats: vi.fn(),
  listAgentMcpBindings: vi.fn().mockResolvedValue([]),
  listAgentSkills: vi.fn().mockResolvedValue([]),
  listAgents: vi.fn(),
  listModels: vi.fn(),
  replaceAgentMcpBindings: vi.fn(),
  replaceAgentSkills: vi.fn(),
  searchAgents: vi.fn(),
  toAgentUpdateInput: vi.fn(),
  updateAgent: vi.fn(),
}))

const agent: AgentCard = {
  id: 41,
  spaceId: 7,
  name: '文档审计 Agent',
  description: '检查事实、结构与发布风险',
  modelId: 3,
  modelDisplayName: 'GPT-5.2',
  skillSelectionMode: 'ROUTER',
  externalMcpEnabled: true,
  tokenBudget: 50000,
  maxIterations: 12,
  executionTimeoutSeconds: 300,
  configVersion: 5,
  status: 'ENABLED',
  skillCount: 3,
  mcpCount: 2,
  toolCount: 9,
  createdAt: '2026-09-01T09:00:00Z',
  updatedAt: '2026-09-03T09:00:00Z',
}

async function mountView(permissions: string[]) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspaceStore = useWorkspaceStore()
  workspaceStore.setCurrentSpace(7)
  workspaceStore.setEffectivePermissions({
    spaceId: 7,
    platformSuperAdmin: false,
    role: null,
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/spaces/:spaceId/agents', component: AgentManagementView }],
  })
  await router.push('/spaces/7/agents')
  await router.isReady()
  const wrapper = mount(AgentManagementView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.mocked(agentApi.searchAgents).mockResolvedValue({
    records: [agent],
    total: 6,
    pageNum: 1,
    pageSize: 9,
  })
  vi.mocked(agentApi.getAgentOverviewStats).mockResolvedValue({
    activeAgentCount: 5,
    activeSkillCount: 8,
    enabledMcpCount: 4,
  })
  vi.mocked(agentApi.listModels).mockResolvedValue([
    {
      id: 3,
      provider: 'OPENAI',
      adapterType: 'OPENAI',
      modelKey: 'gpt-5.2',
      displayName: 'GPT-5.2',
      contextWindow: 128000,
      maxOutputTokens: 16000,
      status: 'ENABLED',
      description: null,
    },
  ])
})

describe('AgentManagementView', () => {
  it('renders card summaries returned by the paged API', async () => {
    const wrapper = await mountView([
      SPACE_PERMISSIONS.AGENT_READ,
      SPACE_PERMISSIONS.AGENT_MANAGE,
      SPACE_PERMISSIONS.AGENT_BIND_SKILL,
      SPACE_PERMISSIONS.AGENT_BIND_MCP,
    ])

    expect(wrapper.text()).toContain('文档审计 Agent')
    expect(wrapper.text()).toContain('GPT-5.2')
    expect(wrapper.text()).toContain('ROUTER')
    expect(wrapper.text()).toContain('已启用 5')
    expect(wrapper.text()).toContain('50K Token')
    expect(wrapper.text()).toContain('新建 Agent')
    expect(wrapper.find('[aria-label="Agent 操作"]').exists()).toBe(true)
  })

  it('keeps the page read-only without independent mutation permissions', async () => {
    const wrapper = await mountView([SPACE_PERMISSIONS.AGENT_READ])

    expect(wrapper.text()).not.toContain('新建 Agent')
    expect(wrapper.find('[aria-label="Agent 操作"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('查看')
  })
})
