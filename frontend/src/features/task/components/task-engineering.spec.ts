import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import TaskEvidencePanel from './TaskEvidencePanel.vue'
import TaskTracePanel from './TaskTracePanel.vue'
import TaskDetailView from '@/views/TaskDetailView.vue'
import * as taskApi from '@/features/task/api/task-api'
import { getEvaluationTaskLink } from '@/features/evaluation/api/evaluation-api'
import type {
  ExecutionArtifact,
  ReplayEligibility,
  TaskTraceView,
  TraceSpan,
} from '../engineering-types'
import type { TaskExecutionDetail, TaskLineageType } from '../types'
import { SPACE_PERMISSIONS, type SpacePermissionCode } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/features/task/api/task-api', () => ({
  getTaskExecutionDetail: vi.fn(),
  getTaskTrace: vi.fn(),
  getTaskArtifacts: vi.fn(),
  getReplayEligibility: vi.fn(),
  runTask: vi.fn(),
  rerunTask: vi.fn(),
  terminateTask: vi.fn(),
}))
vi.mock('@/features/evaluation/api/evaluation-api', () => ({ getEvaluationTaskLink: vi.fn() }))
const wrappers: VueWrapper[] = []
const permissions: SpacePermissionCode[] = [
  SPACE_PERMISSIONS.TASK_READ,
  SPACE_PERMISSIONS.DOCUMENT_READ,
  SPACE_PERMISSIONS.EVALUATION_READ,
  SPACE_PERMISSIONS.EVALUATION_MANAGE,
]
function detail(id = '100'): TaskExecutionDetail {
  return {
    task: {
      id,
      traceId: 'a'.repeat(32),
      parentTaskId: '80',
      rootTaskId: '70',
      lineageType: 'ORIGINAL',
      executionMode: 'LIVE',
      taskNo: 'T-100',
      spaceId: '7',
      agentId: '3',
      documentId: '4',
      documentType: 'FORMAL',
      name: `任务 ${id}`,
      instruction: '检查文档',
      status: 'COMPLETED',
      tokenBudget: 10000,
      readScope: 'FULL',
      focusRegions: [],
      tokensUsed: 1200,
      tokensEstimated: false,
      startTime: '2026-10-03T00:00:00Z',
      dispatchedAt: null,
      lastHeartbeatAt: null,
      endTime: '2026-10-03T00:00:03Z',
      retryCount: 0,
      errorMessage: null,
      resultSummary: '执行完成',
      createdBy: '1',
      createdAt: '2026-10-03T00:00:00Z',
    },
    agentName: '文档 Agent',
    tokensEstimated: false,
    output: null,
    execution: {
      id: '200',
      traceId: 'a'.repeat(32),
      spanId: 'b'.repeat(16),
      workbenchTaskId: id,
      spaceId: '7',
      agentId: '3',
      agentName: '文档 Agent',
      agentConfigVersion: 1,
      maxIterations: 10,
      executionTimeoutSeconds: 300,
      status: 'COMPLETED',
      cancelRequested: false,
      promptHash: 'prompt-hash',
      executionSnapshotHash: 'snapshot-hash',
      executionSnapshotSchemaVersion: 3,
      model: { id: '5', modelKey: 'test-model', displayName: '测试模型', configVersion: 1 },
      skill: {
        configuredMode: 'ALL_BOUND',
        effectiveMode: 'ALL_BOUND',
        instructionHash: null,
        routerModelId: null,
        routerDurationMs: null,
        routerFallbackReason: null,
        routerInputHash: null,
        routerResponseHash: null,
        boundSkills: [],
        selectedSkillVersionIds: [],
      },
      toolDefinitions: [],
      externalMcps: [],
      modelCalls: [],
      toolCalls: [],
      inputTokens: 800,
      inputTokensEstimated: false,
      cachedInputTokens: 0,
      cachedInputTokensEstimated: false,
      outputTokens: 400,
      outputTokensEstimated: false,
      startedAt: '2026-10-03T00:00:00Z',
      finishedAt: '2026-10-03T00:00:03Z',
      createdAt: '2026-10-03T00:00:00Z',
    },
  }
}
function trace(spans: TraceSpan[] = []): TaskTraceView {
  return {
    taskId: '100',
    spaceId: '7',
    traceId: 'a'.repeat(32),
    availabilityCode: 'AVAILABLE',
    partial: false,
    truncated: false,
    startTimeMicros: '1790985600000000',
    endTimeMicros: '1790985600100001',
    durationMicros: 100001,
    spanCount: spans.length,
    errorCount: 0,
    canceledCount: 0,
    retryCount: 0,
    services: [],
    spans,
  }
}
function span(id: string, category: TraceSpan['category'], duration: number): TraceSpan {
  return {
    spanId: id,
    parentSpanId: id === 'root' ? null : 'root',
    name: id,
    service: 'agent-service',
    category,
    kind: 'INTERNAL',
    status: 'OK',
    startTimeMicros: '1790985600000000',
    durationMicros: duration,
    attributes: [],
  }
}
function eligibility(replayable = true): ReplayEligibility {
  return {
    replayable,
    reasonCode: replayable ? null : 'INPUT_SNAPSHOT_MISSING',
    sourceTaskId: '100',
    sourceExecutionId: '200',
    rootTaskId: '70',
    sourceLineage: 'ORIGINAL',
    replayDepth: 0,
    inputSnapshotSchemaVersion: 1,
    inputSnapshotHash: 'input-hash',
    executionSnapshotSchemaVersion: 3,
    executionSnapshotHash: 'snapshot-hash',
  }
}
async function mountPage(
  component: typeof TaskEvidencePanel | typeof TaskTracePanel | typeof TaskDetailView,
  data = detail(),
  allowed = permissions,
  tab = '',
) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspace = useWorkspaceStore()
  workspace.setCurrentSpace('7')
  workspace.setEffectivePermissions({
    spaceId: '7',
    platformSuperAdmin: false,
    role: null,
    permissions: allowed,
  })
  const blank = { template: '<div />' }
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/spaces/:spaceId/tasks/:taskId', component: blank },
      { path: '/spaces/:spaceId/tasks', component: blank },
      { path: '/spaces/:spaceId/agents', name: 'space-agents', component: blank },
      { path: '/spaces/:spaceId/documents/:id', component: blank },
      {
        path: '/spaces/:spaceId/evaluation/test-cases/:testCaseId?',
        name: 'evaluation-test-cases',
        component: blank,
      },
      {
        path: '/spaces/:spaceId/evaluation/runs/:runId',
        name: 'evaluation-runs',
        component: blank,
      },
      {
        path: '/spaces/:spaceId/evaluation/experiments/:experimentId',
        name: 'evaluation-experiments',
        component: blank,
      },
    ],
  })
  await router.push(`/spaces/7/tasks/${data.task.id}${tab ? `?tab=${tab}` : ''}`)
  await router.isReady()
  const wrapper = mount(component, {
    props: component === TaskDetailView ? {} : { detail: data },
    global: { plugins: [pinia, router] },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router, workspace }
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(taskApi.getTaskExecutionDetail).mockResolvedValue(detail())
  vi.mocked(taskApi.getTaskArtifacts).mockResolvedValue([])
  vi.mocked(taskApi.getTaskTrace).mockResolvedValue(trace())
  vi.mocked(taskApi.getReplayEligibility).mockResolvedValue(eligibility())
  vi.mocked(getEvaluationTaskLink).mockResolvedValue(null)
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.useRealTimers()
  vi.restoreAllMocks()
})
describe('execution evidence', () => {
  it.each<[TaskLineageType, string]>([
    ['ORIGINAL', '首次执行'],
    ['RERUN', '重新执行'],
    ['REVIEW_REWORK', '审批返工'],
    ['REPLAY', '冻结回放'],
    ['EXPERIMENT', '实验执行'],
    ['LEGACY_UNKNOWN', '历史未知'],
  ])('shows %s without inventing evaluation relations', async (lineageType, label) => {
    const data = detail()
    data.task.lineageType = lineageType
    if (lineageType === 'REPLAY' || lineageType === 'EXPERIMENT')
      data.task.executionMode = 'ISOLATED'
    const { wrapper } = await mountPage(TaskEvidencePanel, data)
    expect(wrapper.text()).toContain(label)
    expect(wrapper.text()).toContain(data.task.executionMode)
    expect(wrapper.text()).toContain('snapshot-hash')
    expect(taskApi.getTaskArtifacts).toHaveBeenCalledTimes(
      data.task.executionMode === 'ISOLATED' ? 1 : 0,
    )
    expect(
      wrapper.findAll('a').some((link) => link.attributes('href')?.includes('/evaluation/')),
    ).toBe(false)
  })
  it('loads isolated artifacts as metadata and structural summaries with no apply operation', async () => {
    const data = detail()
    data.task.executionMode = 'ISOLATED'
    data.task.lineageType = 'REPLAY'
    const artifact: ExecutionArtifact = {
      id: '1',
      taskId: '100',
      executionId: '200',
      sourceTaskId: '80',
      sequenceNo: 1,
      sourceToolCallId: null,
      artifactType: 'DRAFT_CHANGES',
      schemaVersion: 1,
      payloadSha256: 'artifact-hash',
      payloadJson: '{"proposal":{"changes":[{"newText":"PRIVATE_BODY"}]},"token":"SECRET"}',
      createdAt: '2026-10-03',
    }
    vi.mocked(taskApi.getTaskArtifacts).mockResolvedValue([artifact])
    const { wrapper } = await mountPage(TaskEvidencePanel, data)
    expect(wrapper.text()).toContain('ISOLATED')
    expect(wrapper.text()).toContain('artifact-hash')
    expect(wrapper.text()).toContain('1 处候选变更')
    expect(wrapper.text()).not.toContain('PRIVATE_BODY')
    expect(wrapper.text()).not.toContain('SECRET')
    expect(wrapper.findAll('button').some((button) => button.text().includes('应用'))).toBe(false)
    await wrapper.setProps({ detail: structuredClone(data) })
    await flushPromises()
    expect(taskApi.getTaskArtifacts).toHaveBeenCalledTimes(1)
  })
  it('returns to formal frozen case/run/experiment IDs instead of lineage root and obeys resource read permissions', async () => {
    const data = detail()
    data.task.lineageType = 'EXPERIMENT'
    vi.mocked(getEvaluationTaskLink).mockResolvedValue({
      taskId: '100',
      spaceId: '7',
      sourceTaskId: '81',
      testCaseId: '50',
      testCaseVersionId: '51',
      caseRunId: '31',
      caseAttemptId: '32',
      runId: '10',
      experimentId: '60',
      variantKey: 'candidate',
    })
    const { wrapper } = await mountPage(
      TaskEvidencePanel,
      data,
      permissions.filter((permission) => permission !== SPACE_PERMISSIONS.DOCUMENT_READ),
    )
    const hrefs = wrapper.findAll('a').map((link) => link.attributes('href'))
    expect(hrefs).toContain('/spaces/7/evaluation/test-cases/50?versionId=51')
    expect(hrefs).toContain('/spaces/7/evaluation/runs/10?caseRunId=31&attemptId=32')
    expect(hrefs).toContain('/spaces/7/evaluation/experiments/60')
    expect(hrefs).not.toContain('/spaces/7/agents?agentId=3')
    expect(hrefs).not.toContain('/spaces/7/documents/4')
  })
  it('does not request evaluation links without read permission and rejects cross-space responses', async () => {
    const data = detail()
    data.task.lineageType = 'REPLAY'
    const first = await mountPage(TaskEvidencePanel, data, [SPACE_PERMISSIONS.TASK_READ])
    expect(getEvaluationTaskLink).not.toHaveBeenCalled()
    expect(first.wrapper.text()).toContain('评估读取权限')
    vi.mocked(getEvaluationTaskLink).mockResolvedValue({ taskId: '100', spaceId: '8' } as never)
    const second = await mountPage(TaskEvidencePanel, data)
    expect(second.wrapper.text()).toContain('评估关联身份不一致')
    expect(
      second.wrapper.findAll('a').some((link) => link.attributes('href')?.includes('/evaluation/')),
    ).toBe(false)
  })
  it('rejects artifacts belonging to another execution', async () => {
    const data = detail()
    data.task.executionMode = 'ISOLATED'
    vi.mocked(taskApi.getTaskArtifacts).mockResolvedValue([
      { taskId: '100', executionId: '999' } as ExecutionArtifact,
    ])
    const { wrapper } = await mountPage(TaskEvidencePanel, data)
    expect(wrapper.text()).toContain('产物关联身份不一致')
  })
})
describe('technical Trace and authoritative facts', () => {
  it('clears old technical success data after a failed refresh while keeping business facts', async () => {
    vi.mocked(taskApi.getTaskTrace).mockResolvedValue(trace([span('root', 'AGENT', 100000)]))
    const { wrapper } = await mountPage(TaskTracePanel)
    expect(wrapper.find('.span-detail').text()).toContain('root')
    vi.mocked(taskApi.getTaskTrace).mockRejectedValue(new Error('刷新失败'))
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.span-detail').text()).not.toContain('root')
    expect(wrapper.find('.trace-facts').text()).toContain('1200')
  })
  it('renders microsecond bars and keeps error/cancel/retry and critical nodes visible while folding other technical spans', async () => {
    const nodes = [
      span('root', 'AGENT', 100000),
      span('critical', 'DATABASE', 80000),
      span('folded', 'REDIS', 1000),
      { ...span('error', 'HTTP', 2000), status: 'ERROR' as const },
      { ...span('cancel', 'HTTP', 3000), status: 'CANCELED' as const },
      {
        ...span('retry', 'TECHNICAL', 1000),
        attributes: [{ key: 'agentdoc.retry.count', valueType: 'LONG' as const, value: '1' }],
      },
    ]
    nodes[0]!.attributes = [
      { key: 'run.id', valueType: 'LONG', value: '100' },
      { key: 'db.statement', valueType: 'STRING', value: 'SECRET_SQL' },
    ]
    vi.mocked(taskApi.getTaskTrace).mockResolvedValue({
      ...trace(nodes),
      partial: true,
      truncated: true,
      errorCount: 1,
      canceledCount: 1,
      retryCount: 1,
    })
    const { wrapper } = await mountPage(TaskTracePanel)
    const rows = wrapper.findAll('.span-row')
    expect(rows.map((row) => row.text()).join(' ')).toContain('critical')
    expect(rows.map((row) => row.text()).join(' ')).toContain('error')
    expect(rows.map((row) => row.text()).join(' ')).toContain('cancel')
    expect(rows.map((row) => row.text()).join(' ')).toContain('retry')
    expect(rows.map((row) => row.text()).join(' ')).not.toContain('folded')
    expect(wrapper.text()).toContain('Trace 已截断')
    expect(wrapper.text()).toContain('run.id')
    expect(wrapper.text()).not.toContain('SECRET_SQL')
    expect(wrapper.find('.span-bar').attributes('style')).toContain('left: 0%')
    expect(wrapper.findAll('a')).toHaveLength(0)
    await wrapper.findAll('.trace-filters button')[1]!.trigger('click')
    expect(wrapper.findAll('.span-row')).toHaveLength(2)
  })
  it.each([
    'NO_TRACE',
    'BACKEND_UNAVAILABLE',
    'RETENTION_WINDOW_ELAPSED',
    'NOT_FOUND_OR_NOT_SAMPLED',
  ] as const)(
    'keeps unavailable statistics unknown and business facts visible for %s',
    async (availabilityCode) => {
      vi.mocked(taskApi.getTaskTrace).mockResolvedValue({
        ...trace(),
        availabilityCode,
        durationMicros: null,
        spanCount: null,
        errorCount: null,
        canceledCount: null,
        retryCount: null,
      })
      const { wrapper } = await mountPage(TaskTracePanel)
      expect(wrapper.find('.trace-summary').text()).toContain('不可用 / 不可用 / 不可用')
      expect(wrapper.find('.trace-facts').text()).toContain('1200')
      expect(wrapper.find('.trace-facts').text()).toContain('snapshot-hash')
      expect(wrapper.findAll('.span-row')).toHaveLength(0)
    },
  )
  it('retains authoritative facts when HTTP fails and rejects foreign trace identities', async () => {
    vi.mocked(taskApi.getTaskTrace).mockRejectedValueOnce(new Error('查询失败'))
    const { wrapper } = await mountPage(TaskTracePanel)
    expect(wrapper.text()).toContain('查询失败')
    expect(wrapper.find('.trace-facts').text()).toContain('1200')
    vi.mocked(taskApi.getTaskTrace).mockResolvedValue({ ...trace(), spaceId: '8' })
    await wrapper.find('button').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Trace 关联身份不一致')
  })
})
describe('Task detail integration', () => {
  it('shows isolated candidates without claiming a document write even when the legacy output projection returns a draft', async () => {
    const isolated = detail()
    isolated.task.executionMode = 'ISOLATED'
    isolated.task.lineageType = 'REPLAY'
    isolated.output = { type: 'DRAFT_DOCUMENT', id: '4', documentId: '4', status: null }
    vi.mocked(taskApi.getTaskExecutionDetail).mockResolvedValue(isolated)
    const { wrapper } = await mountPage(TaskDetailView, isolated, [
      ...permissions,
      SPACE_PERMISSIONS.DOCUMENT_READ,
    ])
    expect(wrapper.text()).toContain('隔离候选产物')
    expect(wrapper.text()).not.toContain('已更新草稿文档')
    expect(wrapper.text()).not.toContain('打开草稿文档')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '查看候选产物')!
      .trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Artifact 只读预览')
  })
  it('does not show a change request action for a draft output when document read is missing', async () => {
    const data = detail()
    data.output = { type: 'DRAFT_DOCUMENT', id: '4', documentId: '4', status: null }
    vi.mocked(taskApi.getTaskExecutionDetail).mockResolvedValue(data)
    const { wrapper } = await mountPage(TaskDetailView, data, [
      ...permissions.filter((permission) => permission !== SPACE_PERMISSIONS.DOCUMENT_READ),
      SPACE_PERMISSIONS.CHANGE_REQUEST_READ,
    ])
    expect(wrapper.text()).not.toContain('打开草稿文档')
    expect(wrapper.text()).not.toContain('查看变更审批')
  })
  it('restarts an initial load interrupted by hiding the tab', async () => {
    let hidden = false
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden)
    vi.mocked(taskApi.getTaskExecutionDetail).mockReturnValueOnce(new Promise(() => {}))
    const { wrapper } = await mountPage(TaskDetailView)
    const signal = vi.mocked(taskApi.getTaskExecutionDetail).mock.calls[0]![1]!
    hidden = true
    document.dispatchEvent(new Event('visibilitychange'))
    expect(signal.aborted).toBe(true)
    hidden = false
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(taskApi.getTaskExecutionDetail).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('任务 100')
  })
  it('offers seeding to a catalog manager without run/create permission, rechecks eligibility on click and preserves source identity', async () => {
    const { wrapper, router } = await mountPage(TaskDetailView)
    const seed = wrapper.findAll('button').find((button) => button.text() === '沉淀为测试用例')!
    expect(seed.attributes('disabled')).toBeUndefined()
    await seed.trigger('click')
    await flushPromises()
    expect(taskApi.getReplayEligibility).toHaveBeenCalledTimes(2)
    expect(router.currentRoute.value.query.sourceTaskId).toBe('100')
    expect(taskApi.runTask).not.toHaveBeenCalled()
  })
  it('keeps seeding disabled when a catalog manager cannot read the frozen document', async () => {
    const { wrapper } = await mountPage(TaskDetailView, detail(), [
      SPACE_PERMISSIONS.TASK_READ,
      SPACE_PERMISSIONS.EVALUATION_READ,
      SPACE_PERMISSIONS.EVALUATION_MANAGE,
    ])
    const seed = wrapper.findAll('button').find((button) => button.text() === '沉淀为测试用例')!
    expect(seed.attributes('disabled')).toBeDefined()
    expect(wrapper.text()).toContain('需要文档读取权限才能核验冻结输入。')
    expect(taskApi.getReplayEligibility).not.toHaveBeenCalled()
  })
  it('blocks changed eligibility and hides seeding for isolated tasks or missing manage', async () => {
    const { wrapper, router } = await mountPage(TaskDetailView)
    vi.mocked(taskApi.getReplayEligibility).mockResolvedValue(eligibility(false))
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '沉淀为测试用例')!
      .trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/spaces/7/tasks/100')
    expect(wrapper.text()).toContain('INPUT_SNAPSHOT_MISSING')
    const isolated = detail()
    isolated.task.executionMode = 'ISOLATED'
    vi.mocked(taskApi.getTaskExecutionDetail).mockResolvedValue(isolated)
    const second = await mountPage(TaskDetailView)
    expect(second.wrapper.text()).not.toContain('沉淀为测试用例')
    vi.mocked(taskApi.getTaskExecutionDetail).mockResolvedValue(detail())
    const third = await mountPage(TaskDetailView, detail(), [SPACE_PERMISSIONS.TASK_READ])
    expect(third.wrapper.text()).not.toContain('沉淀为测试用例')
  })
  it('cancels late requests when navigating task identities and reloads the same component', async () => {
    let oldResolve!: (value: TaskExecutionDetail) => void
    vi.mocked(taskApi.getTaskExecutionDetail).mockReturnValueOnce(
      new Promise((resolve) => {
        oldResolve = resolve
      }),
    )
    const { wrapper, router } = await mountPage(TaskDetailView)
    const oldSignal = vi.mocked(taskApi.getTaskExecutionDetail).mock.calls[0]![1]!
    vi.mocked(taskApi.getTaskExecutionDetail).mockResolvedValue(detail('101'))
    await router.push('/spaces/7/tasks/101')
    await flushPromises()
    expect(oldSignal.aborted).toBe(true)
    oldResolve(detail())
    await flushPromises()
    expect(wrapper.text()).toContain('任务 101')
    expect(wrapper.text()).not.toContain('任务 100')
  })
  it('pauses hidden polling, refreshes on visibility, backs off failures and stops at terminal state', async () => {
    vi.useFakeTimers()
    let hidden = false
    vi.spyOn(document, 'hidden', 'get').mockImplementation(() => hidden)
    const running = detail()
    running.task.status = 'RUNNING'
    running.task.endTime = null
    vi.mocked(taskApi.getTaskExecutionDetail).mockResolvedValue(running)
    const { wrapper } = await mountPage(TaskDetailView)
    hidden = true
    document.dispatchEvent(new Event('visibilitychange'))
    await vi.advanceTimersByTimeAsync(30000)
    expect(taskApi.getTaskExecutionDetail).toHaveBeenCalledTimes(1)
    hidden = false
    document.dispatchEvent(new Event('visibilitychange'))
    await flushPromises()
    expect(taskApi.getTaskExecutionDetail).toHaveBeenCalledTimes(2)
    vi.mocked(taskApi.getTaskExecutionDetail).mockRejectedValueOnce(new Error('断网'))
    await vi.advanceTimersByTimeAsync(3000)
    await flushPromises()
    expect(wrapper.text()).toContain('保留上次业务记录')
    await vi.advanceTimersByTimeAsync(4999)
    expect(taskApi.getTaskExecutionDetail).toHaveBeenCalledTimes(3)
    vi.mocked(taskApi.getTaskExecutionDetail).mockResolvedValue(detail())
    await vi.advanceTimersByTimeAsync(1)
    await flushPromises()
    expect(taskApi.getTaskExecutionDetail).toHaveBeenCalledTimes(4)
    await vi.advanceTimersByTimeAsync(30000)
    expect(taskApi.getTaskExecutionDetail).toHaveBeenCalledTimes(4)
  })
  it('keeps business audit accessible in the Trace tab even when telemetry fails', async () => {
    vi.mocked(taskApi.getTaskTrace).mockRejectedValue(new Error('遥测后端错误'))
    const { wrapper } = await mountPage(TaskDetailView, detail(), permissions, 'trace')
    expect(wrapper.text()).toContain('业务调用审计')
    expect(wrapper.text()).toContain('遥测后端错误')
    expect(wrapper.text()).toContain('执行上下文已冻结')
  })
})
