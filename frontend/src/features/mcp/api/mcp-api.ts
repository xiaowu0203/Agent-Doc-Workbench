import { request } from '@/api/client'
import type {
  McpAuthType,
  McpConnectionTestResult,
  McpServer,
  McpServerCreateInput,
  McpServerPage,
  McpServerStatus,
  McpServerUpdateInput,
  McpTool,
} from '@/features/mcp/types'
import type { EntityId } from '@/features/workspace/types'

export function searchMcpServers(
  spaceId: EntityId,
  options: {
    keyword?: string
    status?: McpServerStatus
    authType?: McpAuthType
    pageNum?: number
    pageSize?: number
    signal?: AbortSignal
  } = {},
): Promise<McpServerPage> {
  return request<McpServerPage>({
    method: 'POST',
    url: '/agent/mcp-servers/search',
    data: {
      spaceId,
      keyword: options.keyword || undefined,
      status: options.status,
      authType: options.authType,
      pageNum: options.pageNum ?? 1,
      pageSize: options.pageSize ?? 12,
    },
    signal: options.signal,
  }).then(
    (page) =>
      page ?? {
        records: [],
        total: 0,
        pageNum: options.pageNum ?? 1,
        pageSize: options.pageSize ?? 12,
      },
  )
}

export function createMcpServer(payload: McpServerCreateInput): Promise<McpServer> {
  return request<McpServer>({ method: 'POST', url: '/agent/mcp-servers', data: payload })
}

export function getMcpServer(serverId: EntityId): Promise<McpServer> {
  return request<McpServer>({ method: 'GET', url: `/agent/mcp-servers/${serverId}` })
}

export function updateMcpServer(
  serverId: EntityId,
  payload: McpServerUpdateInput,
): Promise<McpServer> {
  return request<McpServer>({
    method: 'PUT',
    url: `/agent/mcp-servers/${serverId}`,
    data: payload,
  })
}

export function deleteMcpServer(serverId: EntityId): Promise<void> {
  return request<void>({ method: 'DELETE', url: `/agent/mcp-servers/${serverId}` })
}

export function testMcpConnection(serverId: EntityId): Promise<McpConnectionTestResult> {
  return request<McpConnectionTestResult>({
    method: 'POST',
    url: `/agent/mcp-servers/${serverId}/test-connect`,
    timeout: 25_000,
  })
}

export function listMcpTools(serverId: EntityId): Promise<McpTool[]> {
  return request<McpTool[]>({ method: 'GET', url: `/agent/mcp-servers/${serverId}/tools` }).then(
    (tools) => tools ?? [],
  )
}
