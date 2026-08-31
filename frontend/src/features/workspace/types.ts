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
  status: string
  role: SpaceRoleSummary | null
  platformSuperAdmin: boolean
  createdAt: string
}

export interface EffectivePermission {
  spaceId: EntityId
  platformSuperAdmin: boolean
  role: SpaceRoleSummary | null
  permissions: string[]
}
