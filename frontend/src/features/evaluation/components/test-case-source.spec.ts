import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import TestCaseSourceSummary from './TestCaseSourceSummary.vue'
import * as taskApi from '@/features/task/api/task-api'
import * as api from '../api/evaluation-api'
import type { TestCaseSource, TestCaseVersion } from '../types'

vi.mock('@/features/task/api/task-api')
vi.mock('../api/evaluation-api')
const version = {
  id: '31',
  spaceId: '7',
  sourceTaskId: '100',
  sourceInputHash: 'input',
  documentVersionSnapshot: 4,
} as TestCaseVersion
const metadata: TestCaseSource = {
  testCaseVersionId: '31',
  spaceId: '7',
  sourceTaskId: '100',
  sourceInputHash: 'input',
  documentId: '4',
  documentTitle: '部署说明',
}
const wrappers: VueWrapper[] = []
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(taskApi.getTaskNames).mockResolvedValue([
    { id: '100', spaceId: '7', name: '检查部署说明' },
  ])
  vi.mocked(api.getTestCaseSource).mockResolvedValue(metadata)
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
async function render(canReadTask = true, canReadDocument = true) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/:path(.*)', component: { template: '<div />' } }],
  })
  await router.push('/')
  const wrapper = mount(TestCaseSourceSummary, {
    props: { version, spaceId: '7', canReadTask, canReadDocument },
    global: { plugins: [router] },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return wrapper
}
it('shows current names with frozen version and makes only bounded metadata reads', async () => {
  const before = JSON.stringify(version)
  const wrapper = await render()
  expect(wrapper.text()).toContain('检查部署说明')
  expect(wrapper.text()).toContain('部署说明')
  expect(wrapper.text()).toContain('v4')
  expect(wrapper.text()).toContain('名称显示当前取值')
  expect(taskApi.getTaskNames).toHaveBeenCalledWith(
    { spaceId: '7', taskIds: ['100'] },
    expect.any(AbortSignal),
  )
  expect(api.getTestCaseSource).toHaveBeenCalledWith('31', expect.any(AbortSignal))
  expect(taskApi.getTask).not.toHaveBeenCalled()
  expect(JSON.stringify(version)).toBe(before)
})
it.each([
  { spaceId: '8' },
  { testCaseVersionId: '32' },
  { sourceTaskId: '101' },
  { sourceInputHash: 'other' },
])('rejects mismatched source metadata %j', async (change) => {
  vi.mocked(api.getTestCaseSource).mockResolvedValue({ ...metadata, ...change })
  const wrapper = await render()
  expect(wrapper.findAll('dd')[1]!.text()).not.toContain('部署说明')
  expect(wrapper.text()).not.toContain('当前文档名称仅辅助定位')
  expect(wrapper.text()).toContain('核验失败')
  expect(wrapper.text()).toContain('v4')
})
it('keeps frozen evidence on failure and supports retry without editing', async () => {
  vi.mocked(api.getTestCaseSource).mockRejectedValueOnce(new Error('offline'))
  vi.mocked(taskApi.getTaskNames).mockRejectedValueOnce(new Error('offline'))
  const wrapper = await render()
  expect(wrapper.text()).toContain('任务 #100')
  expect(wrapper.text()).toContain('v4')
  await wrapper.get('button').trigger('click')
  await flushPromises()
  expect(wrapper.text()).toContain('当前文档名称仅辅助定位')
  expect(wrapper.find('button').exists()).toBe(false)
  expect(api.updateTestCaseVersion).not.toHaveBeenCalled()
})
it('does not query source documents without document permission', async () => {
  const wrapper = await render(true, false)
  expect(wrapper.text()).toContain('检查部署说明')
  expect(api.getTestCaseSource).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('读取权限')
})
it('makes no metadata requests without task permission and keeps document version zero', async () => {
  const wrapper = await render(false)
  await wrapper.setProps({ version: { ...version, documentVersionSnapshot: 0 } })
  expect(taskApi.getTaskNames).not.toHaveBeenCalled()
  expect(api.getTestCaseSource).not.toHaveBeenCalled()
  expect(wrapper.text()).toContain('v0')
  expect(wrapper.find('a').exists()).toBe(false)
})
it('clears the source document immediately when document permission is revoked', async () => {
  const wrapper = await render()
  const signal = vi.mocked(api.getTestCaseSource).mock.calls[0]![1]!
  await wrapper.setProps({ canReadDocument: false })
  await flushPromises()
  expect(signal.aborted).toBe(true)
  expect(wrapper.findAll('dd')[1]!.text()).not.toContain('部署说明')
  expect(api.getTestCaseSource).toHaveBeenCalledTimes(1)
  expect(wrapper.text()).toContain('检查部署说明')
})
it('clears names and aborts requests when task permission is revoked', async () => {
  const wrapper = await render()
  const signal = vi.mocked(api.getTestCaseSource).mock.calls[0]![1]!
  await wrapper.setProps({ canReadTask: false })
  expect(signal.aborted).toBe(true)
  expect(wrapper.text()).not.toContain('检查部署说明')
  expect(wrapper.text()).not.toContain('当前文档名称仅辅助定位')
  expect(wrapper.find('a').exists()).toBe(false)
  expect(api.getTestCaseSource).toHaveBeenCalledTimes(1)
})
it('ignores a late source response after switching versions', async () => {
  let resolve!: (value: TestCaseSource) => void
  vi.mocked(api.getTestCaseSource).mockReturnValueOnce(
    new Promise((done) => {
      resolve = done
    }),
  )
  const wrapper = await render()
  const signal = vi.mocked(api.getTestCaseSource).mock.calls[0]![1]!
  vi.mocked(api.getTestCaseSource).mockResolvedValue({
    ...metadata,
    testCaseVersionId: '32',
    documentTitle: '新来源文档',
  })
  await wrapper.setProps({ version: { ...version, id: '32' } })
  await flushPromises()
  resolve({ ...metadata, documentTitle: '旧来源文档' })
  await flushPromises()
  expect(signal.aborted).toBe(true)
  expect(wrapper.text()).toContain('新来源文档')
  expect(wrapper.text()).not.toContain('旧来源文档')
})
