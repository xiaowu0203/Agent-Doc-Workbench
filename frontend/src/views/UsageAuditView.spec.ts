import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import UsageAuditView from './UsageAuditView.vue'
import * as usageApi from '@/features/usage/api/usage-api'
import * as taskApi from '@/features/task/api/task-api'
import * as agentApi from '@/features/agent/api/agent-api'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS as P } from '@/shared/constants/permissions'
import type { UsageDashboard } from '@/features/usage/types'

vi.mock('@/features/usage/api/usage-api')
vi.mock('@/features/task/api/task-api')
vi.mock('@/features/agent/api/agent-api')
const wrappers: VueWrapper[] = []
const summary = {
  hasData: true,
  inputTokens: 10,
  outputTokens: 20,
  tokens: 30,
  estimatedCost: 0,
  executedTasks: 1,
  toolCalls: 30,
  inputTokensEstimated: false,
  outputTokensEstimated: false,
  hasIncompleteData: false,
}
const dashboard: UsageDashboard = {
  startDate: '2026-10-01',
  endDate: '2026-10-02',
  calculatedAt: '2026-10-02T00:00:00',
  timeZone: 'Asia/Shanghai',
  summary,
  previousSummary: summary,
  trend: [],
  toolSources: [],
  monthlyUsedTokens: 30,
  monthlyTokenBudget: 100,
}
beforeEach(() => {
  vi.resetAllMocks()
  setActivePinia(createPinia())
  const workspace = useWorkspaceStore()
  workspace.setCurrentSpace('7')
  workspace.setEffectivePermissions({
    spaceId: '7',
    role: null,
    platformSuperAdmin: false,
    permissions: [P.USAGE_READ, P.TASK_READ],
  })
  vi.mocked(agentApi.listAgents).mockResolvedValue([])
  vi.mocked(agentApi.listModels).mockResolvedValue([])
  vi.mocked(usageApi.queryUsageDashboard).mockResolvedValue(dashboard)
  vi.mocked(taskApi.searchTasks).mockResolvedValue({
    records: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
  })
  vi.mocked(usageApi.searchUsageToolCalls).mockResolvedValue({
    records: [
      {
        taskId: '9007199254740993',
        task: null,
        toolCall: { toolName: 'document_read', toolSource: 'WORKBENCH_MCP' },
      },
    ],
    total: 30,
    pageNum: 1,
    pageSize: 10,
  } as Awaited<ReturnType<typeof usageApi.searchUsageToolCalls>>)
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
async function create() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div />' } }],
  })
  await router.push('/')
  const wrapper = mount(UsageAuditView, {
    global: {
      plugins: [router],
      stubs: {
        EChartCanvas: true,
        ElDrawer: true,
        ElOption: true,
        ElSelect: { name: 'ElSelect', props: ['modelValue'], template: '<div><slot /></div>' },
      },
    },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
describe('用量全局筛选与独立工具分页', () => {
  it.each([
    [0, '¥ 0.00'],
    [0.00003, '¥ 0.00003'],
    [0.00000001, '¥ <0.000001'],
  ])('成本 %s 保留零与非零小额差异', async (cost, expected) => {
    vi.mocked(usageApi.queryUsageDashboard).mockResolvedValue({
      ...dashboard,
      summary: { ...summary, estimatedCost: cost as number },
    })
    const wrapper = await create()
    expect(wrapper.text()).toContain(expected)
  })
  it('执行页为空时仍独立读取全部工具调用，保留长任务身份并支持分页', async () => {
    const wrapper = await create()
    wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'tools')
    await flushPromises()
    expect(usageApi.searchUsageToolCalls).toHaveBeenCalledWith(
      expect.objectContaining({ spaceId: '7', pageNum: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).toContain('任务 #9007199254740993')
    const pagination = wrapper
      .findAllComponents({ name: 'ElPagination' })
      .find((item) => item.props('total') === 30)!
    pagination.vm.$emit('current-change', 2)
    await flushPromises()
    expect(usageApi.searchUsageToolCalls).toHaveBeenLastCalledWith(
      expect.objectContaining({ pageNum: 2 }),
      expect.any(AbortSignal),
    )
    expect(taskApi.getTaskExecutionDetail).not.toHaveBeenCalled()
  })
  it('false 参与过滤与模式同时进入汇总、执行列表和工具查询，未应用的编辑不改变分页范围', async () => {
    const wrapper = await create()
    const select = (label: string) =>
      wrapper
        .findAllComponents({ name: 'ElSelect' })
        .find((item) => item.attributes('aria-label') === label)!
    select('用量执行模式').vm.$emit('update:modelValue', 'ISOLATED')
    select('用量线上实验参与').vm.$emit('update:modelValue', false)
    await wrapper
      .findAll('button')
      .find((item) => item.text() === '查询')!
      .trigger('click')
    await flushPromises()
    expect(usageApi.queryUsageDashboard).toHaveBeenLastCalledWith(
      expect.objectContaining({ executionMode: 'ISOLATED', onlineParticipation: false }),
      expect.any(AbortSignal),
    )
    expect(taskApi.searchTasks).toHaveBeenLastCalledWith(
      expect.objectContaining({ executionMode: 'ISOLATED', onlineParticipation: false }),
      expect.any(AbortSignal),
    )
    select('用量执行模式').vm.$emit('update:modelValue', 'LIVE')
    wrapper.findComponent({ name: 'ElTabs' }).vm.$emit('update:modelValue', 'tools')
    await flushPromises()
    expect(usageApi.searchUsageToolCalls).toHaveBeenLastCalledWith(
      expect.objectContaining({ executionMode: 'ISOLATED', onlineParticipation: false }),
      expect.any(AbortSignal),
    )
  })
})
