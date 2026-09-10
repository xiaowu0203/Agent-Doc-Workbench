import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from '@/features/auth/api/auth-api'
import type { LoginResponse } from '@/features/auth/types'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/features/auth/api/auth-api', () => ({
  login: vi.fn(),
  refresh: vi.fn(),
  logout: vi.fn(),
  changePassword: vi.fn(),
}))

const session: LoginResponse = {
  accessToken: 'access-token',
  tokenType: 'Bearer',
  expiresIn: 1800,
  user: { id: 1, username: 'alice', nickname: 'Alice', email: null, avatarUrl: null },
  platformRoles: [],
}

beforeEach(() => {
  setActivePinia(createPinia())
  localStorage.clear()
  sessionStorage.clear()
  vi.clearAllMocks()
})

describe('auth store', () => {
  it('keeps the access token only in memory after login', async () => {
    vi.mocked(authApi.login).mockResolvedValue(session)
    const store = useAuthStore()

    await store.login({ username: 'alice', password: 'secret1', remember: true })

    expect(store.accessToken).toBe('access-token')
    expect(localStorage).toHaveLength(0)
    expect(sessionStorage).toHaveLength(0)
  })

  it('restores a browser session through the HttpOnly refresh cookie', async () => {
    vi.mocked(authApi.refresh).mockResolvedValue(session)
    const store = useAuthStore()

    await expect(store.restoreSession()).resolves.toBe(true)

    expect(authApi.refresh).toHaveBeenCalledWith()
    expect(store.isAuthenticated).toBe(true)
  })

  it('clears memory when no refresh cookie is available', async () => {
    vi.mocked(authApi.refresh).mockRejectedValue(new Error('missing cookie'))
    const store = useAuthStore()
    store.setSession(session)

    await expect(store.restoreSession()).resolves.toBe(false)

    expect(store.accessToken).toBeNull()
    expect(store.user).toBeNull()
    expect(store.initialized).toBe(true)
  })
})
