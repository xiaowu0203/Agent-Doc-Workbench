/// <reference types="vite/client" />

import type { SpacePermissionCode } from '@/shared/constants/permissions'
import type { PlatformRoleKey } from '@/shared/constants/platform-roles'

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_GATEWAY_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

declare module 'vue-router' {
  interface RouteMeta {
    guestOnly?: boolean
    requiresAuth?: boolean
    requiresSpace?: boolean
    platformRole?: PlatformRoleKey
    permission?: SpacePermissionCode
  }
}
