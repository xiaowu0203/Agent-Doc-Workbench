import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import OnlineReportPanel from './OnlineReportPanel.vue'
import { onlineReportPayload } from './report'
import { experiment } from './fixtures.spec-support'
import * as api from './api'
import type { OnlineReport } from './types'
vi.mock('./api')
const wrappers: VueWrapper[] = []
const frozen: OnlineReport = {
  id: '21',
  experimentId: experiment.id,
  revision: 1,
  reportType: 'ONLINE_V1',
  schemaVersion: 2,
  reportHash: 'c'.repeat(64),
  inputHash: 'd'.repeat(64),
  state: 'SUPPORTED',
  report: {
    domain: 'online.report',
    schemaVersion: 2,
    payload: {
      reportType: 'ONLINE_V1',
      schemaVersion: 2,
      manifestHash: experiment.manifestHash!,
      assignmentCutoffSequence: '9007199254740993',
      observationCutoffAt: '2026-10-12T12:00:00.000',
      groups: {
        BASELINE: {
          participatingDocumentCount: 1,
          assignedTaskCount: 3,
          primaryValidRatio: '0.000000000000',
          primaryConfidenceInterval: { lower: '0', upper: '0.8' },
          qualityByBlindness: { BLINDED: { meanRubricScore: '0.000000000000', documentCount: 1 } },
          firstAssignmentResource: {
            ledgerTokens: '9007199254740993',
            meanCostByCurrency: { CNY: '0.00' },
            queueLatencyMeanMillis: null,
          },
        },
        CANDIDATE: {
          participatingDocumentCount: 1,
          assignedTaskCount: 2,
          primaryValidRatio: null,
          firstAssignmentResource: {},
        },
      },
      approvalFeedback: {
        groups: {
          BASELINE: { status_APPROVED: 1, meanHumanModificationRatio: '0.5' },
          CANDIDATE: {},
        },
        unit: 'LATEST_IMPORTED_CHANGE_REQUEST',
        unrecordedMeaning: 'PENDING_OR_NOT_APPLICABLE_OR_NOT_IMPORTED',
      },
      actualWindows: {},
      analysisPlan: experiment.analysisPlan,
      primaryDifference: {
        candidateMinusBaselineRatio: null,
        mdeAbsoluteRatio: '0.1',
        interpretation: 'DESCRIPTIVE_NOT_CAUSAL',
      },
      srmDiagnostics: {
        range: { status: 'NOT_ENOUGH_UNITS', pValue: null },
        runtime: { status: 'NOT_ENOUGH_UNITS', pValue: null },
      },
      ruleOutcomes: {},
      taskRuleOutcomes: {},
      budgetByCurrency: { CNY: '0.00', USD: '1.25' },
      feedbackStrata: {},
      exceptions: [],
      limitations: ['TRACE_NOT_AUTHORITATIVE'],
      decisionEligibility: {
        comparisonAllowed: false,
        reasonCodes: ['DOCUMENT_SAMPLE_INSUFFICIENT'],
        confirmedSafetyFeedbackIds: ['9007199254740993'],
        safetyRejectedAllowed: true,
      },
    },
  },
}
const button = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((value) => value.text() === text)!
function create(status = 'STOPPED', manageable = true) {
  const wrapper = mount(OnlineReportPanel, {
    props: {
      experiment: { ...experiment, status },
      readable: true,
      manageable,
      canRecalculate: manageable,
    },
    global: {
      stubs: {
        ElDialog: {
          props: ['modelValue'],
          template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
        },
      },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}
async function load(wrapper: VueWrapper, full = true) {
  if (full) await button(wrapper, '核验完整报告').trigger('click')
  else await wrapper.findAll('form')[0]!.trigger('submit')
  await flushPromises()
}
async function submitForm(wrapper: VueWrapper, label: string) {
  await wrapper
    .findAll('form')
    .find((form) => form.findAll('button').some((value) => value.text() === label))!
    .trigger('submit')
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.getOnlineReport).mockImplementation(async (_id, _revision, full) => ({
    ...frozen,
    state: full ? 'SUPPORTED' : 'SUMMARY_VERIFIED',
  }))
})
afterEach(() => wrappers.splice(0).forEach((value) => value.unmount()))
describe('固定线上报告和人工决定', () => {
  it.each(['UNSUPPORTED_SCHEMA', 'HASH_MISMATCH', 'FUTURE'])(
    '未知校验状态 %s 不展示正文',
    (state) => expect(onlineReportPayload({ ...frozen, state }, experiment)).toBeNull(),
  )
  it('旧 schema、错清单和错实验身份不能变成可信报告', () => {
    expect(onlineReportPayload({ ...frozen, schemaVersion: 1 }, experiment)).toBeNull()
    expect(onlineReportPayload({ ...frozen, experimentId: '9' }, experiment)).toBeNull()
    expect(onlineReportPayload(frozen, { ...experiment, manifestHash: 'changed' })).toBeNull()
  })
  it('概要保留零和缺失、独立文档/Task 分母、币种和审批差异，并禁止决定', async () => {
    const wrapper = create()
    await load(wrapper, false)
    expect(wrapper.text()).toContain('0.000000000000')
    expect(wrapper.text()).toContain('未知')
    expect(wrapper.text()).toContain('9007199254740993')
    expect(wrapper.text()).toContain('全部 Task 辅助分析')
    expect(wrapper.text()).toContain('CNY 0.00')
    expect(wrapper.text()).toContain('USD 1.25')
    expect(wrapper.text()).toContain('平均人工修改比例')
    const approvalRatio = wrapper.findAll('tr').find((row) => row.text().includes('可比较提案的平均人工修改比例'))!
    expect(approvalRatio.findAll('td').map((cell) => cell.text())).toEqual(['0.5', '未知'])
    expect(wrapper.text()).toContain('证据不足或存在阻塞')
    expect(button(wrapper, '核对结论').attributes('disabled')).toBeDefined()
    expect(api.getOnlineReport).toHaveBeenCalledWith(
      experiment.id,
      1,
      false,
      expect.any(AbortSignal),
    )
  })
  it('完整核验后证据不足可以记录不足，不能一般接受或拒绝', async () => {
    const wrapper = create()
    await load(wrapper)
    await wrapper.get('[aria-label="线上结论理由"]').setValue('文档数不足')
    expect(button(wrapper, '核对结论').attributes('disabled')).toBeUndefined()
    await wrapper.get('[aria-label="线上人工结论"]').setValue('ACCEPTED')
    expect(button(wrapper, '核对结论').attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="线上人工结论"]').setValue('REJECTED')
    expect(button(wrapper, '核对结论').attributes('disabled')).toBeDefined()
  })
  it('运行中只允许引用已确认安全依据的拒绝，不以安全例外自动放宽普通结论', async () => {
    vi.mocked(api.decideOnlineExperiment).mockResolvedValue(undefined)
    const wrapper = create('ACTIVE')
    await load(wrapper)
    await wrapper.get('[aria-label="线上结论理由"]').setValue('确认严重事件')
    expect(button(wrapper, '核对结论').attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="线上人工结论"]').setValue('REJECTED')
    await wrapper.get('[aria-label="拒绝安全反馈"]').setValue('9007199254740993')
    await submitForm(wrapper, '核对结论')
    await button(wrapper, '确认提交').trigger('click')
    await flushPromises()
    expect(api.decideOnlineExperiment).toHaveBeenCalledWith(
      experiment.id,
      expect.objectContaining({
        decision: 'REJECTED',
        safetyFeedbackId: '9007199254740993',
        reportRevision: 1,
        reportHash: frozen.reportHash,
      }),
      expect.any(AbortSignal),
    )
    expect(wrapper.emitted('decided')).toHaveLength(1)
  })
  it.each(['state', 'permission', 'reason', 'revision'])(
    '%s 变化使人工结论确认失效',
    async (cause) => {
      const wrapper = create()
      await load(wrapper)
      await wrapper.get('[aria-label="线上结论理由"]').setValue('证据不足')
      await submitForm(wrapper, '核对结论')
      expect(button(wrapper, '确认提交')).toBeDefined()
      if (cause === 'state')
        await wrapper.setProps({
          experiment: { ...experiment, status: 'STOPPED', stateVersion: '9007199254740994' },
        })
      if (cause === 'permission') await wrapper.setProps({ manageable: false })
      if (cause === 'reason')
        await wrapper.get('[aria-label="线上结论理由"]').setValue('改变判断理由')
      if (cause === 'revision') await wrapper.get('[aria-label="线上报告版本"]').setValue(2)
      expect(button(wrapper, '确认提交')).toBeUndefined()
      expect(api.decideOnlineExperiment).not.toHaveBeenCalled()
    },
  )
  it('显式重算核对截止，响应丢失重试保留 key；0 是有效截止序号', async () => {
    vi.mocked(api.recalculateOnlineReport).mockRejectedValue(new Error('响应丢失'))
    const wrapper = create()
    await wrapper.get('[aria-label="报告分配截止序号"]').setValue('0')
    await wrapper.get('[aria-label="报告观察截止"]').setValue('2026-10-12T12:00:00.000')
    await submitForm(wrapper, '核对重算范围')
    await button(wrapper, '确认提交').trigger('click')
    await flushPromises()
    await button(wrapper, '确认提交').trigger('click')
    await flushPromises()
    expect(vi.mocked(api.recalculateOnlineReport).mock.calls[0]![1].assignmentCutoffSequence).toBe(
      '0',
    )
    expect(vi.mocked(api.recalculateOnlineReport).mock.calls[1]![1].clientRequestKey).toBe(
      vi.mocked(api.recalculateOnlineReport).mock.calls[0]![1].clientRequestKey,
    )
    await wrapper.get('[aria-label="报告分配截止序号"]').setValue('1')
    expect(button(wrapper, '确认提交')).toBeUndefined()
  })
  it('只读用户没有重算或人工结论；读取撤销立即清除旧报告', async () => {
    const wrapper = create('STOPPED', false)
    await load(wrapper)
    expect(wrapper.find('[aria-label="报告分配截止序号"]').exists()).toBe(false)
    expect(wrapper.find('[aria-label="线上人工结论"]').exists()).toBe(false)
    await wrapper.setProps({ readable: false })
    expect(wrapper.text()).not.toContain('0.000000000000')
  })
  it('切换 revision 不静默改写旧版本，响应必须对应选择版本', async () => {
    const wrapper = create()
    await load(wrapper)
    await wrapper.get('[aria-label="线上报告版本"]').setValue(2)
    await load(wrapper)
    expect(wrapper.text()).toContain('报告身份或版本不一致')
    expect(wrapper.text()).not.toContain('0.000000000000')
  })
})
