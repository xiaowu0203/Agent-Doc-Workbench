import type { EntityId } from '@/features/workspace/types'

export type WorkbenchSearchType = 'DOCUMENT' | 'TASK' | 'AGENT'

export interface WorkbenchSearchItem {
  type: WorkbenchSearchType
  id: EntityId
  title: string
  subtitle: string | null
  status: string | null
  updatedAt: string | null
}

export interface WorkbenchSearchGroup {
  records: WorkbenchSearchItem[]
  total: number
}

export interface WorkbenchSearchResult {
  documents: WorkbenchSearchGroup
  tasks: WorkbenchSearchGroup
  agents: WorkbenchSearchGroup
}
