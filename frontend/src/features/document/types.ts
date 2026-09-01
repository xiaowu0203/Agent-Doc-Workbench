import type { EntityId } from '@/features/workspace/types'

export type DocumentType = 'FORMAL' | 'DRAFT'
export type DocumentStatus = 'NORMAL' | 'ARCHIVED'
export type DocumentNodeType = 'DOCUMENT' | 'DIRECTORY'

export interface DocumentTreeNode {
  id: EntityId
  parentId: EntityId | null
  title: string
  docType: DocumentType | null
  nodeType: DocumentNodeType
  children: DocumentTreeNode[]
}

export interface DocumentDetail {
  id: EntityId
  spaceId: EntityId
  directoryId: EntityId | null
  title: string
  docType: DocumentType
  content: string | null
  version: number
  status: DocumentStatus
  updatedAt: string | null
  updatedBy: EntityId | null
  createdBy: EntityId | null
  creatorName: string | null
}

export interface DocumentDraft {
  documentId: EntityId
  baseVersion: number
  title: string | null
  content: string
}

export interface SaveDocumentDraftRequest {
  baseVersion: number
  title: string
  content: string
}

export interface DirectoryDetail {
  id: EntityId
  spaceId: EntityId
  parentId: EntityId | null
  title: string
  status: DocumentStatus
  createdAt: string | null
  updatedAt: string | null
}

export interface DocumentVersion {
  id: EntityId
  documentId: EntityId
  versionNo: number
  changeSummary: string | null
  createdBy: EntityId | null
  createdAt: string | null
}

export interface DocumentVersionDetail {
  documentId: EntityId
  versionNo: number
  content: string | null
  changeSummary: string | null
  createdBy: EntityId | null
}

export interface CreateDocumentRequest {
  spaceId: EntityId
  directoryId: EntityId | null
  title: string
  docType: DocumentType
  content: string
}

export interface CreateDirectoryRequest {
  spaceId: EntityId
  parentId: EntityId | null
  title: string
}

export interface UpdateDirectoryRequest {
  title: string
}

export interface UpdateDocumentRequest {
  baseVersion: number
  title?: string
  content?: string
}

export interface DocumentActivity {
  id: EntityId
  type: 'TASK' | 'CHANGE_REQUEST'
  title: string
  status: string | null
  sourceTaskId: EntityId | null
  operatorName: string | null
  activityAt: string | null
}

export interface DocumentAsset {
  id: EntityId
  documentId: EntityId
  originalName: string
  contentType: string
  sizeBytes: number
  url: string
  createdAt: string | null
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNum: number
  pageSize: number
}
