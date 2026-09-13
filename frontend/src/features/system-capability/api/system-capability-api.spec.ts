import { beforeEach, describe, expect, it, vi } from 'vitest'

import { request } from '@/api/client'
import {
  createMcpTemplateVersion,
  importSystemSkillPackage,
  installAgentTemplate,
  setMcpTemplateVersionEnabled,
  updateMcpTemplateVersion,
} from './system-capability-api'

vi.mock('@/api/client', () => ({
  request: vi.fn(),
  requestRaw: vi.fn(),
}))

describe('system-capability-api', () => {
  beforeEach(() => {
    vi.mocked(request).mockReset()
    vi.mocked(request).mockResolvedValue(undefined)
  })

  it('uploads a system Skill package to the import POST endpoint', async () => {
    const file = new File(['zip'], 'sample.zip', { type: 'application/zip' })

    await importSystemSkillPackage(file, { displayName: '示例 Skill', description: '说明' })

    const config = vi.mocked(request).mock.calls[0]?.[0]
    expect(config?.method).toBe('POST')
    expect(config?.url).toBe('/agent/system-skills/import')
    expect(config?.data).toBeInstanceOf(FormData)
  })

  it('uses the dedicated MCP draft update and lifecycle endpoints', async () => {
    const input = {
      displayName: '检索服务',
      endpointUrl: 'https://example.com/mcp',
      authType: 'NONE' as const,
    }

    await createMcpTemplateVersion(11, input)
    await updateMcpTemplateVersion(31, input)
    await setMcpTemplateVersionEnabled(31, false)
    await setMcpTemplateVersionEnabled(31, true)

    expect(vi.mocked(request).mock.calls.map(([config]) => [config.method, config.url])).toEqual([
      ['POST', '/agent/mcp-templates/11/versions'],
      ['PUT', '/agent/mcp-template-versions/31'],
      ['POST', '/agent/mcp-template-versions/31/disable'],
      ['POST', '/agent/mcp-template-versions/31/enable'],
    ])
  })

  it('installs an Agent template version into the selected space', async () => {
    await installAgentTemplate(7, { templateVersionId: 21, name: '空间 Agent' })

    expect(request).toHaveBeenCalledWith({
      method: 'POST',
      url: '/agent/spaces/7/agent-installations',
      data: { templateVersionId: 21, name: '空间 Agent' },
    })
  })
})
