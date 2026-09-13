import axios, { type AxiosRequestConfig, type AxiosResponse } from 'axios'

import { expireAuthSession, getAccessToken, recoverAuthSession } from './auth-session'
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
      return JSON.parse(preserveLargeIntegers(data)) as unknown
    },
  ],
})

/**
 * Preserve snowflake-style IDs as strings without rewriting digits inside JSON string values.
 * A response may contain a JSON-encoded detail field, so a regular expression over the whole
 * response would corrupt that nested string before the outer JSON is parsed.
 */
export function preserveLargeIntegers(data: string): string {
  let result = ''
  let inString = false
  let escaped = false

  for (let index = 0; index < data.length; index += 1) {
    const character = data[index]
    if (inString) {
      result += character
      if (escaped) {
        escaped = false
      } else if (character === '\\') {
        escaped = true
      } else if (character === '"') {
        inString = false
      }
      continue
    }

    if (character === '"') {
      inString = true
      result += character
      continue
    }

    if (character === '-' || /\d/.test(character)) {
      const start = index
      if (character === '-') index += 1
      while (index + 1 < data.length && /\d/.test(data[index + 1])) index += 1
      const token = data.slice(start, index + 1)
      const next = data.slice(index + 1)
      if (/^-?\d{16,}\s*[,}\]]/.test(`${token}${next}`)) {
        result += `"${token}"`
      } else {
        result += token
      }
      continue
    }

    result += character
  }

  return result
}

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
      const retriedApiError = normalizeApiError(retryError)
      if (isAuthenticationError(retriedApiError)) {
        await expireAuthSession()
      }
      throw retriedApiError
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
