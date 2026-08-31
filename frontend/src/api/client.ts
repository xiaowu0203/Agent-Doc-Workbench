import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'

import { getAccessToken, recoverAuthSession } from './auth-session'
import { ApiError, normalizeApiError, unwrapApiResult } from './errors'
import { isAuthErrorCode, type ApiResult } from './result'

interface RequestOptions {
  retryAfterRefresh?: boolean
}

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15_000,
  withCredentials: true,
  transformResponse: [
    (data: unknown) => {
      if (typeof data !== 'string' || data.length === 0) return data
      const preserved = data.replace(/(:\s*)(-?\d{16,})(?=\s*[,}\]])/g, '$1"$2"')
      return JSON.parse(preserved) as unknown
    },
  ],
})

apiClient.interceptors.request.use((config) => {
  const accessToken = getAccessToken()
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

function isAuthenticationError(error: ApiError): boolean {
  return error.status === 401 || (error.code !== null && isAuthErrorCode(error.code))
}

async function execute<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await apiClient.request<ApiResult<T>>(config)
  return unwrapApiResult(response.data, response.status)
}

async function executeWithAuthRecovery<T>(
  operation: () => Promise<T>,
  retryAfterRefresh: boolean,
): Promise<T> {
  try {
    return await operation()
  } catch (error) {
    const apiError = normalizeApiError(error)
    if (!retryAfterRefresh || !isAuthenticationError(apiError)) {
      throw apiError
    }

    const accessToken = await recoverAuthSession()
    if (!accessToken) {
      throw apiError
    }

    try {
      return await operation()
    } catch (retryError) {
      throw normalizeApiError(retryError)
    }
  }
}

export function request<T>(
  config: AxiosRequestConfig,
  options: RequestOptions = { retryAfterRefresh: true },
): Promise<T> {
  return executeWithAuthRecovery(() => execute<T>(config), options.retryAfterRefresh ?? true)
}

export function requestRaw<T = Blob>(config: AxiosRequestConfig): Promise<AxiosResponse<T>> {
  return executeWithAuthRecovery(() => apiClient.request<T>(config), true)
}

export { apiClient }
