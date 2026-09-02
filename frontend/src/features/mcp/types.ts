import type { EntityId } from '@/features/workspace/types'

export type McpAuthType = 'NONE' | 'BEARER' | 'QUERY_PARAM'
export type McpConnectionStatus = 'UNTESTED' | 'SUCCESS' | 'FAILED'
export type McpServerStatus = 0 | 1

export interface McpServer {
  id: EntityId
  spaceId: EntityId
  serverKey: string
  displayName: string
  endpointUrl: string
  authType: McpAuthType
  authParamName: string | null
  authConfigured: boolean
  configVersion: number
  status: McpServerStatus
  connectionStatus: McpConnectionStatus
  lastTestedAt: string | null
  lastTestDurationMs: number | null
  lastTestError: string | null
  discoveredToolCount: number
  toolsDiscoveredAt: string | null
}

export interface McpTool {
  name: string
  description: string | null
  inputSchema: string | null
}

export interface McpConnectionTestResult {
  serverId: EntityId
  connected: boolean
  status: McpConnectionStatus
  testedAt: string
  durationMs: number
  errorMessage: string | null
  tools: McpTool[]
}

export interface McpServerPage {
  records: McpServer[]
  total: number
  pageNum: number
  pageSize: number
}

export interface McpServerCreateInput {
  spaceId: EntityId
  serverKey: string
  displayName: string
  endpointUrl: string
  authType: McpAuthType
  authParamName?: string
  authToken?: string
}

export interface McpServerUpdateInput {
  displayName: string
  endpointUrl: string
  authType: McpAuthType
  authParamName?: string
  authToken?: string
  status: McpServerStatus
}
