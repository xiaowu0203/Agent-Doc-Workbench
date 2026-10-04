import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ElMessageBox } from 'element-plus'
import EvaluationCatalogView from '@/views/EvaluationCatalogView.vue'
import TaskSourcePicker from './TaskSourcePicker.vue'
import PublishedVersionPicker from './PublishedVersionPicker.vue'
import CatalogBindings from './CatalogBindings.vue'
import JsonConfigEditor from './JsonConfigEditor.vue'
import * as api from '../api/evaluation-api'
import * as taskApi from '@/features/task/api/task-api'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import type { EvaluationTestCase, Evaluator, EvaluatorVersion } from '../types'
import type { TaskDetail, TaskListItem } from '@/features/task/types'
import type { ReplayEligibility } from '@/features/task/engineering-types'

vi.mock('../api/evaluation-api')
vi.mock('@/features/task/api/task-api')
const wrappers: VueWrapper[] = []
const evaluator = {
  id: '20',
  spaceId: '7',
  name: '终态检查',
  description: null,
  archived: false,
  evaluatorKey: 'task-terminal-status',
} as Evaluator
const draft = {
  id: '30',
  evaluatorId: '20',
  spaceId: '7',
  versionNo: 1,
  status: 'DRAFT',
  configSchemaVersion: 1,
  configJson: '{}',
  resultSchemaVersion: 1,
  evaluatorKey: 'task-terminal-status',
  implementationVersion: 'v1',
  contentHash: null,
  publishedAt: null,
} as EvaluatorVersion
const source = {
  id: '100',
  spaceId: '7',
  name: '来源任务',
  taskNo: 'T-100',
  status: 'COMPLETED',
  executionMode: 'LIVE',
  lineageType: 'ORIGINAL',
  agentId: '3',
  agentName: 'Agent',
  traceId: null,
  parentTaskId: null,
  rootTaskId: '100',
  documentId: '4',
  documentType: 'FORMAL',
  instruction: '检查',
  tokenBudget: null,
  readScope: 'FULL',
  focusRegions: [],
  tokensUsed: null,
  tokensEstimated: false,
  startTime: null,
  dispatchedAt: null,
  lastHeartbeatAt: null,
  endTime: null,
  retryCount: 0,
  errorMessage: null,
  resultSummary: null,
  createdBy: null,
  createdAt: '',
} satisfies TaskDetail & { agentName: string }
const eligibility = {
  sourceTaskId: '100',
  sourceExecutionId: '101',
  replayable: true,
  reasonCode: null,
  inputSnapshotHash: 'input',
  executionSnapshotHash: 'execution',
} as ReplayEligibility
const page = <T>(records: T[], total = records.length) => ({
  records,
  total,
  pageNum: 1,
  pageSize: 10,
})
const button = (wrapper: VueWrapper, text: string) =>
  wrapper.findAll('button').find((item) => item.text() === text)!

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
      SPACE_PERMISSIONS.EVALUATION_MANAGE,
      SPACE_PERMISSIONS.TASK_READ,
      SPACE_PERMISSIONS.DOCUMENT_READ,
    ],
  })
  vi.mocked(api.searchEvaluators).mockResolvedValue(page([evaluator]))
  vi.mocked(api.getEvaluator).mockResolvedValue(evaluator)
  vi.mocked(api.searchEvaluatorVersions).mockResolvedValue(page([draft]))
  vi.mocked(api.getEvaluatorVersion).mockResolvedValue(draft)
  vi.mocked(api.getTestCaseEvaluators).mockResolvedValue([])
  vi.mocked(taskApi.searchTasks).mockResolvedValue(
    page([{ ...source, documentTitle: null, creatorName: null }] satisfies TaskListItem[]),
  )
  vi.mocked(taskApi.getTask).mockResolvedValue(source)
  vi.mocked(taskApi.getReplayEligibility).mockResolvedValue(eligibility)
  vi.spyOn(ElMessageBox, 'confirm').mockResolvedValue(
    'confirm' as Awaited<ReturnType<typeof ElMessageBox.confirm>>,
  )
})
afterEach(() => {
  wrappers.splice(0).forEach((wrapper) => wrapper.unmount())
  vi.restoreAllMocks()
})

async function catalog(detail = true, query = '') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/spaces/:spaceId/evaluation/evaluators/:id?', component: { template: '<div />' } },
    ],
  })
  await router.push(`/spaces/7/evaluation/evaluators${detail ? '/20' : ''}${query}`)
  const wrapper = mount(EvaluationCatalogView, {
    props: { section: 'evaluators', resourceId: detail ? '20' : undefined },
    global: { plugins: [router], stubs: { ElDialog: true } },
  })
  wrappers.push(wrapper)
  await flushPromises()
  return { wrapper, router }
}

describe('catalog editing and frozen contracts', () => {
  it('queries one resource page without per-row version requests', async () => {
    const { wrapper } = await catalog(false)
    expect(api.searchEvaluators).toHaveBeenCalledWith(
      { spaceId: '7', archived: false, keyword: undefined, pageNum: 1, pageSize: 20 },
      expect.any(AbortSignal),
    )
    expect(api.searchEvaluatorVersions).not.toHaveBeenCalled()
    await wrapper.get('input[aria-label="搜索评估器"]').setValue('终态')
    await wrapper.get('form').trigger('submit')
    expect(api.searchEvaluators).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: '终态', pageNum: 1 }),
      expect.any(AbortSignal),
    )
  })
  it('rejects resources returned from another space', async () => {
    vi.mocked(api.searchEvaluators).mockResolvedValue(page([{ ...evaluator, spaceId: '8' }]))
    const { wrapper } = await catalog(false)
    expect(wrapper.text()).toContain('资源空间归属不一致')
    expect(wrapper.text()).not.toContain('终态检查')
  })
  it.each(['PUBLISHED', 'ARCHIVED'] as const)(
    'keeps %s configuration read-only',
    async (status) => {
      vi.mocked(api.getEvaluatorVersion).mockResolvedValue({
        ...draft,
        status,
        contentHash: 'frozen',
      })
      const { wrapper } = await catalog()
      expect(wrapper.get('textarea[aria-label="configJson"]').attributes()).toHaveProperty(
        'readonly',
      )
      expect(button(wrapper, '保存草稿配置')).toBeUndefined()
      expect(button(wrapper, '核对并发布')).toBeUndefined()
      expect(wrapper.text()).toContain('frozen')
    },
  )
  it('allows readers to view a draft without edit or archive actions', async () => {
    useWorkspaceStore().currentPermissions!.permissions = [SPACE_PERMISSIONS.EVALUATION_READ]
    const { wrapper } = await catalog()
    expect(wrapper.get('textarea').attributes()).toHaveProperty('readonly')
    expect(button(wrapper, '新建草稿版本')).toBeUndefined()
    expect(button(wrapper, '归档资源')).toBeUndefined()
  })
  it('retains the evaluator parent archive rule and disallows draft changes', async () => {
    vi.mocked(api.getEvaluator).mockResolvedValue({ ...evaluator, archived: true })
    const { wrapper } = await catalog()
    expect(wrapper.text()).toContain('已发布用例的既有 PUBLISHED 评估器绑定仍可使用')
    expect(button(wrapper, '新建草稿版本')).toBeUndefined()
    expect(wrapper.get('textarea').attributes()).toHaveProperty('readonly')
  })
  it('validates JSON before saving, then sends only editable fields', async () => {
    const { wrapper } = await catalog()
    await wrapper.get('textarea').setValue('{broken')
    await button(wrapper, '保存草稿配置').trigger('click')
    await flushPromises()
    expect(api.updateEvaluatorVersion).not.toHaveBeenCalled()
    vi.mocked(api.updateEvaluatorVersion).mockResolvedValue({
      ...draft,
      configJson: '{\n  "requiredStatus": "COMPLETED"\n}',
    })
    await wrapper.get('textarea').setValue('{"requiredStatus":"COMPLETED"}')
    await button(wrapper, '保存草稿配置').trigger('click')
    await flushPromises()
    expect(api.updateEvaluatorVersion).toHaveBeenCalledWith(
      '30',
      {
        configSchemaVersion: 1,
        configJson: '{\n  "requiredStatus": "COMPLETED"\n}',
        resultSchemaVersion: 1,
      },
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).toContain('草稿可配置')
  })
  it('blocks publication while configuration is dirty', async () => {
    const { wrapper } = await catalog()
    await wrapper.get('textarea').setValue('{"x":1}')
    await button(wrapper, '核对并发布').trigger('click')
    expect(api.publishEvaluatorVersion).not.toHaveBeenCalled()
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请先保存草稿配置和全部绑定')
  })
  it('requires archive confirmation and respects cancellation', async () => {
    vi.mocked(ElMessageBox.confirm).mockRejectedValue('cancel')
    const { wrapper } = await catalog()
    await button(wrapper, '归档资源').trigger('click')
    await flushPromises()
    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(api.archiveEvaluator).not.toHaveBeenCalled()
  })
  it('reads a deep-linked version outside the first page and rejects another parent', async () => {
    vi.mocked(api.getEvaluatorVersion).mockResolvedValue({ ...draft, id: '50', evaluatorId: '21' })
    const { wrapper } = await catalog(true, '?versionId=50')
    expect(api.getEvaluatorVersion).toHaveBeenCalledWith('50', expect.any(AbortSignal))
    expect(wrapper.text()).toContain('版本不属于当前资源')
    expect(wrapper.find('textarea').exists()).toBe(false)
  })
  it('aborts a pending write when the route changes and ignores its late response', async () => {
    let resolve!: (version: EvaluatorVersion) => void
    vi.mocked(api.updateEvaluatorVersion).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const { wrapper, router } = await catalog()
    await button(wrapper, '保存草稿配置').trigger('click')
    const signal = vi.mocked(api.updateEvaluatorVersion).mock.calls[0]![2]!
    await router.push('/spaces/8/evaluation/evaluators/20')
    await flushPromises()
    expect(signal.aborted).toBe(true)
    resolve(draft)
    await flushPromises()
    expect(wrapper.find('textarea').exists()).toBe(false)
  })
})

describe('paged source and binding selectors', () => {
  it('does not create a case version after navigating away during the final source check', async () => {
    let resolve!: (task: TaskDetail) => void
    vi.mocked(api.getTestCase).mockResolvedValue({
      id: '20',
      spaceId: '7',
      name: '用例',
      archived: false,
    } as EvaluationTestCase)
    vi.mocked(api.searchTestCaseVersions).mockResolvedValue(page([]))
    vi.mocked(taskApi.getTask)
      .mockResolvedValueOnce(source)
      .mockReturnValueOnce(
        new Promise((done) => {
          resolve = done
        }),
      )
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/spaces/:spaceId/evaluation/test-cases/:id', component: { template: '<div />' } },
      ],
    })
    await router.push('/spaces/7/evaluation/test-cases/20')
    const wrapper = mount(EvaluationCatalogView, {
      props: { section: 'test-cases', resourceId: '20' },
      global: {
        plugins: [router],
        stubs: {
          ElDialog: {
            props: ['modelValue'],
            template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
          },
        },
      },
    })
    wrappers.push(wrapper)
    await flushPromises()
    await button(wrapper, '新建草稿版本').trigger('click')
    await flushPromises()
    await wrapper.get('.source-picker__list button').trigger('click')
    await flushPromises()
    await button(wrapper, '创建草稿版本').trigger('click')
    const signal = vi.mocked(taskApi.getTask).mock.calls[1]![1]!
    await router.push('/spaces/8/evaluation/test-cases/20')
    await flushPromises()
    resolve(source)
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(api.createTestCaseVersion).not.toHaveBeenCalled()
    expect(taskApi.getReplayEligibility).toHaveBeenCalledTimes(1)
  })
  it('loads completed LIVE sources and checks only the selected task', async () => {
    const wrapper = mount(TaskSourcePicker, { props: { spaceId: '7' } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(taskApi.searchTasks).toHaveBeenCalledWith(
      expect.objectContaining({
        spaceId: '7',
        executionMode: 'LIVE',
        status: 'COMPLETED',
        pageSize: 10,
      }),
      expect.any(AbortSignal),
    )
    expect(taskApi.getReplayEligibility).not.toHaveBeenCalled()
    await wrapper.get('.source-picker__list button').trigger('click')
    await flushPromises()
    expect(wrapper.emitted('select')!.at(-1)).toEqual(['100'])
    expect(wrapper.text()).toContain('execution')
  })
  it('cancels source qualification on a space change', async () => {
    let resolve!: (task: TaskDetail) => void
    vi.mocked(taskApi.getTask).mockReturnValue(
      new Promise((done) => {
        resolve = done
      }),
    )
    const wrapper = mount(TaskSourcePicker, { props: { spaceId: '7', initialTaskId: '100' } })
    wrappers.push(wrapper)
    await flushPromises()
    const signal = vi.mocked(taskApi.getTask).mock.calls[0]![1]!
    await wrapper.setProps({ spaceId: '8' })
    resolve(source)
    await flushPromises()
    expect(signal.aborted).toBe(true)
    expect(taskApi.getReplayEligibility).not.toHaveBeenCalled()
    expect(wrapper.emitted('select')!.every(([value]) => value === null)).toBe(true)
  })
  it('renders stable eligibility reasons without selecting an invalid source', async () => {
    vi.mocked(taskApi.getReplayEligibility).mockResolvedValue({
      ...eligibility,
      replayable: false,
      reasonCode: 'INPUT_SNAPSHOT_MISSING',
    })
    const wrapper = mount(TaskSourcePicker, { props: { spaceId: '7', initialTaskId: '100' } })
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.text()).toContain('快照')
    expect(wrapper.emitted('select')!.every(([value]) => value === null)).toBe(true)
  })
  it('loads published versions only after selecting a parent and disables duplicates', async () => {
    vi.mocked(api.searchEvaluatorVersions).mockResolvedValue(
      page([{ ...draft, status: 'PUBLISHED', contentHash: 'hash' }]),
    )
    const wrapper = mount(PublishedVersionPicker, {
      props: { open: true, spaceId: '7', kind: 'evaluators', excludeIds: ['30'] },
      global: { stubs: { ElDialog: { template: '<div><slot /><slot name="footer" /></div>' } } },
    })
    wrappers.push(wrapper)
    await flushPromises()
    expect(api.searchEvaluatorVersions).not.toHaveBeenCalled()
    await wrapper.get('.picker-resources button').trigger('click')
    await flushPromises()
    expect(api.searchEvaluatorVersions).toHaveBeenCalledWith(
      expect.objectContaining({ spaceId: '7', parentId: '20', status: 'PUBLISHED', pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(button(wrapper, '已绑定').attributes()).toHaveProperty('disabled')
  })
  it('saves complete dataset bindings with reordered entries and disabled cases', async () => {
    vi.mocked(api.getDatasetCases).mockResolvedValue([
      {
        id: '1',
        testCaseVersionId: '31',
        testCaseId: '21',
        testCaseName: 'A',
        versionNo: 1,
        status: 'PUBLISHED',
        sortOrder: 0,
        enabled: false,
      },
      {
        id: '2',
        testCaseVersionId: '32',
        testCaseId: '22',
        testCaseName: 'B',
        versionNo: 1,
        status: 'PUBLISHED',
        sortOrder: 1,
        enabled: true,
      },
    ])
    const wrapper = mount(CatalogBindings, {
      props: { kind: 'datasets', spaceId: '7', versionId: '40', editable: true },
      global: { stubs: { RouterLink: true } },
    })
    wrappers.push(wrapper)
    await flushPromises()
    await button(wrapper, '下移').trigger('click')
    await button(wrapper, '保存全部绑定').trigger('click')
    await flushPromises()
    expect(api.replaceDatasetCases).toHaveBeenCalledWith(
      '40',
      {
        cases: [
          { testCaseVersionId: '32', sortOrder: 0, enabled: true },
          { testCaseVersionId: '31', sortOrder: 1, enabled: false },
        ],
      },
      expect.any(AbortSignal),
    )
  })
  it('does not permit mutation of a frozen test case binding', async () => {
    vi.mocked(api.getTestCaseEvaluators).mockResolvedValue([
      {
        id: '1',
        evaluatorVersionId: '30',
        evaluatorId: '20',
        evaluatorName: '终态检查',
        evaluatorKey: 'task-terminal-status',
        versionNo: 1,
        status: 'PUBLISHED',
        sortOrder: 0,
        expectedJson: '{"status":"COMPLETED"}',
      },
    ])
    const wrapper = mount(CatalogBindings, {
      props: { kind: 'test-cases', spaceId: '7', versionId: '40', editable: false },
      global: { stubs: { RouterLink: true } },
    })
    wrappers.push(wrapper)
    await flushPromises()
    expect(wrapper.get('textarea').attributes()).toHaveProperty('readonly')
    expect(button(wrapper, '保存全部绑定')).toBeUndefined()
    expect(api.replaceTestCaseEvaluators).not.toHaveBeenCalled()
  })
  it('formats JSON as text and reports invalid syntax', async () => {
    const wrapper = mount(JsonConfigEditor, {
      props: { modelValue: '{broken', label: 'expectedJson' },
    })
    wrappers.push(wrapper)
    await button(wrapper, '校验并格式化').trigger('click')
    expect(wrapper.text()).toContain('JSON 语法无效')
    await wrapper.setProps({ modelValue: '{"text":"<script>"}' })
    await button(wrapper, '校验并格式化').trigger('click')
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.emitted('update:modelValue')!.at(-1)).toEqual(['{\n  "text": "<script>"\n}'])
  })
})
