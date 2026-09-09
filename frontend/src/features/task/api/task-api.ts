import { request } from '@/api/client'
import type { PageResult } from '@/features/document/types'
import type { EntityId } from '@/features/workspace/types'
import type {
  CreateTaskRequest,
  CreatedTask,
  TaskCreateOptions,
  TaskDetail,
  TaskExecutionDetail,
  TaskToolCallPageItem,
  TaskDraft,
  TaskFocusRegion,
  TaskPage,
  TaskReadScope,
  TaskStatus,
} from '@/features/task/types'

export function createTask(payload: CreateTaskRequest, signal?: AbortSignal): Promise<CreatedTask> {
  return request<CreatedTask>({
    method: 'POST',
    url: '/task/tasks',
    data: payload,
    signal,
  })
}

export function searchTasks(
  payload: {
    spaceId: EntityId
    pageNum: number
    pageSize: number
    keyword?: string
    status?: TaskStatus
    agentId?: EntityId
    modelId?: EntityId
    documentId?: EntityId
    startedFrom?: string
    startedTo?: string
  },
  signal?: AbortSignal,
): Promise<TaskPage> {
  return request<TaskPage>({ method: 'POST', url: '/task/tasks/search', data: payload, signal })
}

export function getTaskCreateOptions(
  spaceId: EntityId,
  documentId: EntityId,
  signal?: AbortSignal,
): Promise<TaskCreateOptions> {
  return request<TaskCreateOptions>({
    method: 'POST',
    url: '/task/tasks/create-options',
    data: { spaceId, documentId },
    signal,
  })
}

export function getTask(taskId: EntityId, signal?: AbortSignal): Promise<TaskDetail> {
  return request<TaskDetail>({ method: 'GET', url: `/task/tasks/${taskId}`, signal })
}

export function runTask(taskId: EntityId, signal?: AbortSignal): Promise<TaskDetail> {
  return request<TaskDetail>({ method: 'PUT', url: `/task/tasks/${taskId}/run`, signal })
}

export function getTaskExecutionDetail(
  taskId: EntityId,
  signal?: AbortSignal,
): Promise<TaskExecutionDetail> {
  return request<TaskExecutionDetail>({
    method: 'GET',
    url: `/task/tasks/${taskId}/execution-detail`,
    signal,
  })
}

export function getTaskToolCalls(
  spaceId: EntityId,
  taskIds: EntityId[],
  pageNum: number,
  pageSize: number,
  signal?: AbortSignal,
): Promise<PageResult<TaskToolCallPageItem>> {
  return request<PageResult<TaskToolCallPageItem>>({
    method: 'POST',
    url: '/task/tasks/tool-calls/query',
    data: { spaceId, taskIds, pageNum, pageSize },
    signal,
  })
}

export function terminateTask(taskId: EntityId, signal?: AbortSignal): Promise<TaskDetail> {
  return request<TaskDetail>({ method: 'PUT', url: `/task/tasks/${taskId}/terminate`, signal })
}

export function rerunTask(taskId: EntityId, signal?: AbortSignal): Promise<TaskDetail> {
  return request<TaskDetail>({ method: 'PUT', url: `/task/tasks/${taskId}/rerun`, signal })
}

export function saveTaskDraft(
  payload: {
    spaceId: EntityId
    agentId: EntityId | null
    documentId: EntityId | null
    name: string
    instruction: string
    tokenBudget: number | null
    readScope: TaskReadScope
    focusRegions: TaskFocusRegion[]
  },
  signal?: AbortSignal,
): Promise<TaskDraft> {
  return request<TaskDraft>({ method: 'POST', url: '/task/task-drafts', data: payload, signal })
}

export function updateTaskDraft(
  draftId: EntityId,
  payload: Parameters<typeof saveTaskDraft>[0],
  signal?: AbortSignal,
): Promise<TaskDraft> {
  return request<TaskDraft>({
    method: 'PUT',
    url: `/task/task-drafts/${draftId}`,
    data: payload,
    signal,
  })
}

export function getTaskDraft(draftId: EntityId, signal?: AbortSignal): Promise<TaskDraft> {
  return request<TaskDraft>({ method: 'GET', url: `/task/task-drafts/${draftId}`, signal })
}

export function searchTaskDrafts(
  spaceId: EntityId,
  pageNum = 1,
  pageSize = 10,
  keyword?: string,
  signal?: AbortSignal,
): Promise<PageResult<TaskDraft>> {
  return request<PageResult<TaskDraft>>({
    method: 'POST',
    url: '/task/task-drafts/search',
    data: { spaceId, pageNum, pageSize, keyword: keyword || undefined },
    signal,
  })
}

export function deleteTaskDraft(draftId: EntityId, signal?: AbortSignal): Promise<void> {
  return request<void>({ method: 'DELETE', url: `/task/task-drafts/${draftId}`, signal })
}

export function launchTaskDraft(draftId: EntityId, signal?: AbortSignal): Promise<CreatedTask> {
  return request<CreatedTask>({
    method: 'POST',
    url: `/task/task-drafts/${draftId}/launch`,
    signal,
  })
}
