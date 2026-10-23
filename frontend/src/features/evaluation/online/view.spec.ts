import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OnlineExperimentView from '@/views/OnlineExperimentView.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS as P } from '@/shared/constants/permissions'
import { ApiError } from '@/api/errors'
import * as api from './api'
import { getTaskNames } from '@/features/task/api/task-api'
import { experiment } from './fixtures.spec-support'
vi.mock('./api')
vi.mock('@/features/task/api/task-api')
const wrappers: VueWrapper[] = []
const page = <T>(records: T[]) => ({ records, total: records.length, pageNum: 1, pageSize: 10 })
function permissions(values: string[], owner = false, platformSuperAdmin = false) {
  useWorkspaceStore().setEffectivePermissions({
    spaceId: '7',
    platformSuperAdmin,
    role: owner ? { roleId: '1', roleKey: 'OWNER', displayName: '所有者' } : null,
    permissions: values,
  })
}
async function create(id: string | undefined = experiment.id) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/spaces/:spaceId/evaluation/online-experiments/:experimentId/review',
        component: { template: '<div />' },
      },
      {
        path: '/spaces/:spaceId/evaluation/online-experiments/:experimentId?',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push(`/spaces/7/evaluation/online-experiments/${id || ''}`)
  const wrapper = mount(OnlineExperimentView, {
    props: { resourceId: id },
    global: { plugins: [router], stubs: { OnlineExperimentCreate: true } },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers()
  Object.defineProperty(document, 'hidden', { configurable: true, value: false })
  setActivePinia(createPinia())
  useWorkspaceStore().setCurrentSpace('7')
  permissions([P.SPACE_READ, P.EVALUATION_READ])
  vi.mocked(api.getOnlineExperiment).mockResolvedValue({ ...experiment, status: 'ACTIVE' })
  vi.mocked(api.searchOnlineAssignments).mockResolvedValue(page([]))
  vi.mocked(api.searchOnlineExperiments).mockResolvedValue(page([experiment]))
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
})
describe('线上观察权限与轮询', () => {
  it('分配读取失败不显示为空，重新读取成功后才显示已核验空态', async () => {
    vi.mocked(api.searchOnlineAssignments).mockRejectedValueOnce(new Error('分配读取失败'))
    const { wrapper } = await create()
    expect(wrapper.text()).toContain('分配读取失败')
    expect(wrapper.text()).not.toContain('暂无已接受分配')
    expect(wrapper.text()).toContain('不能判断是否为空')
    await wrapper
      .findAll('button')
      .find((value) => value.text() === '刷新')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无已接受分配')
    expect(wrapper.text()).not.toContain('分配读取失败')
  })
  it('停止后报告在事实之前，运行时仍先显示事实', async () => {
    vi.mocked(api.getOnlineExperiment).mockResolvedValue({ ...experiment, status: 'STOPPED' })
    const { wrapper } = await create()
    const sections = wrapper.findAll('#online-report, #online-facts, #online-assignments')
    expect(sections.map((section) => section.attributes('id'))).toEqual([
      'online-report',
      'online-facts',
      'online-assignments',
    ])
    expect(wrapper.get('#online-facts').text()).toContain('浏览器最近成功读取于')
  })
  it('本页任务名批量读取，撤销任务权限清除名称并忽略迟到响应', async () => {
    permissions([P.SPACE_READ, P.EVALUATION_READ, P.TASK_READ])
    vi.mocked(api.searchOnlineAssignments).mockResolvedValue(
      page([
        {
          id: '30',
          spaceId: '7',
          experimentId: experiment.id,
          taskId: '40',
          documentId: '50',
          variant: 'BASELINE',
          taskConfirmationStatus: 'CONFIRMED',
          dispatchStatus: 'STARTED',
          slotStatus: 'RELEASED',
          cancelStatus: 'NONE',
          settlementStatus: 'SETTLED',
          factStatus: 'COMPLETED',
        },
      ]) as Awaited<ReturnType<typeof api.searchOnlineAssignments>>,
    )
    vi.mocked(getTaskNames).mockResolvedValue([{ id: '40', spaceId: '7', name: '线上交付任务' }])
    const { wrapper } = await create()
    expect(getTaskNames).toHaveBeenCalledExactlyOnceWith(
      { spaceId: '7', taskIds: ['40'] },
      expect.any(AbortSignal),
    )
    expect(wrapper.get('#online-assignments').text()).toContain('线上交付任务')
    expect(wrapper.get('#online-assignments').text()).toContain('执行槽已释放')
    let resolve!: (value: Awaited<ReturnType<typeof getTaskNames>>) => void
    vi.mocked(getTaskNames).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    await vi.advanceTimersByTimeAsync(5000)
    await flushPromises()
    permissions([P.SPACE_READ, P.EVALUATION_READ])
    resolve([{ id: '40', spaceId: '7', name: '迟到的任务名' }])
    await flushPromises()
    expect(wrapper.text()).not.toContain('线上交付任务')
    expect(wrapper.text()).not.toContain('迟到的任务名')
    expect(wrapper.text()).toContain('Task #40')
  })
  it('没有任务权限时不请求名称，名称越界响应不展示', async () => {
    vi.mocked(api.searchOnlineAssignments).mockResolvedValue(
      page([
        {
          id: '30',
          spaceId: '7',
          experimentId: experiment.id,
          taskId: '40',
          documentId: '50',
          variant: 'BASELINE',
        },
      ]) as Awaited<ReturnType<typeof api.searchOnlineAssignments>>,
    )
    const { wrapper } = await create()
    expect(getTaskNames).not.toHaveBeenCalled()
    vi.mocked(getTaskNames).mockResolvedValue([{ id: '40', spaceId: '9', name: '越界任务名' }])
    permissions([P.SPACE_READ, P.EVALUATION_READ, P.TASK_READ])
    await flushPromises()
    expect(wrapper.text()).not.toContain('越界任务名')
    expect(wrapper.text()).toContain('任务名称身份或空间不一致')
  })
  it.each([
    { values: [P.EVALUATION_READ] },
    { values: [P.SPACE_READ] },
    { values: [P.EVALUATION_MANAGE] },
  ])('不从不完整权限加载事实 $values', async ({ values }) => {
    permissions(values)
    const { wrapper } = await create()
    expect(wrapper.text()).toContain('缺少实验读取权限')
    expect(api.getOnlineExperiment).not.toHaveBeenCalled()
  })
  it('平台管理员没有当前 OWNER 身份时隐藏所有管理与预检', async () => {
    permissions(Object.values(P), false, true)
    const { wrapper } = await create()
    expect(wrapper.find('[aria-label="线上实验操作"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="启动前预检"]').exists()).toBe(false)
    expect(api.getOnlinePreflight).not.toHaveBeenCalled()
  })
  it('保留长整数、零与未知，并区分文档数和 Task 数', async () => {
    vi.mocked(api.getOnlineExperiment).mockResolvedValue({
      ...experiment,
      status: 'STOPPED',
      assignedTaskCount: 6,
      participatingDocumentCount: 2,
    })
    const { wrapper } = await create()
    expect(wrapper.text()).toContain('参与 2 文档 / 6 Task')
    expect(wrapper.text()).toContain('授权 9007199254740993 / 实际 未知 / 未结算预留 0')
    expect(api.getOnlineExperiment).toHaveBeenCalledWith(experiment.id, expect.any(AbortSignal))
  })
  it('跨空间响应与不安全状态版本均清除正文', async () => {
    vi.mocked(api.getOnlineExperiment).mockResolvedValue({ ...experiment, spaceId: '9' })
    const { wrapper } = await create()
    expect(wrapper.text()).toContain('实验身份或空间不一致')
    expect(wrapper.text()).not.toContain(experiment.name)
    vi.mocked(api.getOnlineExperiment).mockResolvedValue({
      ...experiment,
      stateVersion: Number('9007199254740993'),
    })
    await wrapper
      .findAll('button')
      .find((value) => value.text() === '刷新')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('状态版本不可安全读取')
    expect(api.searchOnlineAssignments).not.toHaveBeenCalled()
  })
  it.each(['ACTIVE', 'PAUSED', 'STOPPING'])(
    '%s 单管线轮询、后台暂停、恢复及离页中止',
    async (status) => {
      vi.mocked(api.getOnlineExperiment).mockResolvedValue({ ...experiment, status })
      const { wrapper } = await create()
      await vi.advanceTimersByTimeAsync(5000)
      await flushPromises()
      expect(api.getOnlineExperiment).toHaveBeenCalledTimes(2)
      Object.defineProperty(document, 'hidden', { configurable: true, value: true })
      document.dispatchEvent(new Event('visibilitychange'))
      await vi.advanceTimersByTimeAsync(60000)
      expect(api.getOnlineExperiment).toHaveBeenCalledTimes(2)
      Object.defineProperty(document, 'hidden', { configurable: true, value: false })
      document.dispatchEvent(new Event('visibilitychange'))
      await flushPromises()
      expect(api.getOnlineExperiment).toHaveBeenCalledTimes(3)
      const signal = vi.mocked(api.getOnlineExperiment).mock.calls[2]![1]!
      wrapper.unmount()
      expect(signal.aborted).toBe(true)
      await vi.advanceTimersByTimeAsync(60000)
      expect(api.getOnlineExperiment).toHaveBeenCalledTimes(3)
    },
  )
  it('网络失败退避 10/20/30 秒，权限拒绝清除旧结果', async () => {
    const { wrapper } = await create()
    vi.mocked(api.getOnlineExperiment).mockRejectedValue(new Error('暂时断网'))
    await vi.advanceTimersByTimeAsync(5000)
    expect(wrapper.text()).toContain('保留上次结果')
    await vi.advanceTimersByTimeAsync(9999)
    expect(api.getOnlineExperiment).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(1)
    expect(api.getOnlineExperiment).toHaveBeenCalledTimes(3)
    await vi.advanceTimersByTimeAsync(20000)
    expect(api.getOnlineExperiment).toHaveBeenCalledTimes(4)
    vi.mocked(api.getOnlineExperiment).mockRejectedValue(new ApiError('权限撤销', { status: 403 }))
    await vi.advanceTimersByTimeAsync(30000)
    expect(api.getOnlineExperiment).toHaveBeenCalledTimes(5)
    expect(wrapper.text()).not.toContain(experiment.name)
    expect(wrapper.text()).toContain('权限撤销')
    await vi.advanceTimersByTimeAsync(60000)
    expect(api.getOnlineExperiment).toHaveBeenCalledTimes(5)
  })
  it('撤销读取权限立即清除事实并中止请求', async () => {
    const { wrapper } = await create()
    const signal = vi.mocked(api.getOnlineExperiment).mock.calls[0]![1]!
    permissions([])
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.text()).not.toContain(experiment.name)
  })
})
