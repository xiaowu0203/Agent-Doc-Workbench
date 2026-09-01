import { request } from '@/api/client'
import type { EntityId } from '@/features/workspace/types'

export interface PermissionItem {
  code: string
  name: string
  category: string
  description: string
  sortOrder: number
}

export interface SpaceRole {
  id: EntityId
  spaceId: EntityId
  roleKey: string
  displayName: string
  systemRole: boolean
  description: string | null
  protectedRole: boolean
  memberCount: number
  permissionCodes: string[]
  createdAt: string
}

export interface Member {
  id: EntityId
  userId: EntityId
  role: {
    roleId: EntityId
    roleKey: string
    displayName: string
  }
  createdAt: string
}

export interface MemberUser {
  userId: EntityId
  username: string
  nickname: string | null
}

export function listPermissions(spaceId: EntityId): Promise<PermissionItem[]> {
  return request<PermissionItem[]>({
    method: 'GET',
    url: `/document/spaces/${spaceId}/permissions`,
  })
}

export function listRoles(spaceId: EntityId): Promise<SpaceRole[]> {
  return request<SpaceRole[]>({
    method: 'GET',
    url: `/document/spaces/${spaceId}/roles`,
  })
}

export function createRole(
  spaceId: EntityId,
  data: {
    roleKey: string
    displayName: string
    description: string
    permissionCodes: string[]
  },
): Promise<SpaceRole> {
  return request<SpaceRole>({
    method: 'POST',
    url: `/document/spaces/${spaceId}/roles`,
    data,
  })
}

export function updateRole(
  spaceId: EntityId,
  roleId: EntityId,
  data: { displayName: string; description: string },
): Promise<SpaceRole> {
  return request<SpaceRole>({
    method: 'PUT',
    url: `/document/spaces/${spaceId}/roles/${roleId}`,
    data,
  })
}

export function replaceRolePermissions(
  spaceId: EntityId,
  roleId: EntityId,
  permissionCodes: string[],
): Promise<SpaceRole> {
  return request<SpaceRole>({
    method: 'PUT',
    url: `/document/spaces/${spaceId}/roles/${roleId}/permissions`,
    data: { permissionCodes },
  })
}

export function deleteRole(spaceId: EntityId, roleId: EntityId): Promise<void> {
  return request<void>({
    method: 'DELETE',
    url: `/document/spaces/${spaceId}/roles/${roleId}`,
  })
}

export function listMembers(spaceId: EntityId): Promise<Member[]> {
  return request<Member[]>({
    method: 'GET',
    url: `/document/spaces/${spaceId}/members`,
  })
}

export function listMemberUsers(spaceId: EntityId, userIds: EntityId[]): Promise<MemberUser[]> {
  return request<MemberUser[]>({
    method: 'POST',
    url: `/document/spaces/${spaceId}/members/users/query`,
    data: { userIds },
  })
}

export function addMember(
  spaceId: EntityId,
  data: { userId: EntityId; roleId: EntityId },
): Promise<Member> {
  return request<Member>({
    method: 'POST',
    url: `/document/spaces/${spaceId}/members`,
    data,
  })
}

export function changeMemberRole(
  spaceId: EntityId,
  userId: EntityId,
  roleId: EntityId,
): Promise<Member> {
  return request<Member>({
    method: 'PUT',
    url: `/document/spaces/${spaceId}/members/${userId}`,
    data: { roleId },
  })
}

export function removeMember(spaceId: EntityId, userId: EntityId): Promise<void> {
  return request<void>({
    method: 'DELETE',
    url: `/document/spaces/${spaceId}/members/${userId}`,
  })
}
