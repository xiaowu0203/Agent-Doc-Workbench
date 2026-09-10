export const PLATFORM_ROLES = {
  SUPER_ADMIN: 'PLATFORM_SUPER_ADMIN',
} as const

export type PlatformRoleKey = (typeof PLATFORM_ROLES)[keyof typeof PLATFORM_ROLES]
