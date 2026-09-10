import { describe, expect, it } from 'vitest'

import { buildDiffHunks, resolveSelectedHunks } from './line-diff'

describe('line diff', () => {
  it('builds stable hunks and resolves a selected change', () => {
    const before = ['标题', '旧段落', '保留', '旧结尾'].join('\n')
    const after = ['标题', '新段落', '保留', '新结尾'].join('\n')
    const hunks = buildDiffHunks(before, after)

    expect(hunks).toHaveLength(1)
    expect(hunks[0]?.added).toBe(2)
    expect(hunks[0]?.removed).toBe(2)
    expect(resolveSelectedHunks(before, after, new Set([hunks[0]!.key]))).toBe(after)
    expect(buildDiffHunks(before, after)[0]?.key).toBe(hunks[0]?.key)
  })

  it('keeps the original text when no hunk is selected', () => {
    expect(resolveSelectedHunks('a\nb', 'a\nc', new Set())).toBe('a\nb')
  })
})
