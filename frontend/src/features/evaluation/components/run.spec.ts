import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import EvaluationRunView from '@/views/EvaluationRunView.vue'
import EvaluationCasePanel from './EvaluationCasePanel.vue'
import EvaluationResultPanel from './EvaluationResultPanel.vue'
import EvaluationRunCreate from './EvaluationRunCreate.vue'
import EvaluationFeedbackPanel from './EvaluationFeedbackPanel.vue'
import PublishedVersionPicker from './PublishedVersionPicker.vue'
import * as api from '../api/evaluation-api'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import type {
  DatasetVersion,
  EvaluationRun,
  EvaluationCaseRun,
  EvaluationCaseAttemptHistory,
  EvaluationResultSummary,
  EvaluationResultDetail,
  EvaluationRunSummary,
  EvaluationDataset,
  EvaluationFeedback,
  StandardMetric,
} from '../types'
import { RUN_POLL_INTERVAL, RUN_POLL_MAX_DELAY } from '../run'
vi.mock('../api/evaluation-api')
const wrappers: VueWrapper[] = []
const caseRun: EvaluationCaseRun = {
  id: '20',
  testCaseVersionId: '30',
  status: 'COMPLETED',
  currentAttemptId: '40',
  executionTaskId: '50',
  attemptNo: 2,
  feedback: [],
}
const run: EvaluationRun = {
  id: '10',
  spaceId: '7',
  datasetVersionId: '60',
  singleTestCaseVersionId: null,
  status: 'COMPLETED',
  pauseReason: null,
  cancelRequested: false,
  caseCount: 1,
  reconciliationFailureCount: 0,
  startedAt: null,
  finishedAt: null,
  cases: [caseRun],
}
const result: EvaluationResultSummary = {
  id: '80',
  evaluatorVersionId: '90',
  evaluatorName: '终态检查',
  evaluatorVersionNo: 1,
  evaluationAttemptNo: 2,
  currentEvaluationResultAttempt: true,
  status: 'PASSED',
  score: 0,
  summaryCode: 'PASSED',
  traceId: null,
  spanId: null,
  startedAt: null,
  finishedAt: null,
}
const attempt: EvaluationCaseAttemptHistory = {
  id: '40',
  attemptNo: 2,
  currentCaseAttempt: true,
  replayTaskId: '50',
  executionTaskId: '50',
  status: 'COMPLETED',
  failureStage: null,
  failureCode: null,
  failureMessage: null,
  startedAt: null,
  finishedAt: null,
  updatedAt: null,
  results: [result],
}
const historical = {
  ...attempt,
  id: '41',
  attemptNo: 1,
  currentCaseAttempt: false,
  status: 'REPLAY_FAILED' as const,
  results: [],
  executionTaskId: '51',
}
const detail: EvaluationResultDetail = {
  ...result,
  spaceId: '7',
  runId: '10',
  caseAttemptId: '40',
  detailsJson: 'PRIVATE_RESULT_BODY',
  implementationVersion: 'v1',
  createdAt: null,
  metrics: [],
  evidence: [],
  feedback: [],
}
const version: DatasetVersion = {
  id: '60',
  datasetId: '61',
  spaceId: '7',
  versionNo: 1,
  status: 'PUBLISHED',
  contentHash: 'hash',
  publishedAt: null,
  createdBy: null,
}
const page = <T>(records: T[], total = records.length, pageNum = 1) => ({
  records,
  total,
  pageNum,
  pageSize: 10,
})
const button = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((item) => item.text() === text)
beforeEach(() => {
  vi.resetAllMocks()
  setActivePinia(createPinia())
  const workspace = useWorkspaceStore()
  workspace.setCurrentSpace('7')
  workspace.setEffectivePermissions({
    spaceId: '7',
    role: null,
    platformSuperAdmin: false,
    permissions: [
      SPACE_PERMISSIONS.EVALUATION_READ,
      SPACE_PERMISSIONS.EVALUATION_RUN,
      SPACE_PERMISSIONS.TASK_READ,
    ],
  })
  vi.mocked(api.getEvaluationRun).mockResolvedValue(run)
  vi.mocked(api.searchEvaluationRuns).mockResolvedValue(
    page([
      {
        ...run,
        datasetName: '数据集',
        datasetVersionNo: 1,
        completedCaseCount: 1,
        errorCaseCount: 0,
        testCaseName: null,
        testCaseVersionNo: null,
        experimentVariantId: null,
        createdBy: null,
        createdAt: null,
        updatedAt: null,
      } satisfies EvaluationRunSummary,
    ]),
  )
  vi.mocked(api.searchCaseAttempts).mockResolvedValue(page([attempt, historical]))
  vi.mocked(api.getEvaluationResult).mockResolvedValue(detail)
  vi.mocked(api.getDatasetVersion).mockResolvedValue(version)
  vi.mocked(api.getDataset).mockResolvedValue({
    id: '61',
    spaceId: '7',
    archived: false,
    name: '数据集',
  } as EvaluationDataset)
  vi.mocked(api.getDatasetCases).mockResolvedValue([
    {
      id: '1',
      testCaseVersionId: '30',
      testCaseId: '31',
      testCaseName: '用例',
      versionNo: 1,
      status: 'PUBLISHED',
      sortOrder: 0,
      enabled: true,
    },
  ])
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(
    'confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>,
  )
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
  vi.restoreAllMocks()
})
async function mountRun(id?: string, query = '') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/spaces/:spaceId/evaluation/runs/:runId?', component: { template: '<div />' } },
    ],
  })
  await router.push(`/spaces/7/evaluation/runs${id ? `/${id}` : ''}${query}`)
  const wrapper = mount(EvaluationRunView, {
    props: { resourceId: id },
    global: {
      plugins: [router],
      stubs: {
        EvaluationCasePanel: true,
        EvaluationRunCreate: true,
        ElDialog: {
          props: ['modelValue'],
          template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
        },
      },
    },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}
function mountCase(extra = {}, realFeedback = false) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/spaces/:spaceId/tasks/:taskId', component: { template: '<div />' } }],
  })
  const wrapper = mount(EvaluationCasePanel, {
    props: {
      spaceId: '7',
      runId: '10',
      caseRun,
      revision: 0,
      canRun: true,
      canReadTask: true,
      cancelRequested: false,
      busy: false,
      ...extra,
    },
    global: {
      plugins: [router],
      stubs: { EvaluationResultPanel: true, EvaluationFeedbackPanel: !realFeedback },
    },
  })
  wrappers.push(wrapper)
  return wrapper
}
describe('run list, permissions and lifecycle', () => {
  it('resumes an initial read aborted by a hidden tab and never presents an old finish time as current', async () => {
    vi.useFakeTimers()
    let resolve!: (value: EvaluationRun) => void
    vi.mocked(api.getEvaluationRun).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    const { wrapper } = await mountRun('10')
    const signal = vi.mocked(api.getEvaluationRun).mock.calls[0]![1]!
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    document.dispatchEvent(new Event('visibilitychange'))
    expect(signal.aborted).toBe(true)
    vi.mocked(api.getEvaluationRun).mockResolvedValue({
      ...run,
      status: 'RUNNING',
      finishedAt: '2020-01-01T00:00:00',
    })
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    resolve(run)
    await flushPromises()
    expect(wrapper.text()).toContain('尚未结束')
    expect(wrapper.text()).not.toContain('2020-01-01')
    expect(api.getEvaluationRun).toHaveBeenCalledTimes(2)
  })
  it('queries one summary page, applies filters and rejects reversed times locally', async () => {
    const { wrapper } = await mountRun()
    expect(api.getEvaluationRun).not.toHaveBeenCalled()
    expect(api.searchCaseAttempts).not.toHaveBeenCalled()
    await wrapper.get('select[aria-label="运行状态"]').setValue('PAUSED')
    await wrapper.get('input[aria-label="创建起始"]').setValue('2026-10-04T12:00')
    await wrapper.get('input[aria-label="创建截止"]').setValue('2026-10-03T12:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.searchEvaluationRuns).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('创建起始不能晚于截止')
    await wrapper.get('input[aria-label="创建截止"]').setValue('2026-10-05T12:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.searchEvaluationRuns).toHaveBeenLastCalledWith(
      expect.objectContaining({ spaceId: '7', status: 'PAUSED', pageSize: 10 }),
      expect.any(AbortSignal),
    )
  })
  it('has no execute controls for a catalog manager without evaluation:run', async () => {
    useWorkspaceStore().currentPermissions!.permissions = [
      SPACE_PERMISSIONS.EVALUATION_READ,
      SPACE_PERMISSIONS.EVALUATION_MANAGE,
    ]
    const { wrapper } = await mountRun()
    expect(button(wrapper, '创建评估运行')).toBeUndefined()
    vi.mocked(api.getEvaluationRun).mockResolvedValue({ ...run, status: 'PAUSED' })
    await wrapper.setProps({ resourceId: '10' })
    await flushPromises()
    expect(button(wrapper, '恢复运行')).toBeUndefined()
    expect(button(wrapper, '取消运行')).toBeUndefined()
  })
  it.each(['COMPLETED', 'COMPLETED_WITH_ERRORS', 'CANCELED'] as const)(
    'shows %s without active cancellation or invented aggregate metrics',
    async (status) => {
      vi.mocked(api.getEvaluationRun).mockResolvedValue({ ...run, status })
      const { wrapper } = await mountRun('10')
      expect(wrapper.text()).toContain('用例结束')
      expect(button(wrapper, '取消运行')).toBeUndefined()
      expect(wrapper.text()).not.toContain('平均质量分')
      expect(wrapper.text()).not.toContain('预计剩余')
    },
  )
  it('uses a new TTL for paused resume and calls only the resume endpoint', async () => {
    vi.mocked(api.getEvaluationRun).mockResolvedValue({
      ...run,
      status: 'PAUSED',
      pauseReason: 'AUTHORIZATION_EXPIRED',
    })
    vi.mocked(api.resumeEvaluationRun).mockResolvedValue(run)
    const { wrapper } = await mountRun('10')
    expect(wrapper.text()).toContain('AUTHORIZATION_EXPIRED')
    await button(wrapper, '恢复运行')!.trigger('click')
    await wrapper.get('input[aria-label="重试或恢复授权有效期（秒）"]').setValue(7200)
    await wrapper
      .findAll('button')
      .filter((item) => item.text() === '恢复运行')
      .at(-1)!
      .trigger('click')
    await flushPromises()
    expect(api.resumeEvaluationRun).toHaveBeenCalledWith(
      '10',
      { workerCapabilityTtlSeconds: 7200 },
      expect.any(AbortSignal),
    )
    expect(api.createEvaluationRun).not.toHaveBeenCalled()
  })
  it('respects cancel dismissal and rejects cancellation after a route switch during confirmation', async () => {
    vi.mocked(api.getEvaluationRun).mockResolvedValue({ ...run, status: 'PAUSED' })
    const { wrapper, router } = await mountRun('10')
    vi.mocked(ElMessageBox.confirm).mockRejectedValueOnce('cancel')
    await button(wrapper, '取消运行')!.trigger('click')
    await flushPromises()
    expect(api.cancelEvaluationRun).not.toHaveBeenCalled()
    let resolve!: () => void
    vi.mocked(ElMessageBox.confirm).mockReturnValueOnce(
      new Promise((done) => {
        resolve = () => done('confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>)
      }),
    )
    await button(wrapper, '取消运行')!.trigger('click')
    await router.push('/spaces/8/evaluation/runs/10')
    await flushPromises()
    resolve()
    await flushPromises()
    expect(api.cancelEvaluationRun).not.toHaveBeenCalled()
  })
  it('rejects a run returned for another space and never mounts its case panel', async () => {
    vi.mocked(api.getEvaluationRun).mockResolvedValue({ ...run, spaceId: '8' })
    const { wrapper } = await mountRun('10')
    expect(wrapper.text()).toContain('运行身份或空间归属不一致')
    expect(wrapper.findComponent(EvaluationCasePanel).exists()).toBe(false)
  })
  it('rejects a case deep link outside the current run', async () => {
    const { wrapper } = await mountRun('10', '?caseRunId=99')
    expect(wrapper.text()).toContain('用例不属于当前运行')
    expect(wrapper.findComponent(EvaluationCasePanel).exists()).toBe(false)
  })
  it('pauses polling when hidden, backs off network errors, and stops at terminal state', async () => {
    vi.useFakeTimers()
    vi.mocked(api.getEvaluationRun).mockResolvedValue({ ...run, status: 'RUNNING' })
    const { wrapper } = await mountRun('10')
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(RUN_POLL_MAX_DELAY)
    expect(api.getEvaluationRun).toHaveBeenCalledTimes(1)
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    vi.mocked(api.getEvaluationRun).mockRejectedValueOnce(new Error('offline'))
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(wrapper.text()).toContain('刷新失败')
    await vi.advanceTimersByTimeAsync(RUN_POLL_INTERVAL)
    expect(api.getEvaluationRun).toHaveBeenCalledTimes(2)
    vi.mocked(api.getEvaluationRun).mockResolvedValue(run)
    await vi.advanceTimersByTimeAsync(RUN_POLL_INTERVAL)
    await flushPromises()
    expect(api.getEvaluationRun).toHaveBeenCalledTimes(3)
    await vi.advanceTimersByTimeAsync(RUN_POLL_MAX_DELAY)
    expect(api.getEvaluationRun).toHaveBeenCalledTimes(3)
  })
  it('aborts pending reads on navigation and ignores late responses', async () => {
    let resolve!: (value: EvaluationRun) => void
    vi.mocked(api.getEvaluationRun).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const { wrapper, router } = await mountRun('10')
    const signal = vi.mocked(api.getEvaluationRun).mock.calls[0]![1]!
    await router.push('/spaces/8/evaluation/runs/10')
    await flushPromises()
    resolve(run)
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(wrapper.findComponent(EvaluationCasePanel).exists()).toBe(false)
  })
})
describe('attempt and evaluation result histories', () => {
  it('keeps typed feedback during a background history refresh', async () => {
    const wrapper = mountCase({}, true)
    await flushPromises()
    await wrapper.get('textarea[aria-label="反馈说明"]').setValue('正在撰写的反馈')
    let resolve!: (value: ReturnType<typeof page<EvaluationCaseAttemptHistory>>) => void
    vi.mocked(api.searchCaseAttempts).mockReturnValueOnce(
      new Promise((done) => {
        resolve = done
      }),
    )
    await wrapper.setProps({ revision: 1 })
    await flushPromises()
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('正在撰写的反馈')
    resolve(page([attempt, historical]))
    await flushPromises()
    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe('正在撰写的反馈')
  })
  it('defaults to current Attempt and Result and switches old attempts to read-only', async () => {
    const wrapper = mountCase()
    await flushPromises()
    expect(wrapper.findComponent(EvaluationResultPanel).props('summary').id).toBe('80')
    expect(button(wrapper, '仅重试评估器')).toBeDefined()
    await wrapper
      .findAll('button')
      .find((item) => item.text().includes('Attempt 1'))!
      .trigger('click')
    await flushPromises()
    expect(button(wrapper, '仅重试评估器')).toBeUndefined()
    expect(button(wrapper, '重放模型与工具')).toBeUndefined()
    expect(wrapper.text()).toContain('回放失败，缺少评估结果与指标')
    expect(wrapper.findComponent(EvaluationFeedbackPanel).props('canWrite')).toBe(false)
  })
  it('makes a historical Result read-only and follows a newly appended current Result on refresh', async () => {
    const old = {
      ...result,
      id: '81',
      evaluationAttemptNo: 1,
      currentEvaluationResultAttempt: false,
    }
    vi.mocked(api.searchCaseAttempts).mockResolvedValue(
      page([{ ...attempt, results: [result, old] }]),
    )
    const wrapper = mountCase()
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((item) => item.text().includes('Result #81'))!
      .trigger('click')
    expect(button(wrapper, '仅重试评估器')).toBeUndefined()
    expect(wrapper.findComponent(EvaluationFeedbackPanel).props('canWrite')).toBe(false)
    await wrapper
      .findAll('button')
      .find((item) => item.text().includes('Result #80'))!
      .trigger('click')
    const next = { ...result, id: '82', evaluationAttemptNo: 3 }
    vi.mocked(api.searchCaseAttempts).mockResolvedValue(
      page([
        { ...attempt, results: [next, { ...result, currentEvaluationResultAttempt: false }, old] },
      ]),
    )
    await wrapper.setProps({ revision: 1 })
    await flushPromises()
    expect(wrapper.findComponent(EvaluationResultPanel).props('summary').id).toBe('82')
  })
  it('locates a deep-linked historical Attempt on a later page without per-result detail reads', async () => {
    vi.mocked(api.searchCaseAttempts)
      .mockResolvedValueOnce(page([attempt], 11))
      .mockResolvedValueOnce(page([historical], 11, 2))
    const wrapper = mountCase({ initialAttemptId: '41' })
    await flushPromises()
    expect(api.searchCaseAttempts).toHaveBeenCalledTimes(2)
    expect(api.getEvaluationResult).not.toHaveBeenCalled()
    expect(button(wrapper, '重放模型与工具')).toBeUndefined()
  })
  it('rejects unrelated Result and inconsistent current Attempt identities', async () => {
    const wrapper = mountCase({ initialResultId: '99' })
    await flushPromises()
    expect(wrapper.text()).toContain('评价结果不属于当前执行尝试')
    vi.mocked(api.searchCaseAttempts).mockResolvedValue(
      page([{ ...historical, currentCaseAttempt: true }]),
    )
    await wrapper.setProps({ revision: 1 })
    await flushPromises()
    expect(wrapper.text()).toContain('当前执行尝试身份不一致')
  })
  it('emits separate replay and evaluator identities and suppresses task links without task:read', async () => {
    const wrapper = mountCase({ canReadTask: false })
    await flushPromises()
    expect(wrapper.find('a').exists()).toBe(false)
    await button(wrapper, '重放模型与工具')!.trigger('click')
    await button(wrapper, '仅重试评估器')!.trigger('click')
    expect(wrapper.emitted('retryReplay')).toEqual([[]])
    expect(wrapper.emitted('retryEvaluation')).toEqual([['40']])
  })
})
describe('result facts, evidence and feedback', () => {
  function mountResult() {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [{ path: '/spaces/:spaceId/tasks/:taskId', component: { template: '<div />' } }],
    })
    const wrapper = mount(EvaluationResultPanel, {
      props: {
        spaceId: '7',
        runId: '10',
        caseRunId: '20',
        attemptId: '40',
        summary: result,
        taskId: '50',
        canReadTask: true,
        revision: 0,
      },
      global: { plugins: [router] },
    })
    wrappers.push(wrapper)
    return wrapper
  }
  it('preserves zero, false, empty string and missing separately and hides private payload/locators', async () => {
    const metric = {
      id: '1',
      spaceId: '7',
      runId: '10',
      caseRunId: '20',
      caseAttemptId: '40',
      evaluationResultId: '80',
      numericValue: null,
      booleanValue: null,
      stringValue: null,
      evidence: [],
      metricKey: 'quality',
      valueType: 'NUMBER',
    } as unknown as StandardMetric
    vi.mocked(api.getEvaluationResult).mockResolvedValue({
      ...detail,
      metrics: [
        { ...metric, id: '1', numericValue: 0 },
        { ...metric, id: '2', valueType: 'BOOLEAN', booleanValue: false },
        { ...metric, id: '3', valueType: 'STRING', stringValue: '' },
        { ...metric, id: '4' },
      ],
      evidence: [
        {
          id: '5',
          evidenceType: 'TRACE',
          businessId: 'trace',
          contentHash: 'hash',
          summary: '脱敏摘要',
          locatorJson: 'PRIVATE_LOCATOR',
        },
      ],
    })
    const wrapper = mountResult()
    await flushPromises()
    expect(wrapper.text()).toContain('false')
    expect(wrapper.text()).toContain('空字符串')
    expect(wrapper.text()).toContain('VALUE_MISSING')
    expect(wrapper.text()).toContain('便捷得分0')
    expect(wrapper.text()).not.toContain('PRIVATE_RESULT_BODY')
    expect(wrapper.text()).not.toContain('PRIVATE_LOCATOR')
    expect(wrapper.get('a').attributes('href')).toContain('/tasks/50?tab=audit')
  })
  it('rejects cross-space Result details before displaying evidence', async () => {
    vi.mocked(api.getEvaluationResult).mockResolvedValue({ ...detail, spaceId: '8' })
    const wrapper = mountResult()
    await flushPromises()
    expect(wrapper.text()).toContain('结果身份或空间归属不一致')
    expect(wrapper.find('dl').exists()).toBe(false)
  })
  it('displays duplicate manual and ChangeRequest feedback independently without treating zero as missing', async () => {
    const row = {
      id: '1',
      spaceId: '7',
      runId: '10',
      caseRunId: '20',
      taskId: '50',
      sourceType: 'MANUAL',
      label: 'REJECTED',
      score: 0,
      comment: '需改进',
    } as EvaluationFeedback
    const wrapper = mount(EvaluationFeedbackPanel, {
      props: {
        spaceId: '7',
        runId: '10',
        caseRunId: '20',
        taskId: '50',
        records: [row, { ...row, id: '2' }, { ...row, id: '3', sourceType: 'CHANGE_REQUEST' }],
        canWrite: false,
      },
    })
    wrappers.push(wrapper)
    expect(wrapper.findAll('article')).toHaveLength(3)
    expect(wrapper.text()).toContain('人工分数 0')
    expect(wrapper.find('form').exists()).toBe(false)
  })
  it('validates feedback score and stops a pending confirmation when the target changes', async () => {
    const wrapper = mount(EvaluationFeedbackPanel, {
      props: {
        spaceId: '7',
        runId: '10',
        caseRunId: '20',
        taskId: '50',
        records: [],
        canWrite: true,
      },
    })
    wrappers.push(wrapper)
    await wrapper.get('input').setValue(2)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.createEvaluationFeedback).not.toHaveBeenCalled()
    await wrapper.get('input').setValue(0)
    let resolve!: () => void
    vi.mocked(ElMessageBox.confirm).mockReturnValueOnce(
      new Promise((done) => {
        resolve = () => done('confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>)
      }),
    )
    await wrapper.get('form').trigger('submit')
    await wrapper.setProps({ caseRunId: '21' })
    resolve()
    await flushPromises()
    expect(api.createEvaluationFeedback).not.toHaveBeenCalled()
  })
})
describe('creation from published resources', () => {
  async function mountCreate() {
    const wrapper = mount(EvaluationRunCreate, {
      props: { spaceId: '7', canRun: true },
      global: {
        stubs: { PublishedVersionPicker: true, ElDialog: { template: '<div><slot /></div>' } },
      },
    })
    wrappers.push(wrapper)
    wrapper.findComponent(PublishedVersionPicker).vm.$emit('select', version, '数据集')
    await flushPromises()
    return wrapper
  }
  it('shows enabled case count, rechecks the source and submits only one source', async () => {
    vi.mocked(api.createEvaluationRun).mockResolvedValue(run)
    const wrapper = await mountCreate()
    expect(wrapper.text()).toContain('计划 1 个用例')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.getDatasetVersion).toHaveBeenCalledTimes(2)
    expect(api.createEvaluationRun).toHaveBeenCalledWith(
      { spaceId: '7', datasetVersionId: '60', workerCapabilityTtlSeconds: 3600 },
      expect.any(AbortSignal),
    )
  })
  it('does not create when the parent is archived after selection', async () => {
    const wrapper = await mountCreate()
    vi.mocked(api.getDataset).mockResolvedValue({
      id: '61',
      spaceId: '7',
      archived: true,
    } as EvaluationDataset)
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.createEvaluationRun).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('来源资源已归档')
  })
  it('rejects invalid TTL and ignores source verification after a space change', async () => {
    const wrapper = await mountCreate()
    await wrapper.get('input').setValue(299)
    await wrapper.get('form').trigger('submit')
    expect(api.createEvaluationRun).not.toHaveBeenCalled()
    let resolve!: (value: DatasetVersion) => void
    vi.mocked(api.getDatasetVersion).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    wrapper.findComponent(PublishedVersionPicker).vm.$emit('select', version, '数据集')
    await flushPromises()
    const signal = vi.mocked(api.getDatasetVersion).mock.calls.at(-1)![1]!
    await wrapper.setProps({ spaceId: '8' })
    resolve(version)
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(api.createEvaluationRun).not.toHaveBeenCalled()
  })
})
