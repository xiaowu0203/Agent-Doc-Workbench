import { beforeEach, describe, expect, it, vi } from 'vitest'

import { request } from '@/api/client'
import { upgradeAgentTemplate } from './agent-api'

vi.mock('@/api/client', () => ({ request: vi.fn() }))

describe('agent-api template upgrade', () => {
  beforeEach(() => vi.mocked(request).mockReset())

  it('previews and applies an upgrade through the same endpoint', async () => {
    vi.mocked(request).mockResolvedValue(undefined)

    await upgradeAgentTemplate(9, { targetVersionId: 22, previewOnly: true })
    await upgradeAgentTemplate(9, {
      targetVersionId: 22,
      previewOnly: false,
      resolvedSkillVersionIds: [31],
      resolvedMcpBindings: [{ mcpServerId: 41, toolWhitelist: null }],
    })

    expect(vi.mocked(request).mock.calls.map(([config]) => config)).toEqual([
      {
        method: 'POST',
        url: '/agent/agents/9/template-upgrades',
        data: { targetVersionId: 22, previewOnly: true },
      },
      {
        method: 'POST',
        url: '/agent/agents/9/template-upgrades',
        data: {
          targetVersionId: 22,
          previewOnly: false,
          resolvedSkillVersionIds: [31],
          resolvedMcpBindings: [{ mcpServerId: 41, toolWhitelist: null }],
        },
      },
    ])
  })
})
