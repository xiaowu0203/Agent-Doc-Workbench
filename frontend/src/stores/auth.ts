import { defineStore } from 'pinia'

import { login as requestLogin, refresh as requestRefresh } from '@/features/auth/api/auth-api'
import type { AuthSession, LoginRequest, User } from '@/features/auth/types'
import { PLATFORM_ROLES } from '@/shared/constants/platform-roles'

const LOCAL_SESSION_KEY = 'adw.auth.session'
const TAB_SESSION_KEY = 'adw.auth.session.tab'

interface StoredAuthSession extends AuthSession {
  refreshToken: string
}

function getStorage(kind: 'local' | 'session'): Storage | null {
  if (typeof window === 'undefined') return null
  return kind === 'local' ? window.localStorage : window.sessionStorage
}

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: User | null
  platformRoles: string[]
  remembered: boolean
  initialized: boolean
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: null,
    refreshToken: null,
    user: null,
    platformRoles: [],
    remembered: false,
    initialized: false,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken && state.user),
    isPlatformSuperAdmin: (state) => state.platformRoles.includes(PLATFORM_ROLES.SUPER_ADMIN),
  },
  actions: {
    async login(credentials: LoginRequest) {
      const session = await requestLogin(credentials)
      this.setSession(session)
    },
    setSession(session: AuthSession) {
      this.accessToken = session.accessToken
      this.refreshToken = session.refreshToken ?? null
      this.user = session.user
      this.platformRoles = session.platformRoles ?? []
      this.initialized = true
    },
    persistSession(remember: boolean) {
      const storage = getStorage(remember ? 'local' : 'session')
      if (!storage || !this.accessToken || !this.refreshToken || !this.user) return
      const otherStorage = getStorage(remember ? 'session' : 'local')
      otherStorage?.removeItem(remember ? TAB_SESSION_KEY : LOCAL_SESSION_KEY)
      const session: StoredAuthSession = {
        accessToken: this.accessToken,
        refreshToken: this.refreshToken,
        user: this.user,
        platformRoles: this.platformRoles,
      }
      storage.setItem(remember ? LOCAL_SESSION_KEY : TAB_SESSION_KEY, JSON.stringify(session))
    },
    restoreSession(): boolean {
      const candidates: Array<[Storage | null, boolean, string]> = [
        [getStorage('local'), true, LOCAL_SESSION_KEY],
        [getStorage('session'), false, TAB_SESSION_KEY],
      ]
      for (const [storage, remembered, key] of candidates) {
        const raw = storage?.getItem(key)
        if (!raw) continue
        try {
          const session = JSON.parse(raw) as StoredAuthSession
          if (!session.accessToken || !session.refreshToken || !session.user) continue
          this.setSession(session)
          this.remembered = remembered
          this.initialized = true
          return true
        } catch {
          storage?.removeItem(key)
        }
      }
      this.initialized = true
      return false
    },
    async refreshAccessToken(): Promise<string | null> {
      if (!this.refreshToken) return null
      const session = await requestRefresh(this.refreshToken)
      this.setSession(session)
      this.persistSession(this.remembered)
      return this.accessToken
    },
    clearSession() {
      this.accessToken = null
      this.refreshToken = null
      this.user = null
      this.platformRoles = []
      this.remembered = false
      this.initialized = true
      getStorage('local')?.removeItem(LOCAL_SESSION_KEY)
      getStorage('session')?.removeItem(TAB_SESSION_KEY)
    },
  },
})
