import { request, requestRaw } from '@/api/client'
import type { SkillAgentBinding, SkillVersion } from '@/features/skill/types'
import type {
  CapabilityVersion,
  AgentTemplateVersionCreateInput,
  SystemCapabilityCreateInput,
  SystemCapabilityPage,
  SystemCapabilityStatistics,
  SystemCapabilityType,
  McpTemplateVersionInput,
  SystemCapabilityUpdateInput,
  SpaceSkillInstallation,
} from '@/features/system-capability/types'
import type { EntityId } from '@/features/workspace/types'

export function searchSystemCapabilities(
  options: {
    type?: SystemCapabilityType
    status?: 0 | 1
    keyword?: string
    pageNum?: number
    pageSize?: number
    signal?: AbortSignal
  } = {},
): Promise<SystemCapabilityPage> {
  return request<SystemCapabilityPage>({
    method: 'POST',
    url: '/agent/system-capabilities/search',
    data: {
      type: options.type,
      status: options.status,
      keyword: options.keyword || undefined,
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

export function getSystemCapabilityStatistics(): Promise<SystemCapabilityStatistics> {
  return request<SystemCapabilityStatistics>({
    method: 'GET',
    url: '/agent/system-capabilities/statistics',
  })
}

export function listCapabilityVersions(
  type: SystemCapabilityType,
  id: EntityId,
): Promise<CapabilityVersion[]> {
  const urls: Record<SystemCapabilityType, string> = {
    SKILL: `/agent/system-skills/${id}/versions`,
    AGENT_TEMPLATE: `/agent/agent-templates/${id}/versions`,
    MCP_TEMPLATE: `/agent/mcp-templates/${id}/versions`,
  }
  return request<CapabilityVersion[]>({ method: 'GET', url: urls[type] })
}

export function createSystemCapability(
  type: SystemCapabilityType,
  input: SystemCapabilityCreateInput,
): Promise<{ id: EntityId }> {
  const data =
    type === 'SKILL'
      ? { name: input.technicalKey, displayName: input.displayName, description: input.description }
      : type === 'AGENT_TEMPLATE'
        ? {
            name: input.technicalKey,
            displayName: input.displayName,
            description: input.description,
          }
        : {
            serverKey: input.technicalKey,
            displayName: input.displayName,
            description: input.description,
          }
  const urls: Record<SystemCapabilityType, string> = {
    SKILL: '/agent/system-skills',
    AGENT_TEMPLATE: '/agent/agent-templates',
    MCP_TEMPLATE: '/agent/mcp-templates',
  }
  return request<{ id: EntityId }>({ method: 'POST', url: urls[type], data })
}

export function createAgentTemplateVersion(
  templateId: EntityId,
  input: AgentTemplateVersionCreateInput,
): Promise<unknown> {
  return request({
    method: 'POST',
    url: `/agent/agent-templates/${templateId}/versions`,
    data: input,
  })
}

export function updateAgentTemplateVersion(
  versionId: EntityId,
  input: AgentTemplateVersionCreateInput,
): Promise<unknown> {
  return request({
    method: 'PUT',
    url: `/agent/agent-template-versions/${versionId}`,
    data: input,
  })
}

export function disableAgentTemplateVersion(versionId: EntityId): Promise<unknown> {
  return request({
    method: 'POST',
    url: `/agent/agent-template-versions/${versionId}/disable`,
  })
}

export function publishAgentTemplateVersion(versionId: EntityId): Promise<unknown> {
  return request({
    method: 'POST',
    url: `/agent/agent-template-versions/${versionId}/publish`,
  })
}

export function enableAgentTemplateVersion(versionId: EntityId): Promise<unknown> {
  return request({ method: 'POST', url: `/agent/agent-template-versions/${versionId}/enable` })
}

export function createMcpTemplateVersion(
  templateId: EntityId,
  input: McpTemplateVersionInput,
): Promise<CapabilityVersion> {
  return request<CapabilityVersion>({
    method: 'POST',
    url: `/agent/mcp-templates/${templateId}/versions`,
    data: input,
  })
}

export function updateMcpTemplateVersion(
  versionId: EntityId,
  input: McpTemplateVersionInput,
): Promise<CapabilityVersion> {
  return request<CapabilityVersion>({
    method: 'PUT',
    url: `/agent/mcp-template-versions/${versionId}`,
    data: input,
  })
}

export function publishMcpTemplateVersion(versionId: EntityId): Promise<CapabilityVersion> {
  return request<CapabilityVersion>({
    method: 'POST',
    url: `/agent/mcp-template-versions/${versionId}/publish`,
  })
}

export function setMcpTemplateVersionEnabled(
  versionId: EntityId,
  enabled: boolean,
): Promise<CapabilityVersion> {
  return request<CapabilityVersion>({
    method: 'POST',
    url: `/agent/mcp-template-versions/${versionId}/${enabled ? 'enable' : 'disable'}`,
  })
}

export function updateSystemCapabilityTemplate(
  type: Exclude<SystemCapabilityType, 'SKILL'>,
  id: EntityId,
  input: SystemCapabilityUpdateInput,
): Promise<unknown> {
  return request({
    method: 'PUT',
    url: type === 'AGENT_TEMPLATE' ? `/agent/agent-templates/${id}` : `/agent/mcp-templates/${id}`,
    data: input,
  })
}

export function uploadSystemSkillVersion(skillId: EntityId, file: File): Promise<SkillVersion> {
  const data = new FormData()
  data.append('file', file)
  return request<SkillVersion>({
    method: 'POST',
    url: `/agent/system-skills/${skillId}/versions`,
    data,
    timeout: 60_000,
  })
}

export function listSystemSkillVersions(skillId: EntityId): Promise<SkillVersion[]> {
  return request<SkillVersion[]>({
    method: 'GET',
    url: `/agent/system-skills/${skillId}/versions`,
  }).then((versions) => versions ?? [])
}

export function listSystemSkillAgentBindings(skillId: EntityId): Promise<SkillAgentBinding[]> {
  return request<SkillAgentBinding[]>({
    method: 'GET',
    url: `/agent/system-skills/${skillId}/agents`,
  }).then((bindings) => bindings ?? [])
}

export function publishSystemSkillVersion(versionId: EntityId): Promise<SkillVersion> {
  return request<SkillVersion>({
    method: 'POST',
    url: `/agent/system-skill-versions/${versionId}/publish`,
  })
}

export async function downloadSystemSkillVersion(
  skillId: EntityId,
  versionId: EntityId,
  filename: string,
): Promise<void> {
  const response = await requestRaw<Blob>({
    method: 'GET',
    url: `/agent/system-skills/${skillId}/versions/${versionId}/package`,
    responseType: 'blob',
  })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}

export function importSystemSkillPackage(
  file: File,
  metadata?: Pick<SystemCapabilityCreateInput, 'displayName' | 'description'>,
): Promise<unknown> {
  const data = new FormData()
  data.append('file', file)
  if (metadata?.displayName) data.append('displayName', metadata.displayName)
  if (metadata?.description) data.append('description', metadata.description)
  return request({ method: 'POST', url: '/agent/system-skills/import', data, timeout: 60_000 })
}

export function updateSystemSkill(
  skillId: EntityId,
  input: Pick<SystemCapabilityCreateInput, 'technicalKey' | 'displayName' | 'description'>,
): Promise<unknown> {
  return request({
    method: 'PUT',
    url: `/agent/system-skills/${skillId}`,
    data: {
      name: input.technicalKey,
      displayName: input.displayName,
      description: input.description,
    },
  })
}

export function setSystemSkillStatus(skillId: EntityId, enabled: boolean): Promise<void> {
  return request<void>({
    method: 'POST',
    url: `/agent/system-skills/${skillId}/${enabled ? 'enable' : 'disable'}`,
  })
}

export function listSpaceSkillInstallations(spaceId: EntityId): Promise<SpaceSkillInstallation[]> {
  return request<SpaceSkillInstallation[]>({
    method: 'GET',
    url: `/agent/spaces/${spaceId}/skill-installations`,
  }).then((items) => items ?? [])
}

export function installSystemSkill(
  spaceId: EntityId,
  skillId: EntityId,
  skillVersionId: EntityId,
): Promise<SpaceSkillInstallation> {
  return request<SpaceSkillInstallation>({
    method: 'POST',
    url: `/agent/spaces/${spaceId}/skill-installations`,
    data: { skillId, skillVersionId },
  })
}

export function updateSystemSkillInstallation(
  spaceId: EntityId,
  installationId: EntityId,
  input: { skillVersionId?: EntityId; enabled?: boolean },
): Promise<SpaceSkillInstallation> {
  return request<SpaceSkillInstallation>({
    method: 'PUT',
    url: `/agent/spaces/${spaceId}/skill-installations/${installationId}`,
    data: input,
  })
}

export function uninstallSystemSkill(spaceId: EntityId, installationId: EntityId): Promise<void> {
  return request<void>({
    method: 'DELETE',
    url: `/agent/spaces/${spaceId}/skill-installations/${installationId}`,
  })
}

export function installAgentTemplate(
  spaceId: EntityId,
  input: { templateVersionId: EntityId; name?: string; documentScope?: string },
): Promise<unknown> {
  return request({
    method: 'POST',
    url: `/agent/spaces/${spaceId}/agent-installations`,
    data: input,
  })
}

export function installMcpTemplate(
  spaceId: EntityId,
  input: { templateVersionId: EntityId; authToken?: string },
): Promise<unknown> {
  return request({ method: 'POST', url: `/agent/spaces/${spaceId}/mcp-installations`, data: input })
}
