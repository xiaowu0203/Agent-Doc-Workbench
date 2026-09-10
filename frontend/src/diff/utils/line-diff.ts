export type DiffLineKind = 'context' | 'added' | 'removed'

export interface DiffLine {
  kind: DiffLineKind
  text: string
  oldLine: number | null
  newLine: number | null
  hunkKey: string | null
}

export interface DiffHunk {
  key: string
  lines: DiffLine[]
  added: number
  removed: number
}

type RawLine = Omit<DiffLine, 'hunkKey'>

const MAX_LCS_CELLS = 2_000_000

function linesOf(content: string): string[] {
  return content === '' ? [] : content.split('\n')
}

function rawDiff(oldContent: string, newContent: string): RawLine[] {
  const before = linesOf(oldContent)
  const after = linesOf(newContent)
  if (before.length * after.length > MAX_LCS_CELLS) return coarseDiff(before, after)

  const table = Array.from({ length: before.length + 1 }, () => new Uint32Array(after.length + 1))
  for (let oldIndex = before.length - 1; oldIndex >= 0; oldIndex -= 1) {
    for (let newIndex = after.length - 1; newIndex >= 0; newIndex -= 1) {
      table[oldIndex]![newIndex] =
        before[oldIndex] === after[newIndex]
          ? table[oldIndex + 1]![newIndex + 1]! + 1
          : Math.max(table[oldIndex + 1]![newIndex]!, table[oldIndex]![newIndex + 1]!)
    }
  }

  const result: RawLine[] = []
  let oldIndex = 0
  let newIndex = 0
  while (oldIndex < before.length || newIndex < after.length) {
    if (
      oldIndex < before.length &&
      newIndex < after.length &&
      before[oldIndex] === after[newIndex]
    ) {
      result.push({
        kind: 'context',
        text: before[oldIndex]!,
        oldLine: ++oldIndex,
        newLine: ++newIndex,
      })
    } else if (
      newIndex < after.length &&
      (oldIndex === before.length ||
        table[oldIndex]![newIndex + 1]! >= table[oldIndex + 1]![newIndex]!)
    ) {
      result.push({ kind: 'added', text: after[newIndex]!, oldLine: null, newLine: ++newIndex })
    } else {
      result.push({ kind: 'removed', text: before[oldIndex]!, oldLine: ++oldIndex, newLine: null })
    }
  }
  return result
}

function coarseDiff(before: string[], after: string[]): RawLine[] {
  let prefix = 0
  while (prefix < before.length && prefix < after.length && before[prefix] === after[prefix])
    prefix += 1
  let suffix = 0
  while (
    suffix < before.length - prefix &&
    suffix < after.length - prefix &&
    before[before.length - 1 - suffix] === after[after.length - 1 - suffix]
  )
    suffix += 1
  return [
    ...before.slice(0, prefix).map((text, index) => ({
      kind: 'context' as const,
      text,
      oldLine: index + 1,
      newLine: index + 1,
    })),
    ...before.slice(prefix, before.length - suffix).map((text, index) => ({
      kind: 'removed' as const,
      text,
      oldLine: prefix + index + 1,
      newLine: null,
    })),
    ...after.slice(prefix, after.length - suffix).map((text, index) => ({
      kind: 'added' as const,
      text,
      oldLine: null,
      newLine: prefix + index + 1,
    })),
    ...before.slice(before.length - suffix).map((text, index) => ({
      kind: 'context' as const,
      text,
      oldLine: before.length - suffix + index + 1,
      newLine: after.length - suffix + index + 1,
    })),
  ]
}

function hash(value: string): string {
  let result = 5381
  for (let index = 0; index < value.length; index += 1) {
    result = ((result << 5) + result) ^ value.charCodeAt(index)
  }
  return (result >>> 0).toString(36)
}

export function buildDiffHunks(oldContent: string, newContent: string): DiffHunk[] {
  const lines = rawDiff(oldContent, newContent)
  const hunks: DiffHunk[] = []
  let start = 0
  while (start < lines.length) {
    while (start < lines.length && lines[start]!.kind === 'context') start += 1
    if (start >= lines.length) break
    let end = start
    let contextRun = 0
    while (end + 1 < lines.length) {
      end += 1
      contextRun = lines[end]!.kind === 'context' ? contextRun + 1 : 0
      if (contextRun > 6) {
        end -= contextRun
        break
      }
    }
    const body = lines.slice(start, end + 1).filter((line) => line.kind !== 'context')
    const first = body[0]!
    const signature = body.map((line) => `${line.kind}:${line.text}`).join('\n')
    const key = `h-${first.oldLine ?? 0}-${first.newLine ?? 0}-${hash(signature)}`
    const visibleStart = Math.max(0, start - 3)
    const visibleEnd = Math.min(lines.length, end + 4)
    const hunkLines = lines
      .slice(visibleStart, visibleEnd)
      .map((line) => ({ ...line, hunkKey: key }))
    hunks.push({
      key,
      lines: hunkLines,
      added: body.filter((line) => line.kind === 'added').length,
      removed: body.filter((line) => line.kind === 'removed').length,
    })
    start = end + 1
  }
  return hunks
}

export function resolveSelectedHunks(
  oldContent: string,
  newContent: string,
  selectedKeys: ReadonlySet<string>,
): string {
  const hunks = buildDiffHunks(oldContent, newContent)
  const keyByChange = new Map<string, string>()
  hunks.forEach((hunk) => {
    hunk.lines
      .filter((line) => line.kind !== 'context')
      .forEach((line) => {
        keyByChange.set(
          `${line.kind}:${line.oldLine ?? 0}:${line.newLine ?? 0}:${line.text}`,
          hunk.key,
        )
      })
  })
  const resolved = rawDiff(oldContent, newContent).flatMap((line) => {
    if (line.kind === 'context') return [line.text]
    const key = keyByChange.get(
      `${line.kind}:${line.oldLine ?? 0}:${line.newLine ?? 0}:${line.text}`,
    )
    const accepted = key ? selectedKeys.has(key) : false
    if (line.kind === 'added') return accepted ? [line.text] : []
    return accepted ? [] : [line.text]
  })
  return resolved.join('\n')
}
