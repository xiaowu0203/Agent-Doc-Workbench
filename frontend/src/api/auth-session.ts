export interface AuthSessionBridge {
  getAccessToken: () => string | null
  refreshAccessToken?: () => Promise<string | null>
  clearSession: () => void
}

const emptyBridge: AuthSessionBridge = {
  getAccessToken: () => null,
  clearSession: () => undefined,
}

let sessionBridge = emptyBridge
let refreshPromise: Promise<string | null> | null = null

export function configureAuthSession(bridge: AuthSessionBridge): void {
  sessionBridge = bridge
}

export function getAccessToken(): string | null {
  return sessionBridge.getAccessToken()
}

export async function recoverAuthSession(): Promise<string | null> {
  if (!sessionBridge.refreshAccessToken) {
    sessionBridge.clearSession()
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
    sessionBridge.clearSession()
  }
  return accessToken
}

export function resetAuthSessionForTest(): void {
  sessionBridge = emptyBridge
  refreshPromise = null
}
