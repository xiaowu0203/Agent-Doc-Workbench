import { afterEach, describe, expect, it, vi } from 'vitest'

import { configureAuthSession, recoverAuthSession, resetAuthSessionForTest } from './auth-session'

afterEach(() => {
  resetAuthSessionForTest()
})

describe('auth session coordinator', () => {
  it('shares one refresh request across concurrent recoveries', async () => {
    let finishRefresh: ((token: string) => void) | undefined
    const refreshAccessToken = vi.fn(
      () =>
        new Promise<string>((resolve) => {
          finishRefresh = resolve
        }),
    )

    configureAuthSession({
      getAccessToken: () => null,
      refreshAccessToken,
      clearSession: vi.fn(),
    })

    const first = recoverAuthSession()
    const second = recoverAuthSession()
    expect(refreshAccessToken).toHaveBeenCalledTimes(1)

    finishRefresh?.('new-access-token')
    await expect(Promise.all([first, second])).resolves.toEqual([
      'new-access-token',
      'new-access-token',
    ])
  })

  it('clears the in-memory session when no refresh handler is configured', async () => {
    const clearSession = vi.fn()
    const onSessionExpired = vi.fn()
    configureAuthSession({ getAccessToken: () => 'expired', clearSession, onSessionExpired })

    await expect(recoverAuthSession()).resolves.toBeNull()
    expect(clearSession).toHaveBeenCalledOnce()
    expect(onSessionExpired).toHaveBeenCalledOnce()
  })

  it('clears the session and redirects when refresh fails', async () => {
    const clearSession = vi.fn()
    const onSessionExpired = vi.fn()
    configureAuthSession({
      getAccessToken: () => 'expired',
      refreshAccessToken: vi.fn().mockRejectedValue(new Error('refresh rejected')),
      clearSession,
      onSessionExpired,
    })

    await expect(Promise.all([recoverAuthSession(), recoverAuthSession()])).resolves.toEqual([
      null,
      null,
    ])
    expect(clearSession).toHaveBeenCalledOnce()
    expect(onSessionExpired).toHaveBeenCalledOnce()
  })
})
