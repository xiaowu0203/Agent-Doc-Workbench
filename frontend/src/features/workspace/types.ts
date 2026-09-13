export type EntityId = string | number

export interface SpaceRoleSummary {
  roleId: EntityId
  roleKey: string
  displayName: string
}

export interface Space {
  id: EntityId
  name: string
  description: string | null
  ownerId: EntityId
  tokenBudget: number | null
  monthlyTokenBudget?: number | null
  status: string
  role: SpaceRoleSummary | null
  platformSuperAdmin: boolean
  createdAt: string
}

export interface CreateSpaceRequest {
  name: string
  description?: string
  tokenBudget?: number
  monthlyTokenBudget?: number
}

export interface UpdateSpaceRequest {
  name?: string
  description?: string
  tokenBudget?: number
  monthlyTokenBudget?: number
  clearTokenBudget?: boolean
  clearMonthlyTokenBudget?: boolean
}

export interface EffectivePermission {
  spaceId: EntityId
  platformSuperAdmin: boolean
  role: SpaceRoleSummary | null
  permissions: string[]
}
