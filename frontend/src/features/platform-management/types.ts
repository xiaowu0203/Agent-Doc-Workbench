import type { EntityId } from '@/features/workspace/types'

export interface DepartmentSummary {
  id: EntityId
  name: string
  code: string
}

export interface PlatformUser {
  id: EntityId
  username: string
  nickname: string | null
  email: string | null
  avatarUrl: string | null
  department: DepartmentSummary | null
  jobTitle: string | null
  status: 0 | 1
  platformRoles: string[]
  lastLoginAt: string | null
  createdAt: string
  updatedAt: string
}

export interface PlatformUserPage {
  records: PlatformUser[]
  total: number
  pageNum: number
  pageSize: number
}

export interface PlatformUserStats {
  total: number
  enabled: number
  disabled: number
  unassignedDepartment: number
}

export interface PlatformUserInput {
  username?: string
  password?: string
  nickname: string
  email?: string
  departmentId?: EntityId
  jobTitle?: string
  status?: 0 | 1
  superAdmin?: boolean
}

export interface Department {
  id: EntityId
  parentId: EntityId
  name: string
  code: string
  leaderUserId: EntityId | null
  leaderName: string | null
  memberCount: number
  childCount: number
  sortOrder: number
  status: 0 | 1
  createdAt: string
  updatedAt: string
}

export interface DepartmentInput {
  name: string
  code?: string
  parentId?: EntityId
  leaderUserId?: EntityId
  sortOrder?: number
  status?: 0 | 1
}

export interface SpaceRoleSummary {
  id: EntityId
  roleKey: string
  displayName: string
  protectedRole: boolean
}

export interface PlatformUserMembership {
  userId: EntityId
  spaceId: EntityId
  spaceName: string
  role: SpaceRoleSummary
}
