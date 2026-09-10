import axios from 'axios'

import { API_SUCCESS_CODE, type ApiResult } from './result'

export class ApiError extends Error {
  readonly code: number | null
  readonly status: number | null

  constructor(message: string, options?: { code?: number; status?: number; cause?: unknown }) {
    super(message, { cause: options?.cause })
    this.name = 'ApiError'
    this.code = options?.code ?? null
    this.status = options?.status ?? null
  }
}

export function unwrapApiResult<T>(result: ApiResult<T>, status?: number): T {
  if (result.code !== API_SUCCESS_CODE) {
    throw new ApiError(result.message, { code: result.code, status })
  }

  return result.data
}

export function normalizeApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  if (axios.isAxiosError<ApiResult<unknown>>(error)) {
    const response = error.response
    return new ApiError(response?.data?.message ?? error.message ?? '网络请求失败', {
      code: response?.data?.code,
      status: response?.status,
      cause: error,
    })
  }

  return new ApiError(error instanceof Error ? error.message : '未知错误', { cause: error })
}
