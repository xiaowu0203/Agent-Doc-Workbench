import { request } from '@/api/client'
import type { EffectivePermission, EntityId, Space } from '@/features/workspace/types'

export function listMySpaces(): Promise<Space[]> {
  return request<Space[]>({ method: 'GET', url: '/document/spaces' })
}

export function getEffectivePermissions(spaceId: EntityId): Promise<EffectivePermission> {
  return request<EffectivePermission>({
    method: 'GET',
    url: `/document/spaces/${spaceId}/me/permissions`,
  })
}
