import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import McpManagementView from './McpManagementView.vue'

import * as mcpApi from '@/features/mcp/api/mcp-api'
import type { McpServer } from '@/features/mcp/types'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/features/mcp/api/mcp-api', () => ({
  createMcpServer: vi.fn(),
  deleteMcpServer: vi.fn(),
  getMcpServer: vi.fn(),
  listMcpTools: vi.fn().mockResolvedValue([]),
  searchMcpServers: vi.fn(),
  testMcpConnection: vi.fn(),
  updateMcpServer: vi.fn(),
}))

const server: McpServer = {
  id: 31,
  spaceId: 7,
  serverKey: 'github',
  displayName: 'GitHub 工具',
  endpointUrl: 'https://mcp.example.com/github',
  authType: 'BEARER',
  authParamName: null,
  authConfigured: true,
  configVersion: 4,
  status: 1,
  connectionStatus: 'SUCCESS',
  lastTestedAt: '2026-09-02T10:30:00Z',
  lastTestDurationMs: 265,
  lastTestError: null,
  discoveredToolCount: 3,
  toolsDiscoveredAt: '2026-09-02T10:30:00Z',
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
    routes: [{ path: '/spaces/:spaceId/mcp-servers', component: McpManagementView }],
  })
  await router.push('/spaces/7/mcp-servers')
  await router.isReady()
  const wrapper = mount(McpManagementView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.mocked(mcpApi.searchMcpServers).mockResolvedValue({
    records: [server],
    total: 1,
    pageNum: 1,
    pageSize: 12,
  })
  vi.mocked(mcpApi.getMcpServer).mockResolvedValue(server)
})

describe('McpManagementView', () => {
  it('renders real connection and discovery summaries', async () => {
    const wrapper = await mountView([SPACE_PERMISSIONS.MCP_READ, SPACE_PERMISSIONS.MCP_MANAGE])

    expect(wrapper.text()).toContain('GitHub 工具')
    expect(wrapper.text()).toContain('github')
    expect(wrapper.text()).toContain('连接正常')
    expect(wrapper.text()).toContain('凭证已配置')
    expect(wrapper.text()).toContain('3 工具')
    expect(wrapper.text()).toContain('265 ms')
    expect(wrapper.text()).toContain('添加 MCP 服务')
  })

  it('hides mutation actions from read-only members', async () => {
    const wrapper = await mountView([SPACE_PERMISSIONS.MCP_READ])

    expect(wrapper.text()).not.toContain('添加 MCP 服务')
    expect(wrapper.text()).not.toContain('测试连接')
    expect(wrapper.find('[aria-label="MCP 服务操作"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('查看')
  })
})
