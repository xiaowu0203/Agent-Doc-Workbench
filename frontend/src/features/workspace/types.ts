export interface SpaceRoleSummary {
  roleId: number
  roleKey: string
  displayName: string
}

export interface Space {
  id: number
  name: string
  description: string | null
  ownerId: number
  tokenBudget: number | null
  status: string
  role: SpaceRoleSummary | null
  platformSuperAdmin: boolean
  createdAt: string
}

export interface EffectivePermission {
  spaceId: number
  platformSuperAdmin: boolean
  role: SpaceRoleSummary | null
  permissions: string[]
}
