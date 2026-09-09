import { request } from '@/api/client'
import type {
  Department,
  DepartmentInput,
  PlatformUser,
  PlatformUserInput,
  PlatformUserMembership,
  PlatformUserPage,
  PlatformUserStats,
} from '@/features/platform-management/types'
import type { EntityId } from '@/features/workspace/types'

export function searchPlatformUsers(options: {
  keyword?: string
  status?: 0 | 1
  departmentId?: EntityId
  platformRoleKey?: string
  pageNum?: number
  pageSize?: number
  signal?: AbortSignal
}): Promise<PlatformUserPage> {
  return request<PlatformUserPage>({
    method: 'POST',
    url: '/platform/users/search',
    data: {
      keyword: options.keyword || undefined,
      status: options.status,
      departmentId: options.departmentId,
      platformRoleKey: options.platformRoleKey,
      pageNum: options.pageNum ?? 1,
      pageSize: options.pageSize ?? 10,
    },
    signal: options.signal,
  })
}

export function getPlatformUserStats(): Promise<PlatformUserStats> {
  return request<PlatformUserStats>({ method: 'GET', url: '/platform/users/stats' })
}

export function createPlatformUser(payload: PlatformUserInput): Promise<PlatformUser> {
  return request<PlatformUser>({ method: 'POST', url: '/platform/users', data: payload })
}

export function updatePlatformUser(
  userId: EntityId,
  payload: PlatformUserInput,
): Promise<PlatformUser> {
  return request<PlatformUser>({ method: 'PUT', url: `/platform/users/${userId}`, data: payload })
}

export function updatePlatformUserStatus(userId: EntityId, status: 0 | 1): Promise<PlatformUser> {
  return request<PlatformUser>({
    method: 'PUT',
    url: `/platform/users/${userId}/status`,
    data: { status },
  })
}

export function resetPlatformUserPassword(userId: EntityId, newPassword: string): Promise<void> {
  return request<void>({
    method: 'PUT',
    url: `/platform/users/${userId}/password`,
    data: { newPassword },
  })
}

export function replacePlatformUserRoles(
  userId: EntityId,
  platformRoles: string[],
): Promise<PlatformUser> {
  return request<PlatformUser>({
    method: 'PUT',
    url: `/platform/users/${userId}/platform-roles`,
    data: { roleKeys: platformRoles },
  })
}

export function listDepartments(signal?: AbortSignal): Promise<Department[]> {
  return request<Department[]>({
    method: 'GET',
    url: '/platform/departments',
    signal,
  }).then((departments) => departments ?? [])
}

export function createDepartment(payload: DepartmentInput): Promise<Department> {
  return request<Department>({ method: 'POST', url: '/platform/departments', data: payload })
}

export function updateDepartment(
  departmentId: EntityId,
  payload: DepartmentInput,
): Promise<Department> {
  return request<Department>({
    method: 'PUT',
    url: `/platform/departments/${departmentId}`,
    data: payload,
  })
}

export function deleteDepartment(departmentId: EntityId): Promise<void> {
  return request<void>({ method: 'DELETE', url: `/platform/departments/${departmentId}` })
}

export function queryPlatformUserMemberships(
  userIds: EntityId[],
  signal?: AbortSignal,
): Promise<PlatformUserMembership[]> {
  if (userIds.length === 0) return Promise.resolve([])
  return request<PlatformUserMembership[]>({
    method: 'POST',
    url: '/document/platform/user-memberships/query',
    data: { userIds },
    signal,
  }).then((memberships) => memberships ?? [])
}
