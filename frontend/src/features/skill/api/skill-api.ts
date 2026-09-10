import { request, requestRaw } from '@/api/client'
import type {
  Skill,
  SkillAgentBinding,
  SkillImportResult,
  SkillMetadataInput,
  SkillPage,
  SkillStatus,
  SkillVersion,
} from '@/features/skill/types'
import type { EntityId } from '@/features/workspace/types'

export function searchSkills(
  spaceId: EntityId,
  options: {
    keyword?: string
    status?: SkillStatus
    pageNum?: number
    pageSize?: number
    signal?: AbortSignal
  } = {},
): Promise<SkillPage> {
  const status = options.status === 'ACTIVE' ? 1 : options.status === 'DISABLED' ? 0 : undefined
  return request<SkillPage>({
    method: 'POST',
    url: '/agent/skills/search',
    data: {
      spaceId,
      keyword: options.keyword || undefined,
      status,
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

export function createSkill(payload: SkillMetadataInput): Promise<Skill> {
  return request<Skill>({ method: 'POST', url: '/agent/skills', data: payload })
}

export function updateSkill(skillId: EntityId, payload: SkillMetadataInput): Promise<Skill> {
  return request<Skill>({ method: 'PUT', url: `/agent/skills/${skillId}`, data: payload })
}

export function enableSkill(skillId: EntityId): Promise<void> {
  return request<void>({ method: 'POST', url: `/agent/skills/${skillId}/enable` })
}

export function disableSkill(skillId: EntityId): Promise<void> {
  return request<void>({ method: 'POST', url: `/agent/skills/${skillId}/disable` })
}

export function importSkillPackage(
  spaceId: EntityId,
  file: File,
  metadata?: Pick<SkillMetadataInput, 'displayName' | 'description'>,
): Promise<SkillImportResult> {
  const data = new FormData()
  data.append('spaceId', String(spaceId))
  if (metadata?.displayName) data.append('displayName', metadata.displayName)
  if (metadata?.description) data.append('description', metadata.description)
  data.append('file', file)
  return request<SkillImportResult>({
    method: 'POST',
    url: '/agent/skills/import',
    data,
    timeout: 60_000,
  })
}

export function uploadSkillVersion(skillId: EntityId, file: File): Promise<SkillVersion> {
  const data = new FormData()
  data.append('file', file)
  return request<SkillVersion>({
    method: 'POST',
    url: `/agent/skills-versions/${skillId}`,
    data,
    timeout: 60_000,
  })
}

export function listSkillVersions(skillId: EntityId): Promise<SkillVersion[]> {
  return request<SkillVersion[]>({
    method: 'GET',
    url: `/agent/skills-versions/${skillId}`,
  }).then((versions) => versions ?? [])
}

export function publishSkillVersion(skillId: EntityId, versionId: EntityId): Promise<SkillVersion> {
  return request<SkillVersion>({
    method: 'POST',
    url: `/agent/skills-versions/${skillId}/${versionId}/publish`,
  })
}

export function listSkillAgentBindings(skillId: EntityId): Promise<SkillAgentBinding[]> {
  return request<SkillAgentBinding[]>({
    method: 'GET',
    url: `/agent/skills/${skillId}/agents`,
  }).then((bindings) => bindings ?? [])
}

export async function downloadSkillVersion(
  skillId: EntityId,
  versionId: EntityId,
  filename: string,
): Promise<void> {
  const response = await requestRaw<Blob>({
    method: 'GET',
    url: `/agent/skills-versions/${skillId}/${versionId}/package`,
    responseType: 'blob',
  })
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
