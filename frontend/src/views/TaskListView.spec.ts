import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import TaskListView from './TaskListView.vue'

import * as taskApi from '@/features/task/api/task-api'
import type { TaskListItem } from '@/features/task/types'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('element-plus', async (importOriginal) => {
  const original = await importOriginal<typeof import('element-plus')>()
  return {
    ...original,
    ElMessage: { success: vi.fn(), error: vi.fn() },
    ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) },
  }
})

vi.mock('@/features/task/api/task-api', () => ({
  deleteTaskDraft: vi.fn(),
  launchTaskDraft: vi.fn(),
  rerunTask: vi.fn(),
  runTask: vi.fn(),
  searchTaskDrafts: vi.fn(),
  searchTasks: vi.fn(),
  terminateTask: vi.fn(),
}))

function task(id: number, status: TaskListItem['status']): TaskListItem {
  return {
    id,
    taskNo: `T-${id}`,
    spaceId: 7,
    name: `${status} 任务`,
    status,
    agentId: 2,
    agentName: '文档 Agent',
    documentId: 3,
    documentTitle: '产品方案',
    documentType: 'FORMAL',
    tokenBudget: 10000,
    tokensUsed: status === 'PENDING' ? null : 1200,
    createdBy: 1,
    creatorName: 'Alice',
    startTime: null,
    endTime: null,
    createdAt: '2026-09-10T00:00:00Z',
  }
}

async function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspace = useWorkspaceStore()
  workspace.setCurrentSpace(7)
  workspace.setEffectivePermissions({
    spaceId: 7,
    platformSuperAdmin: false,
    role: null,
    permissions: [
      SPACE_PERMISSIONS.TASK_READ,
      SPACE_PERMISSIONS.TASK_CREATE,
      SPACE_PERMISSIONS.TASK_TERMINATE,
    ],
  })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/spaces/:spaceId/tasks', name: 'space-tasks', component: TaskListView },
      { path: '/spaces/:spaceId/tasks/new', name: 'space-task-create', component: TaskListView },
      {
        path: '/spaces/:spaceId/tasks/:taskId',
        name: 'space-task-detail',
        component: TaskListView,
      },
    ],
  })
  await router.push('/spaces/7/tasks')
  await router.isReady()
  const wrapper = mount(TaskListView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { router, wrapper }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(taskApi.searchTasks).mockResolvedValue({
    records: [task(1, 'PENDING'), task(2, 'RUNNING'), task(3, 'FAILED')],
    total: 3,
    pageNum: 1,
    pageSize: 10,
  })
  vi.mocked(taskApi.searchTaskDrafts).mockResolvedValue({
    records: [
      {
        id: 9,
        spaceId: 7,
        agentId: 2,
        documentId: 3,
        name: '待完善草稿',
        instruction: '补充验收标准',
        tokenBudget: 10000,
        readScope: 'FULL',
        focusRegions: [],
        createdAt: '2026-09-10T00:00:00Z',
        updatedAt: '2026-09-10T01:00:00Z',
      },
    ],
    total: 1,
    pageNum: 1,
    pageSize: 10,
  })
  vi.mocked(taskApi.rerunTask).mockResolvedValue({ id: 30, taskNo: 'T-30' } as never)
})

describe('TaskListView', () => {
  it('renders task states and the permitted run, terminate, and rerun actions', async () => {
    const { wrapper } = await mountView()

    expect(taskApi.searchTasks).toHaveBeenCalledWith(
      expect.objectContaining({ spaceId: '7', pageNum: 1, pageSize: 10 }),
      expect.any(AbortSignal),
    )
    expect(wrapper.text()).toContain('待运行')
    expect(wrapper.text()).toContain('运行中')
    expect(wrapper.text()).toContain('异常失败')
    expect(wrapper.findAll('button').map((button) => button.text())).toEqual(
      expect.arrayContaining(['运行', '终止', '重跑']),
    )
  })

  it('reruns a failed task and opens the new task detail', async () => {
    const { router, wrapper } = await mountView()
    const rerun = wrapper.findAll('button').find((button) => button.text() === '重跑')

    await rerun?.trigger('click')
    await flushPromises()

    expect(taskApi.rerunTask).toHaveBeenCalledWith(3)
    expect(router.currentRoute.value.path).toBe('/spaces/7/tasks/30')
  })

  it('loads saved drafts when the draft tab is selected', async () => {
    const { wrapper } = await mountView()
    const draftTab = wrapper.findAll('.el-tabs__item').find((tab) => tab.text() === '任务草稿')

    await draftTab?.trigger('click')
    await flushPromises()

    expect(taskApi.searchTaskDrafts).toHaveBeenCalled()
    expect(wrapper.text()).toContain('待完善草稿')
    expect(wrapper.text()).toContain('补充验收标准')
  })
})
