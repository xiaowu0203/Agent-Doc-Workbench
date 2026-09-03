import { request } from '@/api/client'
import type {
  ModelConfig,
  ModelConnectionTest,
  ModelInput,
  ModelPage,
  ModelStatus,
} from '@/features/model/types'
import type { EntityId } from '@/features/workspace/types'

export function searchModels(
  options: {
    keyword?: string
    provider?: string
    status?: ModelStatus
    adapterType?: string
    pageNum?: number
    pageSize?: number
    signal?: AbortSignal
  } = {},
): Promise<ModelPage> {
  return request<ModelPage>({
    method: 'POST',
    url: '/agent/models/search',
    data: {
      keyword: options.keyword || undefined,
      provider: options.provider,
      status: options.status === 'ENABLED' ? 1 : options.status === 'DISABLED' ? 0 : undefined,
      adapterType: options.adapterType,
      pageNum: options.pageNum ?? 1,
      pageSize: options.pageSize ?? 8,
    },
    signal: options.signal,
  }).then(
    (page) =>
      page ?? {
        records: [],
        total: 0,
        pageNum: options.pageNum ?? 1,
        pageSize: options.pageSize ?? 8,
      },
  )
}

export function createModel(payload: ModelInput): Promise<ModelConfig> {
  return request<ModelConfig>({ method: 'POST', url: '/agent/models', data: payload })
}

export function updateModel(modelId: EntityId, payload: ModelInput): Promise<ModelConfig> {
  return request<ModelConfig>({ method: 'PUT', url: `/agent/models/${modelId}`, data: payload })
}

export function updateModelStatus(modelId: EntityId, status: 0 | 1): Promise<ModelConfig> {
  return request<ModelConfig>({
    method: 'PUT',
    url: `/agent/models/${modelId}/status`,
    params: { status },
  })
}

export function testSavedModel(modelId: EntityId): Promise<ModelConnectionTest> {
  return request<ModelConnectionTest>({
    method: 'POST',
    url: `/agent/models/${modelId}/test-connect`,
  })
}

export function testModelInput(payload: ModelInput): Promise<ModelConnectionTest> {
  return request<ModelConnectionTest>({
    method: 'POST',
    url: '/agent/models/test-connect',
    data: payload,
  })
}
