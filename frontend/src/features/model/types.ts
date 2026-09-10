import type { EntityId } from '@/features/workspace/types'

export type ModelStatus = 'ENABLED' | 'DISABLED'

export interface ModelConfig {
  id: EntityId
  provider: string
  adapterType: string
  modelKey: string
  displayName: string
  officialUrl: string | null
  baseUrl: string | null
  apiKeyConfigured: boolean
  optionsJson: string | null
  configVersion: number
  contextWindow: number | null
  maxOutputTokens: number | null
  inputPricePerMillion: number | null
  outputPricePerMillion: number | null
  status: ModelStatus
  agentCount: number
  description: string | null
}

export interface ModelPage {
  records: ModelConfig[]
  total: number
  pageNum: number
  pageSize: number
}

export interface ModelInput {
  provider: string
  adapterType?: string
  modelKey: string
  displayName: string
  officialUrl?: string
  baseUrl?: string
  apiKey?: string
  optionsJson?: string
  contextWindow?: number
  maxOutputTokens?: number
  inputPricePerMillion?: number
  outputPricePerMillion?: number
  description?: string
}

export interface ModelConnectionTest {
  connected: boolean
  provider: string
  errorType: string | null
  statusCode: number | null
  retryable: boolean
  message: string
}
