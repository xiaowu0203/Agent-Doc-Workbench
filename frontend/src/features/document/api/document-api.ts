import { request, requestRaw } from '@/api/client'
import type { EntityId } from '@/features/workspace/types'
import type {
  CreateDocumentRequest,
  CreateDirectoryRequest,
  DocumentActivity,
  DocumentAsset,
  ArchivedDocument,
  DocumentDraft,
  DirectoryDetail,
  DocumentDetail,
  DocumentTreeNode,
  DocumentVersion,
  DocumentVersionCompare,
  DocumentVersionDetail,
  PageResult,
  SaveDocumentDraftRequest,
  UpdateDirectoryRequest,
  UpdateDocumentRequest,
} from '@/features/document/types'

export function listDocumentTree(
  spaceId: EntityId,
  options: {
    keyword?: string
    docType?: 'FORMAL' | 'DRAFT'
    status?: 'NORMAL' | 'ARCHIVED'
    signal?: AbortSignal
  } = {},
): Promise<DocumentTreeNode[]> {
  return request<DocumentTreeNode[]>({
    method: 'POST',
    url: '/document/documents/tree',
    data: {
      spaceId,
      keyword: options.keyword || undefined,
      docType: options.docType,
      status: options.status,
    },
    signal: options.signal,
  }).then((tree) => tree ?? [])
}

export function getDocument(documentId: EntityId, signal?: AbortSignal): Promise<DocumentDetail> {
  return request<DocumentDetail>({
    method: 'GET',
    url: `/document/documents/${documentId}`,
    signal,
  })
}

export function getDocumentDraft(
  documentId: EntityId,
  signal?: AbortSignal,
): Promise<DocumentDraft | null> {
  return request<DocumentDraft | null>({
    method: 'GET',
    url: `/document/documents/${documentId}/draft`,
    signal,
  })
}

export function saveDocumentDraft(
  documentId: EntityId,
  payload: SaveDocumentDraftRequest,
  signal?: AbortSignal,
): Promise<DocumentDraft> {
  return request<DocumentDraft>({
    method: 'PUT',
    url: `/document/documents/${documentId}/draft`,
    data: payload,
    signal,
  })
}

export function clearDocumentDraft(documentId: EntityId, signal?: AbortSignal): Promise<void> {
  return request<void>({
    method: 'DELETE',
    url: `/document/documents/${documentId}/draft`,
    signal,
  })
}

export function createDocument(
  payload: CreateDocumentRequest,
  signal?: AbortSignal,
): Promise<Pick<DocumentDetail, 'id'>> {
  return request<Pick<DocumentDetail, 'id'>>({
    method: 'POST',
    url: '/document/documents',
    data: payload,
    signal,
  })
}

export function createDirectory(
  payload: CreateDirectoryRequest,
  signal?: AbortSignal,
): Promise<DirectoryDetail> {
  return request<DirectoryDetail>({
    method: 'POST',
    url: '/document/directories',
    data: payload,
    signal,
  })
}

export function archiveDirectory(directoryId: EntityId, signal?: AbortSignal): Promise<void> {
  return request<void>({
    method: 'PUT',
    url: `/document/directories/${directoryId}/archive`,
    signal,
  })
}

export function moveDirectory(
  directoryId: EntityId,
  parentId: EntityId | null,
  signal?: AbortSignal,
): Promise<DirectoryDetail> {
  return request<DirectoryDetail>({
    method: 'PUT',
    url: `/document/directories/${directoryId}/move`,
    data: { parentId },
    signal,
  })
}

export function updateDirectory(
  directoryId: EntityId,
  payload: UpdateDirectoryRequest,
  signal?: AbortSignal,
): Promise<DirectoryDetail> {
  return request<DirectoryDetail>({
    method: 'PUT',
    url: `/document/directories/${directoryId}`,
    data: payload,
    signal,
  })
}

export function updateDocument(
  documentId: EntityId,
  payload: UpdateDocumentRequest,
  signal?: AbortSignal,
): Promise<DocumentDetail> {
  return request<DocumentDetail>({
    method: 'PUT',
    url: `/document/documents/${documentId}`,
    data: payload,
    signal,
  })
}

export function archiveDocument(documentId: EntityId, signal?: AbortSignal): Promise<void> {
  return request<void>({
    method: 'PUT',
    url: `/document/documents/${documentId}/archive`,
    signal,
  })
}

export function moveDocument(
  documentId: EntityId,
  directoryId: EntityId | null,
  signal?: AbortSignal,
): Promise<void> {
  return request<void>({
    method: 'PUT',
    url: `/document/documents/${documentId}/move`,
    data: { directoryId },
    signal,
  })
}

export function restoreDocument(documentId: EntityId, signal?: AbortSignal): Promise<void> {
  return request<void>({
    method: 'PUT',
    url: `/document/documents/${documentId}/restore`,
    signal,
  })
}

export function listArchivedDocuments(
  spaceId: EntityId,
  pageNum = 1,
  pageSize = 10,
  signal?: AbortSignal,
): Promise<PageResult<ArchivedDocument>> {
  return request<PageResult<ArchivedDocument>>({
    method: 'GET',
    url: '/document/documents/trash',
    params: { spaceId, pageNum, pageSize },
    signal,
  })
}

export function listArchivedDirectories(
  spaceId: EntityId,
  pageNum = 1,
  pageSize = 10,
  signal?: AbortSignal,
): Promise<PageResult<DirectoryDetail>> {
  return request<PageResult<DirectoryDetail>>({
    method: 'GET',
    url: '/document/directories/trash',
    params: { spaceId, pageNum, pageSize },
    signal,
  })
}

export function restoreDirectory(directoryId: EntityId, signal?: AbortSignal): Promise<void> {
  return request<void>({
    method: 'PUT',
    url: `/document/directories/${directoryId}/restore`,
    signal,
  })
}

export function listDocumentVersions(
  documentId: EntityId,
  pageSize = 100,
  signal?: AbortSignal,
  pageNum = 1,
): Promise<PageResult<DocumentVersion>> {
  return request<PageResult<DocumentVersion>>({
    method: 'GET',
    url: `/document/documents/${documentId}/versions`,
    params: { pageNum, pageSize },
    signal,
  }).then(
    (page) =>
      page ?? {
        records: [],
        total: 0,
        pageNum,
        pageSize,
      },
  )
}

export function getDocumentVersion(
  documentId: EntityId,
  versionNo: number,
  signal?: AbortSignal,
): Promise<DocumentVersionDetail> {
  return request<DocumentVersionDetail>({
    method: 'GET',
    url: `/document/documents/${documentId}/versions/${versionNo}`,
    signal,
  })
}

export function compareDocumentVersions(
  documentId: EntityId,
  from: number,
  to: number,
  signal?: AbortSignal,
): Promise<DocumentVersionCompare> {
  return request<DocumentVersionCompare>({
    method: 'GET',
    url: `/document/documents/${documentId}/versions/compare`,
    params: { from, to },
    signal,
  })
}

export function rollbackDocumentVersion(
  documentId: EntityId,
  targetVersion: number,
  baseVersion: number,
  signal?: AbortSignal,
): Promise<DocumentDetail> {
  return request<DocumentDetail>({
    method: 'PUT',
    url: `/document/documents/${documentId}/rollback`,
    data: { targetVersion, baseVersion },
    signal,
  })
}

export function listDocumentActivities(
  documentId: EntityId,
  pageSize = 8,
  signal?: AbortSignal,
): Promise<PageResult<DocumentActivity>> {
  return request<PageResult<DocumentActivity>>({
    method: 'POST',
    url: '/task/documents/activity/query',
    data: { documentId, pageParam: { pageNum: 1, pageSize } },
    signal,
  }).then(
    (page) =>
      page ?? {
        records: [],
        total: 0,
        pageNum: 1,
        pageSize,
      },
  )
}

export function uploadDocumentImage(
  documentId: EntityId,
  file: File,
  signal?: AbortSignal,
): Promise<DocumentAsset> {
  const formData = new FormData()
  formData.append('file', file)
  return request<DocumentAsset>({
    method: 'POST',
    url: `/document/documents/${documentId}/assets/images`,
    data: formData,
    signal,
  })
}

export function readDocumentImage(
  documentId: EntityId,
  assetId: EntityId,
  signal?: AbortSignal,
): Promise<Blob> {
  return requestRaw<Blob>({
    method: 'GET',
    url: `/document/documents/${documentId}/assets/${assetId}`,
    responseType: 'blob',
    signal,
  }).then((response) => response.data)
}
