import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, expect, it, vi } from 'vitest'
import TaskOnlineAssociation from './TaskOnlineAssociation.vue'
import { getOnlineExperiment, searchOnlineAssignments } from '@/features/evaluation/online/api'
import { experiment } from '@/features/evaluation/online/fixtures.spec-support'
import type { OnlineAssignment } from '@/features/evaluation/online/types'
import type { TaskDetail } from '../types'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('@/features/evaluation/online/api', () => ({
  getOnlineExperiment: vi.fn(),
  searchOnlineAssignments: vi.fn(),
}))
const task: TaskDetail = {
  id: '1011',
  spaceId: '7',
  agentId: '3',
  documentId: '101',
  onlineExperimentId: experiment.id,
  onlineAssignmentId: '9007199254740993',
  onlineBindingSchemaVersion: 2,
  onlineBindingHash: 'e'.repeat(64),
  traceId: null,
  parentTaskId: null,
  rootTaskId: '1011',
  lineageType: 'ORIGINAL',
  executionMode: 'LIVE',
  taskNo: 'T-1011',
  documentType: 'FORMAL',
  name: '任务',
  instruction: '原始目标',
  status: 'COMPLETED',
  tokenBudget: null,
  readScope: 'FULL',
  focusRegions: [],
  tokensUsed: 0,
  tokensEstimated: false,
  startTime: null,
  dispatchedAt: null,
  lastHeartbeatAt: null,
  endTime: null,
  retryCount: 0,
  errorMessage: null,
  resultSummary: null,
  createdBy: '1',
  createdAt: '2026-10-12T00:00:00Z',
}
const assignment: OnlineAssignment = {
  id: task.onlineAssignmentId!,
  experimentId: experiment.id,
  spaceId: '7',
  taskId: task.id as string,
  documentId: '101',
  variant: 'CANDIDATE',
  acceptedSequence: '1',
  reservedTokenBudget: '1000',
  consumedTokens: '0',
  taskConfirmationStatus: 'CONFIRMED',
  dispatchStatus: 'COMPLETED',
  slotStatus: 'RELEASED',
  cancelStatus: null,
  settlementStatus: 'SETTLED',
  executionId: '2011',
  reasonCode: null,
  factStatus: null,
  createdAt: null,
}
beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(getOnlineExperiment).mockResolvedValue({
    ...experiment,
    status: 'STOPPED',
    baseline: { id: '501', hash: 'b'.repeat(64), nonPromptHash: 'd'.repeat(64), schemaVersion: 2 },
    candidate: { id: '502', hash: 'c'.repeat(64), nonPromptHash: 'd'.repeat(64), schemaVersion: 2 },
  })
  vi.mocked(searchOnlineAssignments).mockResolvedValue({
    records: [assignment],
    total: 1,
    pageNum: 1,
    pageSize: 1,
  })
})
async function setup(value = task, permissions: string[] = Object.values(SPACE_PERMISSIONS)) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspace = useWorkspaceStore()
  workspace.setCurrentSpace('7')
  workspace.setEffectivePermissions({
    spaceId: '7',
    platformSuperAdmin: false,
    role: null,
    permissions,
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: { template: '<div />' } },
      {
        path: '/spaces/:spaceId/evaluation/online-experiments/:id',
        component: { template: '<div />' },
      },
    ],
  })
  await router.push('/')
  const wrapper = mount(TaskOnlineAssociation, {
    props: { task: value },
    global: { plugins: [pinia, router] },
  })
  await flushPromises()
  return { wrapper, workspace }
}

it('reads the actual assignment and frozen candidate template with exact identities', async () => {
  const { wrapper } = await setup()
  expect(searchOnlineAssignments).toHaveBeenCalledWith(
    experiment.id,
    { taskId: '1011', pageNum: 1, pageSize: 1 },
    expect.any(AbortSignal),
  )
  expect(wrapper.text()).toContain('Candidate（冻结候选）')
  expect(wrapper.text()).toContain('502')
  expect(wrapper.text()).toContain('c'.repeat(64))
  expect(wrapper.text()).toContain('评审时不能据此声明 BLINDED')
  expect(wrapper.get('a').attributes('href')).toBe(
    `/spaces/7/evaluation/online-experiments/${experiment.id}`,
  )
  wrapper.unmount()
})

it('does not infer baseline for normal tasks or make extra reads', async () => {
  const { wrapper } = await setup({ ...task, onlineExperimentId: null, onlineAssignmentId: null })
  expect(wrapper.text()).toBe('')
  expect(getOnlineExperiment).not.toHaveBeenCalled()
  expect(searchOnlineAssignments).not.toHaveBeenCalled()
  wrapper.unmount()
})

it('keeps group and config hidden without evaluation permission and clears them on revocation', async () => {
  const { wrapper, workspace } = await setup()
  workspace.setEffectivePermissions({
    spaceId: '7',
    platformSuperAdmin: true,
    role: null,
    permissions: [SPACE_PERMISSIONS.SPACE_READ, SPACE_PERMISSIONS.TASK_READ],
  })
  await flushPromises()
  expect(wrapper.text()).toContain('无法读取实验组和冻结模板')
  expect(wrapper.text()).not.toContain('Candidate（冻结候选）')
  expect(wrapper.find('a').exists()).toBe(false)
  expect(getOnlineExperiment).toHaveBeenCalledTimes(1)
  wrapper.unmount()
})

it.each([
  { label: 'wrong assignment', row: { ...assignment, id: '999' } },
  { label: 'wrong task', row: { ...assignment, taskId: '999' } },
  { label: 'wrong document', row: { ...assignment, documentId: '999' } },
  { label: 'unknown group', row: { ...assignment, variant: 'UNKNOWN' } },
])('does not display a group from $label', async ({ row }) => {
  vi.mocked(searchOnlineAssignments).mockResolvedValue({
    records: [row],
    total: 1,
    pageNum: 1,
    pageSize: 1,
  })
  const { wrapper } = await setup()
  expect(wrapper.text()).toContain('实验关联未知')
  expect(wrapper.text()).not.toContain('Candidate（冻结候选）')
  expect(wrapper.find('a').exists()).toBe(false)
  wrapper.unmount()
})

it('rejects unsupported or numeric bindings before fetching metadata', async () => {
  const { wrapper } = await setup({ ...task, onlineBindingSchemaVersion: 1 })
  expect(wrapper.text()).toContain('协议尚不支持')
  expect(getOnlineExperiment).not.toHaveBeenCalled()
  await wrapper.setProps({
    task: { ...task, onlineAssignmentId: Number('9007199254740993') as unknown as string },
  })
  expect(getOnlineExperiment).not.toHaveBeenCalled()
  wrapper.unmount()
})

it('abandons delayed group reads when leaving the current space', async () => {
  let resolve!: (page: Awaited<ReturnType<typeof searchOnlineAssignments>>) => void
  vi.mocked(searchOnlineAssignments).mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done
      }),
  )
  const { wrapper, workspace } = await setup()
  workspace.setCurrentSpace('8')
  await flushPromises()
  expect(vi.mocked(searchOnlineAssignments).mock.calls[0]![2]!.aborted).toBe(true)
  resolve({ records: [assignment], total: 1, pageNum: 1, pageSize: 1 })
  await flushPromises()
  expect(wrapper.text()).not.toContain('Candidate（冻结候选）')
  expect(wrapper.find('a').exists()).toBe(false)
  wrapper.unmount()
})
