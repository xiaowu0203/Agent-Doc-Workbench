import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import TaskCreateView from './TaskCreateView.vue'

import * as documentApi from '@/features/document/api/document-api'
import * as taskApi from '@/features/task/api/task-api'
import { useWorkspaceStore } from '@/stores/workspace'

vi.mock('element-plus', async (importOriginal) => {
  const original = await importOriginal<typeof import('element-plus')>()
  return { ...original, ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() } }
})

vi.mock('@/features/document/api/document-api', () => ({
  getDocument: vi.fn(),
  listDocumentTree: vi.fn(),
}))

vi.mock('@/features/task/api/task-api', () => ({
  createTask: vi.fn(),
  getTaskCreateOptions: vi.fn(),
  getTaskDraft: vi.fn(),
  saveTaskDraft: vi.fn(),
  updateTaskDraft: vi.fn(),
}))

async function mountView() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const workspaceStore = useWorkspaceStore()
  workspaceStore.setCurrentSpace(7)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/spaces/:spaceId/tasks/new', component: TaskCreateView },
      { path: '/spaces/:spaceId/tasks/:taskId', component: { template: '<div>任务详情</div>' } },
    ],
  })
  await router.push('/spaces/7/tasks/new')
  await router.isReady()
  const wrapper = mount(TaskCreateView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { router, wrapper }
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(documentApi.listDocumentTree).mockResolvedValue([
    {
      id: 101,
      parentId: null,
      title: '系统能力验收文档',
      docType: 'FORMAL',
      nodeType: 'DOCUMENT',
      children: [],
    },
  ])
  vi.mocked(documentApi.getDocument).mockResolvedValue({
    id: 101,
    spaceId: 7,
    directoryId: null,
    title: '系统能力验收文档',
    docType: 'FORMAL',
    content: '用于回归系统模板安装后的空间 Agent。',
    version: 1,
    status: 'NORMAL',
    updatedAt: null,
    updatedBy: null,
    createdBy: 1,
    creatorName: '管理员',
  })
  vi.mocked(taskApi.getTaskCreateOptions).mockResolvedValue({
    spaceId: 7,
    documentId: 101,
    documentType: 'FORMAL',
    documentVersion: 1,
    documentLength: 20,
    spaceTokenBudget: null,
    agents: [
      {
        id: 23,
        name: '系统模板安装 Agent',
        modelDisplayName: 'GPT-5.2',
        skillSelectionMode: 'ALL_BOUND',
        tokenBudget: 20000,
        executionTimeoutSeconds: 300,
        skillCount: 1,
        mcpCount: 1,
      },
    ],
  })
  vi.mocked(taskApi.createTask).mockResolvedValue({ id: 501, taskNo: 'T-501' })
})

describe('TaskCreateView', () => {
  it('creates a task with the installed system Agent returned by task creation options', async () => {
    const { router, wrapper } = await mountView()

    expect(wrapper.text()).toContain('系统模板安装 Agent')
    expect(taskApi.getTaskCreateOptions).toHaveBeenCalledWith('7', 101, expect.any(AbortSignal))

    await wrapper.findAll('input')[0]?.setValue('系统能力任务回归')
    await wrapper.find('textarea').setValue('验证系统模板安装后的空间 Agent 可以创建任务。')
    await wrapper
      .findAll('button')
      .find((button) => button.text() === '启动任务')
      ?.trigger('click')
    await flushPromises()

    expect(taskApi.createTask).toHaveBeenCalledWith({
      spaceId: '7',
      agentId: 23,
      documentId: 101,
      name: '系统能力任务回归',
      instruction: '验证系统模板安装后的空间 Agent 可以创建任务。',
      tokenBudget: null,
      readScope: 'FULL',
      focusRegions: [],
    })
    expect(router.currentRoute.value.path).toBe('/spaces/7/tasks/501')
  })
})
