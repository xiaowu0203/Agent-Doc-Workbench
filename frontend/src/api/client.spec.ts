import { describe, expect, it } from 'vitest'

import { preserveLargeIntegers } from './client'

describe('preserveLargeIntegers', () => {
  it('preserves large response IDs as strings without corrupting nested JSON strings', () => {
    const raw =
      '{"id":2099000504762310657,"detail":"{\\"templateId\\":2098801005964054530,\\"templateVersionId\\":2099000381747568641}"}'

    const result = JSON.parse(preserveLargeIntegers(raw)) as {
      id: string
      detail: string
    }

    expect(result.id).toBe('2099000504762310657')
    expect(result.detail).toBe(
      '{"templateId":2098801005964054530,"templateVersionId":2099000381747568641}',
    )
  })
})
