import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, expect, it, vi } from 'vitest'
import OnlineParticipationNotice from './OnlineParticipationNotice.vue'
import { searchOnlineExperiments } from './api'
import { experiment } from './fixtures.spec-support'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'
import type { OnlinePage, OnlineSummary } from './types'

vi.mock('./api', () => ({ searchOnlineExperiments: vi.fn() }))
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(searchOnlineExperiments).mockResolvedValue({
    records: [],
    total: 0,
    pageNum: 1,
    pageSize: 1,
  })
})
async function setup(creation = false, read = true) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspace = useWorkspaceStore()
  workspace.setCurrentSpace('7')
  workspace.setEffectivePermissions({
    spaceId: '7',
    platformSuperAdmin: false,
    role: null,
    permissions: read
      ? [SPACE_PERMISSIONS.SPACE_READ, SPACE_PERMISSIONS.EVALUATION_READ]
      : [SPACE_PERMISSIONS.SPACE_READ],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      {
        path: '/spaces/:spaceId/evaluation/online-experiments/:id',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push('/')
  const wrapper = mount(OnlineParticipationNotice, {
    props: { spaceId: '7', creation },
    global: { plugins: [pinia, router] },
  })
  await flushPromises()
  return { wrapper, workspace }
}
function occupying(status: string) {
  vi.mocked(searchOnlineExperiments).mockImplementation(async (request) => ({
    records: request.status === status ? [{ ...experiment, status }] : [],
    total: request.status === status ? 1 : 0,
    pageNum: 1,
    pageSize: 1,
  }))
}

it('discloses real effects without reading experiment data beyond the current permissions', async () => {
  const { wrapper } = await setup(true, false)
  expect(searchOnlineExperiments).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('草稿可能发生真实修改')
  expect(wrapper.text()).toContain('实验、任务及空间预算')
  expect(wrapper.text()).toContain('无法读取实验范围与占位状态')
  expect(wrapper.find('a').exists()).toBe(false)
  wrapper.unmount()
})

it('queries all occupying states with bounded calls and leaves final participation to the server', async () => {
  occupying('ACTIVE')
  const { wrapper } = await setup(true)
  expect(vi.mocked(searchOnlineExperiments).mock.calls.map(([query]) => query)).toEqual(
    ['ACTIVE', 'PAUSED', 'STOPPING'].map((status) => ({
      spaceId: '7',
      status,
      pageNum: 1,
      pageSize: 1,
    })),
  )
  expect(wrapper.text()).toContain('最终归属以服务端任务绑定为准')
  expect(wrapper.get('a').attributes('href')).toContain(experiment.id)
  wrapper.unmount()
})

it.each(['PAUSED', 'STOPPING'])(
  'keeps %s occupancy distinct from production fallback and remote completion',
  async (status) => {
    occupying(status)
    const { wrapper } = await setup()
    expect(wrapper.text()).toContain('不能启动另一个实验')
    expect(wrapper.text()).toContain('与本实验冻结 baseline 可能不同')
    expect(wrapper.text()).toContain('已绑定任务的重试继续使用原身份')
    if (status === 'STOPPING') expect(wrapper.text()).toContain('不等于远端调用已结束')
    wrapper.unmount()
  },
)

it('reports failed reads as unknown and clears old metadata on permission loss', async () => {
  occupying('ACTIVE')
  const { wrapper, workspace } = await setup()
  expect(wrapper.text()).toContain(experiment.name)
  vi.mocked(searchOnlineExperiments).mockRejectedValue(new Error('服务不可达'))
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('占位状态未知')
  expect(wrapper.text()).not.toContain('未发现占位实验')
  expect(wrapper.find('a').exists()).toBe(false)
  workspace.invalidatePermissions('7')
  await flushPromises()
  expect(wrapper.text()).toBe('')
  wrapper.unmount()
})

it('rejects another space and an inconsistent snapshot without leaking experiment links', async () => {
  vi.mocked(searchOnlineExperiments).mockImplementation(async (query) => ({
    records: query.status === 'ACTIVE' ? [{ ...experiment, status: 'ACTIVE', spaceId: '8' }] : [],
    total: query.status === 'ACTIVE' ? 1 : 0,
    pageNum: 1,
    pageSize: 1,
  }))
  const { wrapper } = await setup()
  expect(wrapper.text()).toContain('身份或数量不一致')
  expect(wrapper.find('a').exists()).toBe(false)
  wrapper.unmount()
})

it('aborts all stale reads and ignores results after the current space changes', async () => {
  const resolvers: ((page: OnlinePage<OnlineSummary>) => void)[] = []
  vi.mocked(searchOnlineExperiments).mockImplementation(
    () =>
      new Promise((resolve) => {
        resolvers.push(resolve)
      }),
  )
  const { wrapper, workspace } = await setup()
  workspace.setCurrentSpace('8')
  await flushPromises()
  expect(vi.mocked(searchOnlineExperiments).mock.calls.every(([, signal]) => signal!.aborted)).toBe(
    true,
  )
  resolvers.forEach((resolve) =>
    resolve({ records: [{ ...experiment, status: 'ACTIVE' }], total: 1, pageNum: 1, pageSize: 1 }),
  )
  await flushPromises()
  expect(wrapper.find('a').exists()).toBe(false)
  wrapper.unmount()
})
