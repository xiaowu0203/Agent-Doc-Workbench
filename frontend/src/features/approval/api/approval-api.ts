import { request } from '@/api/client'
import type {
  BatchChangeRequestResult,
  ChangeRequestComment,
  ChangeRequestDecision,
  ChangeRequestDetail,
  ChangeRequestPage,
  ChangeRequestStats,
} from '@/features/approval/types'
import type { EntityId } from '@/features/workspace/types'

export function searchChangeRequests(
  payload: {
    spaceId: EntityId
    status?: string
    assignedToMe?: boolean
    pageNum: number
    pageSize: number
  },
  signal?: AbortSignal,
): Promise<ChangeRequestPage> {
  const { pageNum, pageSize, ...filters } = payload
  return request<ChangeRequestPage>({
    method: 'POST',
    url: '/task/change-requests/query',
    data: { ...filters, pageParam: { pageNum, pageSize } },
    signal,
  })
}

export function getChangeRequestStats(
  spaceId: EntityId,
  signal?: AbortSignal,
): Promise<ChangeRequestStats> {
  return request<ChangeRequestStats>({
    method: 'GET',
    url: '/task/change-requests/stats',
    params: { spaceId },
    signal,
  })
}

export function getChangeRequest(id: EntityId, signal?: AbortSignal): Promise<ChangeRequestDetail> {
  return request<ChangeRequestDetail>({
    method: 'GET',
    url: `/task/change-requests/${id}`,
    signal,
  })
}

export function claimChangeRequest(id: EntityId): Promise<unknown> {
  return request({ method: 'PUT', url: `/task/change-requests/${id}/claim` })
}

export function unclaimChangeRequest(id: EntityId): Promise<unknown> {
  return request({ method: 'PUT', url: `/task/change-requests/${id}/unclaim` })
}

export function approveChangeRequest(
  id: EntityId,
  payload: ChangeRequestDecision,
): Promise<unknown> {
  return request({ method: 'PUT', url: `/task/change-requests/${id}/approve`, data: payload })
}

export function acceptChangeRequest(
  id: EntityId,
  payload: ChangeRequestDecision,
): Promise<unknown> {
  return request({ method: 'PUT', url: `/task/change-requests/${id}/accept`, data: payload })
}

export function rejectChangeRequest(id: EntityId, reviewComment: string): Promise<unknown> {
  return request({
    method: 'PUT',
    url: `/task/change-requests/${id}/reject`,
    data: { reviewComment },
  })
}

export function returnChangeRequest(id: EntityId, reviewComment: string): Promise<unknown> {
  return request({
    method: 'PUT',
    url: `/task/change-requests/${id}/return`,
    data: { reviewComment },
  })
}

export function mergeChangeRequest(id: EntityId): Promise<unknown> {
  return request({ method: 'PUT', url: `/task/change-requests/${id}/merge` })
}

export function addChangeRequestComment(
  id: EntityId,
  payload: { changeKey?: string; content: string },
): Promise<ChangeRequestComment> {
  return request<ChangeRequestComment>({
    method: 'POST',
    url: `/task/change-requests/${id}/comments`,
    data: payload,
  })
}

export function batchApproveChangeRequests(
  ids: EntityId[],
  reviewComment: string,
): Promise<BatchChangeRequestResult> {
  return request<BatchChangeRequestResult>({
    method: 'PUT',
    url: '/task/change-requests/batch/approve',
    data: { ids, reviewComment },
  })
}

export function batchAcceptChangeRequests(
  ids: EntityId[],
  reviewComment: string,
): Promise<BatchChangeRequestResult> {
  return request<BatchChangeRequestResult>({
    method: 'PUT',
    url: '/task/change-requests/batch/accept',
    data: { ids, reviewComment },
  })
}

export function batchRejectChangeRequests(
  ids: EntityId[],
  reviewComment: string,
): Promise<BatchChangeRequestResult> {
  return request<BatchChangeRequestResult>({
    method: 'PUT',
    url: '/task/change-requests/batch/reject',
    data: { ids, reviewComment },
  })
}

export function batchMergeChangeRequests(ids: EntityId[]): Promise<BatchChangeRequestResult> {
  return request<BatchChangeRequestResult>({
    method: 'PUT',
    url: '/task/change-requests/batch/merge',
    data: { ids },
  })
}
