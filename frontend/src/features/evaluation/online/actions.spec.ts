import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OnlineExperimentActions from './OnlineExperimentActions.vue'
import * as api from './api'
import { experiment, proof } from './fixtures.spec-support'
vi.mock('./api')
const wrappers: VueWrapper[] = []
const dialog = {
  props: ['modelValue'],
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
}
const button = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((value) => value.text() === text)!
function create(status = 'CREATED', allowed = true, canTerminate = true) {
  const wrapper = mount(OnlineExperimentActions, {
    props: { experiment: { ...experiment, status }, preflight: proof, allowed, canTerminate },
    global: { stubs: { ElDialog: dialog } },
  })
  wrappers.push(wrapper)
  return wrapper
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.useFakeTimers()
  vi.setSystemTime(proof.checkedAt)
})
afterEach(() => {
  wrappers.splice(0).forEach((value) => value.unmount())
  vi.useRealTimers()
})
async function acknowledge(wrapper: VueWrapper) {
  for (const input of wrapper.findAll('input[type="checkbox"]')) await input.setValue(true)
}
describe('线上动作确认', () => {
  it('缺少动作权限或取消权限时不能提交', async () => {
    const wrapper = create('ACTIVE', false)
    expect(
      wrapper.findAll('button').every((value) => value.attributes('disabled') !== undefined),
    ).toBe(true)
    await wrapper.setProps({ allowed: true, canTerminate: false })
    expect(button(wrapper, '紧急熔断').attributes('disabled')).toBeDefined()
    expect(api.changeOnlineState).not.toHaveBeenCalled()
  })
  it('四项确认后发送冻结 proof 和长状态版本', async () => {
    vi.mocked(api.changeOnlineState).mockResolvedValue({
      experimentId: experiment.id,
      status: 'ACTIVE',
      stateVersion: '9007199254740994',
      manifestHash: experiment.manifestHash!,
      reasonCode: null,
    })
    const wrapper = create()
    await button(wrapper, '启动实验').trigger('click')
    expect(button(wrapper, '确认操作').attributes('disabled')).toBeDefined()
    await acknowledge(wrapper)
    await button(wrapper, '确认操作').trigger('click')
    await flushPromises()
    expect(api.changeOnlineState).toHaveBeenCalledWith(
      experiment.id,
      'start',
      expect.objectContaining({
        expectedStateVersion: experiment.stateVersion,
        preflightProofHash: proof.proofHash,
        liveSideEffectsAcknowledged: true,
        retentionAcknowledged: true,
        sharedResourcesAcknowledged: true,
        emergencyCancellationAcknowledged: true,
      }),
      expect.any(AbortSignal),
    )
    expect(wrapper.emitted('changed')).toHaveLength(1)
  })
  it.each(['expiry', 'proof', 'state', 'permission'])('确认在 %s 改变后失效', async (cause) => {
    const wrapper = create()
    await button(wrapper, '启动实验').trigger('click')
    await acknowledge(wrapper)
    if (cause === 'expiry') await vi.advanceTimersByTimeAsync(600001)
    if (cause === 'proof')
      await wrapper.setProps({ preflight: { ...proof, proofHash: 'c'.repeat(64) } })
    if (cause === 'state')
      await wrapper.setProps({ experiment: { ...experiment, stateVersion: '9007199254740994' } })
    if (cause === 'permission') await wrapper.setProps({ allowed: false })
    const confirm = button(wrapper, '确认操作')
    if (confirm) expect(confirm.attributes('disabled')).toBeDefined()
    expect(api.changeOnlineState).not.toHaveBeenCalled()
  })
  it('响应丢失重试复用 key，改变原因使用新 key', async () => {
    vi.mocked(api.changeOnlineState).mockRejectedValue(new Error('响应丢失'))
    const wrapper = create('PAUSED')
    await button(wrapper, '结束实验').trigger('click')
    await wrapper.get('textarea').setValue('结束观察')
    await button(wrapper, '确认操作').trigger('click')
    await flushPromises()
    await button(wrapper, '确认操作').trigger('click')
    await flushPromises()
    const first = vi.mocked(api.changeOnlineState).mock.calls[0]![2].clientRequestKey
    expect(vi.mocked(api.changeOnlineState).mock.calls[1]![2].clientRequestKey).toBe(first)
    await wrapper.get('textarea').setValue('人工停止')
    await button(wrapper, '确认操作').trigger('click')
    await flushPromises()
    expect(vi.mocked(api.changeOnlineState).mock.calls[2]![2].clientRequestKey).not.toBe(first)
  })
  it('STOPPING 显示逐项未决并允许独立熔断入口', () => {
    const wrapper = create('STOPPING')
    expect(wrapper.text()).toContain('取消、执行槽及预算须分别等待权威确认')
    expect(button(wrapper, '结束实验')).toBeUndefined()
    expect(button(wrapper, '紧急熔断').attributes('disabled')).toBeUndefined()
  })
})
