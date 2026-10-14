import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OnlinePromptComparison from './OnlinePromptComparison.vue'
import * as api from './api'
import { experiment } from './fixtures.spec-support'
import type { OnlineExperiment, OnlinePromptComparison as PromptComparisonResult } from './types'
vi.mock('./api')
const wrappers: VueWrapper[] = []
const hash = 'a'.repeat(64)
const frozen: OnlineExperiment = {
  ...experiment,
  baseline: { id: '51', schemaVersion: 2, hash, nonPromptHash: hash },
  candidate: { id: '52', schemaVersion: 2, hash, nonPromptHash: hash },
  dependencyHash: hash,
}
const response: PromptComparisonResult = {
  experimentId: frozen.id,
  spaceId: '7',
  agentId: '3',
  schemaVersion: 2,
  baseline: { nonPromptHash: hash, hash, schemaVersion: 2, id: '51' },
  candidate: frozen.candidate!,
  dependencyHash: hash,
  baselineSystemPrompt: '冻结完整基线\n历史内容',
  candidateSystemPrompt: '<script>候选完整内容</script>',
}
function create(allowed = true) {
  const wrapper = mount(OnlinePromptComparison, { props: { experiment: frozen, allowed } })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.getOnlinePromptComparison).mockResolvedValue(response)
})
afterEach(() => wrappers.splice(0).forEach((item) => item.unmount()))
describe('完整冻结 Prompt 对照', () => {
  it('明确读取后显示历史全文，模板属性顺序不影响身份核验', async () => {
    const wrapper = create()
    expect(api.getOnlinePromptComparison).not.toHaveBeenCalled()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.get('[aria-label="冻结基线系统提示词"]').element).toHaveProperty(
      'value',
      response.baselineSystemPrompt,
    )
    expect(wrapper.get('[aria-label="冻结候选系统提示词"]').element).toHaveProperty(
      'value',
      response.candidateSystemPrompt,
    )
    expect(wrapper.find('script').exists()).toBe(false)
    expect(api.getOnlinePromptComparison).toHaveBeenCalledWith(frozen.id, expect.any(AbortSignal))
  })
  it.each(['identity', 'schema', 'hash'])('拒绝不可核验的 %s', async (kind) => {
    vi.mocked(api.getOnlinePromptComparison).mockResolvedValue({
      ...response,
      ...(kind === 'identity'
        ? { spaceId: '8' }
        : kind === 'schema'
          ? { schemaVersion: 1 }
          : { baseline: { ...response.baseline, hash: 'b'.repeat(64) } }),
    })
    const wrapper = create()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.get('[role="alert"]').text()).toContain('不一致')
  })
  it('撤销授权清除全文并取消请求，迟到响应不能重新显示', async () => {
    const wrapper = create()
    await wrapper.get('button').trigger('click')
    await flushPromises()
    let resolve!: (value: PromptComparisonResult) => void
    vi.mocked(api.getOnlinePromptComparison).mockImplementation(
      () =>
        new Promise((done) => {
          resolve = done
        }),
    )
    await wrapper.get('button').trigger('click')
    const signal = vi.mocked(api.getOnlinePromptComparison).mock.calls.at(-1)![1]!
    await wrapper.setProps({ allowed: false })
    resolve(response)
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.find('button').exists()).toBe(false)
  })
})
