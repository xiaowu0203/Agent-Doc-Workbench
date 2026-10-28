import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import EvaluationSourceTitle from './EvaluationSourceTitle.vue'
import * as api from '../api/evaluation-api'
import type { DatasetVersion, EvaluationDataset } from '../types'
vi.mock('../api/evaluation-api')
const wrappers: VueWrapper[] = []
const version = { id: '10', spaceId: '7', datasetId: '20', versionNo: 3 } as DatasetVersion
const parent = { id: '20', spaceId: '7', name: '回归数据集' } as EvaluationDataset
function create() {
  const wrapper = mount(EvaluationSourceTitle, {
    props: { spaceId: '7', kind: 'datasets', versionId: '10', fallback: '运行 #30' },
  })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.getDatasetVersion).mockResolvedValue(version)
  vi.mocked(api.getDataset).mockResolvedValue(parent)
})
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
describe('来源标题只读元数据', () => {
  it('核对空间与版本身份后显示当前目录名，归档版本也能辅助识别历史', async () => {
    const wrapper = create()
    await flushPromises()
    expect(wrapper.text()).toBe('回归数据集 · v3')
    expect(api.getDatasetVersion).toHaveBeenCalledExactlyOnceWith('10', expect.any(AbortSignal))
    expect(api.getDataset).toHaveBeenCalledExactlyOnceWith('20', expect.any(AbortSignal))
  })
  it('跨空间版本不能触发父资源读取', async () => {
    vi.mocked(api.getDatasetVersion).mockResolvedValue({ ...version, spaceId: '9' })
    const wrapper = create()
    await flushPromises()
    expect(wrapper.text()).toBe('运行 #30')
    expect(api.getDataset).not.toHaveBeenCalled()
  })
  it('名称读取失败或父资源不匹配时保留运行身份', async () => {
    vi.mocked(api.getDataset).mockResolvedValue({ ...parent, spaceId: '9' })
    const wrapper = create()
    await flushPromises()
    expect(wrapper.text()).toBe('运行 #30')
    vi.mocked(api.getDatasetVersion).mockRejectedValue(new Error('目录不可用'))
    await wrapper.setProps({ versionId: '11' })
    await flushPromises()
    expect(wrapper.text()).toBe('运行 #30')
  })
  it('切换空间忽略迟到名称，卸载中止读取', async () => {
    let resolve!: (value: EvaluationDataset) => void
    vi.mocked(api.getDataset).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    const wrapper = create()
    await flushPromises()
    await wrapper.setProps({ spaceId: '9' })
    resolve(parent)
    await flushPromises()
    expect(wrapper.text()).toBe('运行 #30')
    wrapper.unmount()
    expect(vi.mocked(api.getDatasetVersion).mock.calls.at(-1)![1]!.aborted).toBe(true)
  })
})
