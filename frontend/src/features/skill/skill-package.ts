import JSZip from 'jszip'

import type { OnlineSkillPackageInput } from '@/features/skill/types'

function yamlString(value: string): string {
  return JSON.stringify(value)
}

export async function buildSkillPackage(input: OnlineSkillPackageInput): Promise<File> {
  const zip = new JSZip()
  zip.file(`${input.name}/SKILL.md`, buildSkillMarkdown(input))
  const paths = new Set<string>()
  for (const file of input.files ?? []) {
    const path = validateFilePath(file.path)
    const normalizedPath = path.toLowerCase()
    if (paths.has(normalizedPath)) throw new Error(`文件路径重复: ${path}`)
    paths.add(normalizedPath)
    zip.file(`${input.name}/${path}`, file.content)
  }
  const blob = await zip.generateAsync({ type: 'blob', compression: 'DEFLATE' })
  return new File([blob], `${input.name}.zip`, { type: 'application/zip' })
}

export function buildSkillMarkdown(input: OnlineSkillPackageInput): string {
  const tools = input.allowedTools.map((tool) => `  - ${yamlString(tool)}`).join('\n')
  return [
    '---',
    `name: ${yamlString(input.name)}`,
    `description: ${yamlString(input.activationDescription)}`,
    ...(tools ? ['allowed-tools:', tools] : []),
    '---',
    input.instructions.trim(),
    '',
  ].join('\n')
}

function validateFilePath(filePath: string): string {
  const path = filePath.trim().replaceAll('\\', '/')
  const segments = path.split('/')
  const directory = segments[0]
  const extension = path.includes('.') ? path.split('.').pop()?.toLowerCase() : ''
  if (
    !directory ||
    segments.length < 2 ||
    segments.some((segment) => !segment || segment === '.' || segment === '..') ||
    !['scripts', 'references', 'assets', 'examples'].includes(directory)
  ) {
    throw new Error('文件必须放在 scripts、references、assets 或 examples 目录下')
  }
  if (directory === 'scripts' && !['py', 'sh', 'js'].includes(extension ?? '')) {
    throw new Error('scripts 目录只支持 .py、.sh、.js 文件')
  }
  if (
    (directory === 'references' || directory === 'examples') &&
    !['md', 'txt', 'json', 'yaml', 'yml', 'csv'].includes(extension ?? '')
  ) {
    throw new Error('references/examples 目录只支持 md、txt、json、yaml、yml、csv 文件')
  }
  return path
}
