import { request } from '@/api/client'
import type {
  CreateSpaceRequest,
  EffectivePermission,
  EntityId,
  Space,
} from '@/features/workspace/types'

export function createSpace(payload: CreateSpaceRequest): Promise<Space> {
  return request<Space>({ method: 'POST', url: '/document/spaces', data: payload })
}

export function deleteSpace(spaceId: EntityId): Promise<void> {
  return request<void>({ method: 'DELETE', url: `/document/spaces/${spaceId}` })
}

export function listMySpaces(): Promise<Space[]> {
  return request<Space[]>({ method: 'GET', url: '/document/spaces' })
}

export function getEffectivePermissions(spaceId: EntityId): Promise<EffectivePermission> {
  return request<EffectivePermission>({
    method: 'GET',
    url: `/document/spaces/${spaceId}/me/permissions`,
  })
}
