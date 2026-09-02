import JSZip from 'jszip'
import { describe, expect, it } from 'vitest'

import { buildSkillPackage } from './skill-package'

describe('buildSkillPackage', () => {
  it('creates a flat-root Skill ZIP with matching metadata', async () => {
    const file = await buildSkillPackage({
      name: 'document-review',
      activationDescription: '检查文档质量',
      instructions: '请逐项检查事实与结构。',
      allowedTools: ['search__query'],
      files: [
        { path: 'scripts/check.py', content: 'print("ok")' },
        { path: 'references/checklist.md', content: '# 检查清单' },
      ],
    })
    const buffer = await new Promise<ArrayBuffer>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(reader.result as ArrayBuffer)
      reader.onerror = () => reject(reader.error)
      reader.readAsArrayBuffer(file)
    })
    const zip = await JSZip.loadAsync(buffer)
    const content = await zip.file('document-review/SKILL.md')?.async('string')

    expect(file.name).toBe('document-review.zip')
    expect(content).toContain('name: "document-review"')
    expect(content).toContain('description: "检查文档质量"')
    expect(content).toContain('  - "search__query"')
    expect(content).toContain('请逐项检查事实与结构。')
    expect(await zip.file('document-review/scripts/check.py')?.async('string')).toBe('print("ok")')
    expect(await zip.file('document-review/references/checklist.md')?.async('string')).toBe(
      '# 检查清单',
    )
  })

  it('rejects files outside the supported Skill directories', async () => {
    await expect(
      buildSkillPackage({
        name: 'document-review',
        activationDescription: '检查文档质量',
        instructions: '请逐项检查事实与结构。',
        allowedTools: [],
        files: [{ path: 'README.md', content: '不允许放在根目录' }],
      }),
    ).rejects.toThrow('scripts、references、assets 或 examples')
  })
})
