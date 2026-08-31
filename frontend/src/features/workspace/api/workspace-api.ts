import { request } from '@/api/client'
import type { EffectivePermission, Space } from '@/features/workspace/types'

export function listMySpaces(): Promise<Space[]> {
  return request<Space[]>({ method: 'GET', url: '/document/spaces' })
}

export function getEffectivePermissions(spaceId: number): Promise<EffectivePermission> {
  return request<EffectivePermission>({
    method: 'GET',
    url: `/document/spaces/${spaceId}/me/permissions`,
  })
}
