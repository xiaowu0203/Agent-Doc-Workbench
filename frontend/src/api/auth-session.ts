export interface AuthSessionBridge {
  getAccessToken: () => string | null
  refreshAccessToken?: () => Promise<string | null>
  clearSession: () => void
  onSessionExpired?: () => void | Promise<void>
}

const emptyBridge: AuthSessionBridge = {
  getAccessToken: () => null,
  clearSession: () => undefined,
}

let sessionBridge = emptyBridge
let refreshPromise: Promise<string | null> | null = null
let expirationPromise: Promise<void> | null = null

export function configureAuthSession(bridge: AuthSessionBridge): void {
  sessionBridge = bridge
}

export function getAccessToken(): string | null {
  return sessionBridge.getAccessToken()
}

export async function recoverAuthSession(): Promise<string | null> {
  if (!sessionBridge.refreshAccessToken) {
    await expireAuthSession()
    return null
  }

  if (!refreshPromise) {
    refreshPromise = sessionBridge
      .refreshAccessToken()
      .catch(() => null)
      .finally(() => {
        refreshPromise = null
      })
  }

  const accessToken = await refreshPromise
  if (!accessToken) {
    await expireAuthSession()
  }
  return accessToken
}

export function expireAuthSession(): Promise<void> {
  if (!expirationPromise) {
    expirationPromise = Promise.resolve()
      .then(async () => {
        sessionBridge.clearSession()
        await sessionBridge.onSessionExpired?.()
      })
      .finally(() => {
        expirationPromise = null
      })
  }
  return expirationPromise
}

export function resetAuthSessionForTest(): void {
  sessionBridge = emptyBridge
  refreshPromise = null
  expirationPromise = null
}
