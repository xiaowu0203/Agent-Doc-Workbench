import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { beforeEach, afterEach, describe, it, expect, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import ExperimentView from '@/views/ExperimentView.vue'
import ExperimentCreate from './ExperimentCreate.vue'
import ExperimentReportPanel from './ExperimentReportPanel.vue'
import PublishedVersionPicker from './PublishedVersionPicker.vue'
import * as api from '../api/evaluation-api'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS as P } from '@/shared/constants/permissions'
import { RUN_POLL_INTERVAL } from '../run'
import type {
  Experiment,
  ExperimentVariant,
  ExperimentReport,
  ExperimentReportContent,
  ExperimentSummary,
  ExperimentPreflight,
  DatasetVersion,
} from '../types'
vi.mock('../api/evaluation-api')
const wrappers: VueWrapper[] = []
const variant: ExperimentVariant = {
  id: '11',
  experimentId: '10',
  variantKey: 'baseline',
  role: 'BASELINE',
  type: 'PROMPT',
  candidateConfigRef: null,
  sourceSnapshotSchemaVersion: 3,
  sourceSnapshotHash: 'source-hash',
  candidateSnapshotSchemaVersion: 3,
  candidateSnapshotHash: 'source-hash',
  promptDiffFieldPaths: [],
  snapshotWithoutPromptHash: 'without-prompt',
  evaluationRunId: '20',
}
const candidate: ExperimentVariant = {
  ...variant,
  id: '12',
  variantKey: 'prompt-v2',
  role: 'CANDIDATE',
  candidateConfigRef: '30',
  candidateSnapshotHash: 'candidate-hash',
  promptDiffFieldPaths: ['snapshot.systemPrompt'],
  evaluationRunId: '21',
}
const experiment: Experiment = {
  id: '10',
  spaceId: '7',
  datasetVersionId: '40',
  status: 'COMPLETED',
  manifestSchemaVersion: 1,
  manifestHash: 'manifest-hash',
  authorizedTokenBudget: 8192,
  actualTokenUsage: 4000,
  budgetOverrun: 0,
  failureCode: null,
  failureMessage: null,
  createdBy: '1',
  startedBy: '1',
  startedAt: null,
  cancelRequestedBy: null,
  cancelRequestedAt: null,
  decision: null,
  decisionReason: null,
  decisionReportRevision: null,
  decidedBy: null,
  decidedAt: null,
  finishedAt: null,
  variants: [variant, candidate],
}
const content: ExperimentReportContent = {
  expectedCaseCount: 3,
  variantCount: 2,
  cases: [
    {
      variantKey: 'baseline',
      runId: '20',
      testCaseVersionId: '50',
      caseRunId: '60',
      attemptId: '70',
      taskId: '80',
      attemptStatus: 'COMPLETED',
      failureCode: null,
    },
  ],
  cells: [
    {
      value: {
        variantKey: 'baseline',
        runId: '20',
        testCaseVersionId: '50',
        metricKey: 'quality',
        evaluatorVersionId: '90',
        metricId: '100',
        numericValue: 0,
        booleanValue: null,
        stringValue: null,
        unit: 'ratio',
        missingReason: null,
      },
      evidenceIds: ['110'],
    },
  ],
  summaries: [
    {
      variantKey: 'baseline',
      metricKey: 'quality',
      expectedCount: 1,
      validCount: 1,
      missingCount: 0,
      trueCount: 0,
      falseCount: 0,
      numericTotalsByUnit: { ratio: 0 },
      numericMeansByUnit: { ratio: 0 },
      missingReasons: {},
    },
    {
      variantKey: 'prompt-v2',
      metricKey: 'quality',
      expectedCount: 1,
      validCount: 1,
      missingCount: 0,
      trueCount: 0,
      falseCount: 0,
      numericTotalsByUnit: { ratio: 0.1 },
      numericMeansByUnit: { ratio: 0.1 },
      missingReasons: {},
    },
  ],
  comparisons: [
    {
      candidateVariantKey: 'prompt-v2',
      metricKey: 'quality',
      pairedCount: 1,
      incomparableCurrencyCount: 0,
      improvedCount: 1,
      worsenedCount: 0,
      unchangedCount: 0,
      meanCandidateMinusBaseline: 0.1,
      meanCandidateMinusBaselineByCurrency: {},
    },
  ],
  metricEvidence: [
    {
      metricId: '100',
      evidenceId: '110',
      evidenceType: 'TASK',
      businessId: '80',
      contentHash: 'evidence-hash',
    },
  ],
  feedback: [
    {
      id: '120',
      variantKey: 'baseline',
      testCaseVersionId: '50',
      sourceType: 'MANUAL',
      sourceBusinessId: null,
      label: 'ACCEPTED',
      score: 0,
    },
    {
      id: '121',
      variantKey: 'baseline',
      testCaseVersionId: '50',
      sourceType: 'CHANGE_REQUEST',
      sourceBusinessId: '122',
      label: 'REJECTED',
      score: null,
    },
  ],
  feedbackCoverage: [
    { variantKey: 'baseline', sourceType: 'MANUAL', coveredCaseCount: 1 },
    { variantKey: 'baseline', sourceType: 'CHANGE_REQUEST', coveredCaseCount: 1 },
  ],
  authorizedTokenBudget: 8192,
  actualTokenUsage: 0,
  budgetOverrun: 0,
}
const report: ExperimentReport = {
  experimentId: '10',
  revision: 1,
  schemaVersion: 1,
  manifestHash: 'manifest-hash',
  calculationInputHash: 'calculation-hash',
  selectedRecordIds: {
    runIds: ['20', '21'],
    attemptIds: ['70'],
    resultIds: ['130'],
    metricIds: ['100'],
    evidenceIds: ['110'],
    feedbackIds: ['120', '121'],
  },
  report: content,
  contentHash: 'content-hash',
  generatedBy: '1',
  generatedAt: null,
  compatible: true,
  compatibilityCode: 'SUPPORTED',
}
const preflight: ExperimentPreflight = {
  experimentId: '10',
  caseCount: 1,
  variantCount: 2,
  plannedTaskCount: 2,
  plannedTokenBudget: 8192,
  eligible: true,
  issues: [],
}
const dataset: DatasetVersion = {
  id: '40',
  datasetId: '41',
  spaceId: '7',
  versionNo: 1,
  status: 'PUBLISHED',
  contentHash: 'dataset-hash',
  publishedAt: null,
  createdBy: null,
}
const dialogStub = {
  props: ['modelValue'],
  template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
}
const button = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((item) => item.text() === text)
function permissions(values: string[]) {
  useWorkspaceStore().setEffectivePermissions({
    spaceId: '7',
    role: null,
    platformSuperAdmin: false,
    permissions: values,
  })
}
async function mountView(id?: string, query = '') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/spaces/:spaceId/evaluation/experiments/:experimentId?',
        component: { template: '<div />' },
      },
      { path: '/spaces/:spaceId/evaluation/runs/:id', component: { template: '<div />' } },
      { path: '/spaces/:spaceId/tasks/:id', component: { template: '<div />' } },
    ],
  })
  await router.push(`/spaces/7/evaluation/experiments${id ? `/${id}` : ''}${query}`)
  const wrapper = mount(ExperimentView, {
    props: { resourceId: id },
    global: {
      plugins: [router],
      stubs: { ExperimentCreate: true, ExperimentReportPanel: true, ElDialog: dialogStub },
    },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}
async function mountReport(value = report, canReadTask = true) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      { path: '/spaces/:spaceId/evaluation/runs/:id', component: { template: '<div />' } },
      { path: '/spaces/:spaceId/tasks/:id', component: { template: '<div />' } },
    ],
  })
  await router.push('/')
  const wrapper = mount(ExperimentReportPanel, {
    props: { report: value, spaceId: '7', canReadTask },
    global: { plugins: [router] },
  })
  wrappers.push(wrapper)
  return wrapper
}
function mountCreate() {
  const wrapper = mount(ExperimentCreate, {
    props: { spaceId: '7', allowed: true },
    global: { stubs: { ElDialog: dialogStub, PublishedVersionPicker: true } },
  })
  wrappers.push(wrapper)
  return wrapper
}
async function fillCreate(wrapper: VueWrapper) {
  await button(wrapper, '选择已发布数据集版本')!.trigger('click')
  wrapper.findComponent(PublishedVersionPicker).vm.$emit('select', dataset, '数据集')
  await wrapper.get('[aria-label="候选 Prompt 1"]').setValue('测试候选 Prompt')
}
beforeEach(() => {
  vi.resetAllMocks()
  setActivePinia(createPinia())
  useWorkspaceStore().setCurrentSpace('7')
  permissions([
    P.EVALUATION_READ,
    P.EVALUATION_MANAGE,
    P.EVALUATION_RUN,
    P.AGENT_MANAGE,
    P.TASK_READ,
  ])
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(
    'confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>,
  )
  vi.mocked(api.getExperiment).mockResolvedValue(experiment)
  vi.mocked(api.searchExperiments).mockResolvedValue({
    records: [
      {
        id: '10',
        spaceId: '7',
        datasetVersionId: '40',
        datasetName: '数据集',
        datasetVersionNo: 1,
        status: 'COMPLETED',
        variantCount: 2,
        linkedRunCount: 2,
        authorizedTokenBudget: 8192,
        failureCode: null,
        decision: null,
        decisionReportRevision: null,
        createdBy: '1',
        createdAt: null,
        startedAt: null,
        finishedAt: null,
        updatedAt: null,
      } satisfies ExperimentSummary,
    ],
    total: 1,
    pageNum: 1,
    pageSize: 10,
  })
  vi.mocked(api.getEvaluationRun).mockImplementation(async (id) => ({
    id,
    spaceId: '7',
    datasetVersionId: '40',
    singleTestCaseVersionId: null,
    status: 'COMPLETED',
    pauseReason: null,
    cancelRequested: false,
    caseCount: 1,
    reconciliationFailureCount: 0,
    startedAt: null,
    finishedAt: null,
    cases: [],
  }))
  vi.mocked(api.getExperimentReportRevisions).mockResolvedValue([
    {
      experimentId: '10',
      revision: 1,
      schemaVersion: 1,
      contentHash: 'content-hash',
      generatedBy: '1',
      generatedAt: null,
    },
  ])
  vi.mocked(api.getExperimentReport).mockResolvedValue(report)
  vi.mocked(api.getExperimentPreflight).mockResolvedValue(preflight)
  vi.mocked(api.startExperiment).mockResolvedValue({ ...experiment, status: 'RUNNING' })
  vi.mocked(api.cancelExperiment).mockResolvedValue({ ...experiment, status: 'CANCEL_PENDING' })
  vi.mocked(api.recalculateExperimentReport).mockResolvedValue(report)
  vi.mocked(api.getDatasetVersion).mockResolvedValue(dataset)
  vi.mocked(api.getDataset).mockResolvedValue({
    id: '41',
    spaceId: '7',
    name: '数据集',
    description: null,
    archived: false,
    createdBy: null,
    createdAt: null,
  })
  vi.mocked(api.createExperiment).mockResolvedValue(experiment)
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
  vi.restoreAllMocks()
})
describe('Experiment access and observation', () => {
  it('uses one summary query with filters and validates reversed dates', async () => {
    const { wrapper } = await mountView()
    expect(api.getExperiment).not.toHaveBeenCalled()
    await wrapper.get('[aria-label="实验状态"]').setValue('PAUSED')
    await wrapper.get('[aria-label="筛选数据集版本 ID"]').setValue('40')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(api.searchExperiments).toHaveBeenLastCalledWith(
      expect.objectContaining({ status: 'PAUSED', datasetVersionId: '40', pageSize: 10 }),
      expect.anything(),
    )
    await wrapper.get('[aria-label="实验创建起始"]').setValue('2026-10-04T12:00')
    await wrapper.get('[aria-label="实验创建截止"]').setValue('2026-10-03T12:00')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(wrapper.text()).toContain('创建起始不能晚于截止')
  })
  it('requires both manage permissions to create and never infers run permission', async () => {
    permissions([P.EVALUATION_READ, P.EVALUATION_MANAGE])
    const { wrapper } = await mountView()
    expect(button(wrapper, '创建离线实验')).toBeUndefined()
    vi.mocked(api.getExperiment).mockResolvedValue({ ...experiment, status: 'CREATED' })
    const detail = await mountView('10')
    expect(button(detail.wrapper, '预检并启动实验')).toBeUndefined()
    expect(button(detail.wrapper, '取消实验')).toBeUndefined()
  })
  it('queries only one selected Variant run, then switches explicitly', async () => {
    const { wrapper } = await mountView('10')
    expect(api.getEvaluationRun).toHaveBeenCalledTimes(1)
    expect(api.getEvaluationRun).toHaveBeenCalledWith('20', expect.anything())
    await button(wrapper, '查看 prompt-v2 进度')!.trigger('click')
    await flushPromises()
    expect(api.getEvaluationRun).toHaveBeenLastCalledWith('21', expect.anything())
  })
  it('rejects cross-space experiment and run projections', async () => {
    vi.mocked(api.getExperiment).mockResolvedValue({ ...experiment, spaceId: '8' })
    const { wrapper } = await mountView('10')
    expect(wrapper.text()).toContain('实验身份或空间归属不一致')
    expect(api.getExperimentReport).not.toHaveBeenCalled()
    vi.mocked(api.getExperiment).mockResolvedValue(experiment)
    vi.mocked(api.getEvaluationRun).mockResolvedValue({ id: '20', spaceId: '8' } as Awaited<
      ReturnType<typeof api.getEvaluationRun>
    >)
    await button(wrapper, '刷新')!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('关联运行身份或空间归属不一致')
  })
  it('pauses hidden polling, backs off failures and stops on terminal state', async () => {
    vi.useFakeTimers()
    vi.mocked(api.getExperiment).mockResolvedValue({ ...experiment, status: 'RUNNING' })
    await mountView('10')
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(true)
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(RUN_POLL_INTERVAL * 3)
    expect(api.getExperiment).toHaveBeenCalledTimes(1)
    vi.spyOn(document, 'hidden', 'get').mockReturnValue(false)
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(api.getExperiment).toHaveBeenCalledTimes(2)
    vi.mocked(api.getExperiment).mockRejectedValueOnce(new Error('offline'))
    await vi.advanceTimersByTimeAsync(RUN_POLL_INTERVAL)
    await flushPromises()
    expect(api.getExperiment).toHaveBeenCalledTimes(3)
    await vi.advanceTimersByTimeAsync(RUN_POLL_INTERVAL)
    expect(api.getExperiment).toHaveBeenCalledTimes(3)
    vi.mocked(api.getExperiment).mockResolvedValue(experiment)
    await vi.advanceTimersByTimeAsync(RUN_POLL_INTERVAL)
    await flushPromises()
    expect(api.getExperiment).toHaveBeenCalledTimes(4)
    await vi.advanceTimersByTimeAsync(30000)
    expect(api.getExperiment).toHaveBeenCalledTimes(4)
  })
  it('aborts pending reads on navigation and ignores late responses', async () => {
    let resolve!: (value: Experiment) => void
    vi.mocked(api.getExperiment).mockReturnValue(
      new Promise((value) => {
        resolve = value
      }),
    )
    const { wrapper, router } = await mountView('10')
    const signal = vi.mocked(api.getExperiment).mock.calls[0]![1]!
    await router.push('/spaces/8/evaluation/experiments/10')
    expect(signal.aborted).toBe(true)
    resolve(experiment)
    await flushPromises()
    expect(wrapper.text()).not.toContain('manifest-hash')
  })
})
describe('preflight, budget and mutations', () => {
  async function openBudget(status: 'CREATED' | 'PAUSED' = 'CREATED') {
    vi.mocked(api.getExperiment).mockResolvedValue({
      ...experiment,
      status,
      authorizedTokenBudget: status === 'PAUSED' ? 8192 : null,
    })
    const { wrapper } = await mountView('10')
    await button(wrapper, status === 'PAUSED' ? '预检并恢复实验' : '预检并启动实验')!.trigger(
      'click',
    )
    await flushPromises()
    return wrapper
  }
  it('requires eligible preflight, typed budget and explicit confirmation', async () => {
    const wrapper = await openBudget()
    expect((wrapper.get('[aria-label="授权 Token 预算"]').element as HTMLInputElement).value).toBe(
      '',
    )
    expect(button(wrapper, '确认并启动')!.attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="授权 Token 预算"]').setValue(8191)
    await wrapper.get('[aria-label="确认实验预算与隔离执行"]').setValue(true)
    expect(button(wrapper, '确认并启动')!.attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="授权 Token 预算"]').setValue(8192)
    await button(wrapper, '确认并启动')!.trigger('click')
    await flushPromises()
    expect(api.getExperimentPreflight).toHaveBeenCalledTimes(2)
    expect(api.startExperiment).toHaveBeenCalledWith(
      '10',
      { authorizedTokenBudget: 8192, workerCapabilityTtlSeconds: 3600 },
      expect.anything(),
    )
  })
  it('shows every ineligible reason and does not open start confirmation', async () => {
    vi.mocked(api.getExperimentPreflight).mockResolvedValue({
      ...preflight,
      eligible: false,
      issues: [
        {
          testCaseVersionId: '50',
          code: 'SOURCE_NOT_REPLAYABLE',
          detailReasonCode: 'DOCUMENT_MISSING',
        },
      ],
    })
    const wrapper = await openBudget()
    expect(wrapper.text()).toContain('DOCUMENT_MISSING')
    expect(wrapper.find('[aria-label="授权 Token 预算"]').exists()).toBe(false)
    expect(api.startExperiment).not.toHaveBeenCalled()
  })
  it('resumes using exactly the original budget, never a silently preselected budget', async () => {
    const wrapper = await openBudget('PAUSED')
    await wrapper.get('[aria-label="授权 Token 预算"]').setValue(9000)
    await wrapper.get('[aria-label="确认实验预算与隔离执行"]').setValue(true)
    expect(button(wrapper, '确认并恢复')!.attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="授权 Token 预算"]').setValue(8192)
    expect(button(wrapper, '确认并恢复')!.attributes('disabled')).toBeUndefined()
  })
  it('refuses start after preflight budget grows or while confirmation changes scope', async () => {
    const wrapper = await openBudget()
    await wrapper.get('[aria-label="授权 Token 预算"]').setValue(8192)
    await wrapper.get('[aria-label="确认实验预算与隔离执行"]').setValue(true)
    vi.mocked(api.getExperimentPreflight).mockResolvedValue({
      ...preflight,
      plannedTokenBudget: 9000,
    })
    await button(wrapper, '确认并启动')!.trigger('click')
    await flushPromises()
    expect(api.startExperiment).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('计划预算已变化')
  })
  it('ignores cancel confirmation after navigation', async () => {
    vi.mocked(api.getExperiment).mockResolvedValue({ ...experiment, status: 'PAUSED' })
    const { wrapper, router } = await mountView('10')
    let resolve!: () => void
    vi.mocked(ElMessageBox.confirm).mockReturnValueOnce(
      new Promise((value) => {
        resolve = () => value('confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>)
      }),
    )
    await button(wrapper, '取消实验')!.trigger('click')
    await router.push('/spaces/8/evaluation/experiments/10')
    resolve()
    await flushPromises()
    expect(api.cancelExperiment).not.toHaveBeenCalled()
  })
  it('reuses recalculation key after network failure and accepts the same revision', async () => {
    const { wrapper } = await mountView('10')
    vi.mocked(api.recalculateExperimentReport).mockRejectedValueOnce(new Error('offline'))
    await button(wrapper, '重算报告')!.trigger('click')
    await flushPromises()
    const key = vi.mocked(api.recalculateExperimentReport).mock.calls[0]![1].clientRequestKey
    await button(wrapper, '重算报告')!.trigger('click')
    await flushPromises()
    expect(vi.mocked(api.recalculateExperimentReport).mock.calls[1]![1].clientRequestKey).toBe(key)
    expect(wrapper.get('[aria-label="报告版本"]').element).toHaveProperty('value', '1')
  })
  it('pins an insufficient evidence decision to selected revision and preserves existing conclusions', async () => {
    const { wrapper } = await mountView('10')
    await button(wrapper, '记录人工结论')!.trigger('click')
    await wrapper.get('[aria-label="实验结论依据"]').setValue('证据尚不足')
    await button(wrapper, '记录结论')!.trigger('click')
    await flushPromises()
    expect(api.submitExperimentDecision).toHaveBeenCalledWith(
      '10',
      { reportRevision: 1, decision: 'INSUFFICIENT_EVIDENCE', reason: '证据尚不足' },
      expect.anything(),
    )
    vi.mocked(api.getExperiment).mockResolvedValue({
      ...experiment,
      decision: 'INSUFFICIENT_EVIDENCE',
      decisionReportRevision: 1,
      decisionReason: '证据尚不足',
    })
    await button(wrapper, '刷新')!.trigger('click')
    await flushPromises()
    expect(button(wrapper, '记录人工结论')).toBeUndefined()
  })
  it('rejects unrelated report revisions and reports', async () => {
    const { wrapper } = await mountView('10', '?reportRevision=2')
    expect(wrapper.text()).toContain('请求的报告版本不属于当前实验')
    expect(api.getExperimentReport).not.toHaveBeenCalled()
    vi.mocked(api.getExperimentReport).mockResolvedValue({ ...report, experimentId: '999' })
    const other = await mountView('10')
    expect(other.wrapper.text()).toContain('报告不属于当前实验')
    expect(button(other.wrapper, '记录人工结论')).toBeUndefined()
  })
})
describe('report facts and compatibility', () => {
  it('keeps zero, false, empty string and missing distinct and uses metric-specific pair expectation', async () => {
    const cells = content.cells
    const extra = [
      { ...cells[0]!, value: { ...cells[0]!.value, numericValue: null, booleanValue: false } },
      { ...cells[0]!, value: { ...cells[0]!.value, numericValue: null, stringValue: '' } },
      {
        ...cells[0]!,
        value: { ...cells[0]!.value, numericValue: null, missingReason: 'EVIDENCE_MISSING' },
      },
    ]
    const wrapper = await mountReport({
      ...report,
      report: { ...content, cells: [...cells, ...extra] },
    })
    expect(wrapper.text()).toContain('false')
    expect(wrapper.text()).toContain('空字符串')
    expect(wrapper.text()).toContain('缺失：EVIDENCE_MISSING')
    expect(wrapper.find('[aria-label="Candidate 配对比较"]').text()).toContain('1 / 1')
    expect(wrapper.text()).toContain('分数 0')
    expect(wrapper.text()).toContain('CHANGE_REQUEST')
    expect(wrapper.find('a').attributes('href')).toContain('caseRunId=60&attemptId=70')
  })
  it.each([
    'SCHEMA_MISSING',
    'SCHEMA_INVALID',
    'SCHEMA_UNSUPPORTED',
    'PAYLOAD_INVALID',
    'CONTENT_HASH_MISMATCH',
  ] as const)('hides incompatible %s content without recomputing', async (code) => {
    const wrapper = await mountReport({ ...report, compatible: false, compatibilityCode: code })
    expect(wrapper.text()).toContain(code)
    expect(wrapper.text()).toContain('content-hash')
    expect(wrapper.find('[aria-label="逐用例原值矩阵"]').exists()).toBe(false)
    expect(api.recalculateExperimentReport).not.toHaveBeenCalled()
  })
  it('separates currencies and draws no bars for multiple currencies or empty denominators', async () => {
    const summaries = content.summaries.map((row) => ({
      ...row,
      metricKey: 'execution.cost',
      numericTotalsByUnit: { USD: 0, CNY: 4 },
      numericMeansByUnit: { USD: 0, CNY: 4 },
    }))
    const wrapper = await mountReport({
      ...report,
      report: {
        ...content,
        summaries,
        comparisons: [
          {
            ...content.comparisons[0]!,
            metricKey: 'execution.cost',
            pairedCount: 0,
            incomparableCurrencyCount: 1,
            meanCandidateMinusBaseline: null,
          },
        ],
      },
    })
    expect(wrapper.text()).toContain('USD')
    expect(wrapper.text()).toContain('CNY')
    expect(wrapper.findAll('figure')).toHaveLength(0)
    expect(wrapper.text()).toContain('无有效配对，不计算差值')
  })
  it('does not display private added payload or provide Task links without task:read', async () => {
    const wrapper = await mountReport(
      {
        ...report,
        report: {
          ...content,
          prompt: 'PRIVATE_PROMPT',
          snapshot: 'PRIVATE_SNAPSHOT',
        } as ExperimentReportContent,
      },
      false,
    )
    expect(wrapper.text()).not.toContain('PRIVATE_')
    expect(wrapper.findAll('a').some((link) => link.attributes('href')?.includes('/tasks/'))).toBe(
      false,
    )
  })
})
describe('creation idempotency', () => {
  it('preserves key for identical failed submissions and generates a new key after editing', async () => {
    const wrapper = mountCreate()
    await fillCreate(wrapper)
    vi.mocked(api.createExperiment).mockRejectedValue(new Error('offline'))
    await button(wrapper, '创建实验')!.trigger('click')
    await flushPromises()
    await button(wrapper, '创建实验')!.trigger('click')
    await flushPromises()
    const first = vi.mocked(api.createExperiment).mock.calls[0]![0]
    expect(vi.mocked(api.createExperiment).mock.calls[1]![0]).toEqual(first)
    await wrapper.get('[aria-label="候选 Prompt 1"]').setValue('编辑后的候选')
    await button(wrapper, '创建实验')!.trigger('click')
    await flushPromises()
    expect(vi.mocked(api.createExperiment).mock.calls[2]![0].clientRequestKey).not.toBe(
      first.clientRequestKey,
    )
    expect(Object.keys(first)).toEqual([
      'spaceId',
      'datasetVersionId',
      'clientRequestKey',
      'candidateVariants',
    ])
  })
  it('rejects reserved keys, duplicate candidates and a newly archived dataset', async () => {
    const wrapper = mountCreate()
    await fillCreate(wrapper)
    await wrapper.get('[aria-label="候选标识 1"]').setValue('BASELINE')
    expect(button(wrapper, '创建实验')!.attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="候选标识 1"]').setValue('candidate')
    await button(wrapper, '添加候选')!.trigger('click')
    await wrapper.get('[aria-label="候选标识 2"]').setValue('candidate')
    await wrapper.get('[aria-label="候选 Prompt 2"]').setValue('第二候选')
    expect(button(wrapper, '创建实验')!.attributes('disabled')).toBeDefined()
    await wrapper.get('[aria-label="候选标识 2"]').setValue('candidate2')
    vi.mocked(api.getDataset).mockResolvedValue({
      id: '41',
      spaceId: '7',
      name: '数据集',
      description: null,
      archived: true,
      createdBy: null,
      createdAt: null,
    })
    await button(wrapper, '创建实验')!.trigger('click')
    await flushPromises()
    expect(api.createExperiment).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('数据集已归档')
  })
})
