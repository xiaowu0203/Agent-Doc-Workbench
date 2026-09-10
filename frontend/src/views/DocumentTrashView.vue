<template>
  <section class="trash-page">
    <PageHeader title="回收站" description="查看并恢复当前空间已归档的文档和目录">
      <template #breadcrumb><span>文档 / 回收站</span></template>
    </PageHeader>

    <el-tabs v-model="activeTab" class="trash-tabs">
      <el-tab-pane label="归档文档" name="documents">
        <DataState
          :loading="documentLoading"
          :error="documentError"
          :empty="!documents.length"
          empty-text="回收站中没有归档文档"
          @retry="loadDocuments"
        >
          <div class="trash-table surface-card">
            <el-table :data="documents">
              <el-table-column label="文档" min-width="260">
                <template #default="{ row }">
                  <button class="trash-document-link" type="button" @click="openDocument(row.id)">
                    {{ row.title }}
                  </button>
                  <span class="trash-meta">{{
                    row.docType === 'FORMAL' ? '正式文档' : '草稿文档'
                  }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="version" label="版本" width="90" />
              <el-table-column label="归档时间" width="180">
                <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" fixed="right">
                <template #default="{ row }">
                  <el-button
                    link
                    type="primary"
                    :loading="restoringId === `document-${row.id}`"
                    @click="restoreArchivedDocument(row.id)"
                  >
                    恢复
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </DataState>
        <el-pagination
          v-if="documentPage.total"
          class="trash-pagination"
          background
          layout="total, sizes, prev, pager, next"
          :total="documentPage.total"
          :page-size="documentPage.pageSize"
          :current-page="documentPage.pageNum"
          :page-sizes="[10, 20, 50]"
          @current-change="changeDocumentPage"
          @size-change="changeDocumentPageSize"
        />
      </el-tab-pane>

      <el-tab-pane label="归档目录" name="directories">
        <DataState
          :loading="directoryLoading"
          :error="directoryError"
          :empty="!directories.length"
          empty-text="回收站中没有归档目录"
          @retry="loadDirectories"
        >
          <div class="trash-table surface-card">
            <el-table :data="directories">
              <el-table-column prop="title" label="目录" min-width="280" />
              <el-table-column label="原父目录" width="160">
                <template #default="{ row }">
                  {{ parentDirectoryLabel(row) }}
                  <span v-if="row.parentStatus === 'ARCHIVED'" class="trash-meta">（已归档）</span>
                </template>
              </el-table-column>
              <el-table-column label="归档时间" width="180">
                <template #default="{ row }">{{ formatTime(row.updatedAt) }}</template>
              </el-table-column>
              <el-table-column label="操作" width="110" fixed="right">
                <template #default="{ row }">
                  <el-button
                    link
                    type="primary"
                    :loading="restoringId === `directory-${row.id}`"
                    @click="restoreArchivedDirectory(row.id)"
                  >
                    恢复
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </DataState>
        <el-pagination
          v-if="directoryPage.total"
          class="trash-pagination"
          background
          layout="total, sizes, prev, pager, next"
          :total="directoryPage.total"
          :page-size="directoryPage.pageSize"
          :current-page="directoryPage.pageNum"
          :page-sizes="[10, 20, 50]"
          @current-change="changeDirectoryPage"
          @size-change="changeDirectoryPageSize"
        />
      </el-tab-pane>
    </el-tabs>
    <el-dialog
      v-model="detailVisible"
      :title="selectedDocument?.title || '文档内容'"
      width="min(900px, 92vw)"
    >
      <DataState
        :loading="detailLoading"
        :error="detailError"
        :empty="false"
        @retry="reloadDocument"
      >
        <!-- eslint-disable vue/no-v-html -->
        <div
          v-if="selectedDocument?.content"
          class="trash-document-content markdown-preview__body"
          v-html="renderMarkdown(selectedDocument.content)"
        ></div>
        <!-- eslint-enable vue/no-v-html -->
        <div v-else class="trash-document-content">文档暂无正文</div>
      </DataState>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import {
  ElButton,
  ElDialog,
  ElMessage,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElPagination,
} from 'element-plus'
import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import { onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

import { normalizeApiError } from '@/api/errors'
import {
  listArchivedDirectories,
  listArchivedDocuments,
  getDocument,
  restoreDirectory,
  restoreDocument,
} from '@/features/document/api/document-api'
import type { ArchivedDocument, DirectoryDetail } from '@/features/document/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { useWorkspaceStore } from '@/stores/workspace'

const workspaceStore = useWorkspaceStore()
const activeTab = ref<'documents' | 'directories'>('documents')
const documents = ref<ArchivedDocument[]>([])
const directories = ref<DirectoryDetail[]>([])
const documentLoading = ref(false)
const directoryLoading = ref(false)
const documentError = ref('')
const directoryError = ref('')
const restoringId = ref<string | null>(null)
const documentPage = reactive({ total: 0, pageNum: 1, pageSize: 10 })
const directoryPage = reactive({ total: 0, pageNum: 1, pageSize: 10 })
const selectedDocument = ref<(ArchivedDocument & { content?: string | null }) | null>(null)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
let controller: AbortController | null = null
const markdownRenderer = new MarkdownIt({
  html: false,
  breaks: true,
  linkify: true,
}).enable('table')

function formatTime(value: string | null | undefined) {
  return value ? new Date(value).toLocaleString('zh-CN') : '—'
}

function renderMarkdown(markdown: string): string {
  return DOMPurify.sanitize(markdownRenderer.render(markdown.trim()), {
    USE_PROFILES: { html: true },
  })
}

function parentDirectoryLabel(value: unknown): string {
  const directory = value as DirectoryDetail
  if (directory.parentId === null) return '空间根层'
  return directory.parentTitle || '—'
}

async function openDocument(id: EntityId) {
  selectedDocument.value =
    documents.value.find((document) => String(document.id) === String(id)) || null
  detailVisible.value = true
  await reloadDocument()
}

async function reloadDocument() {
  if (!selectedDocument.value) return
  detailLoading.value = true
  detailError.value = ''
  try {
    const detail = await getDocument(selectedDocument.value.id)
    selectedDocument.value = { ...selectedDocument.value, content: detail.content }
  } catch (cause) {
    detailError.value = normalizeApiError(cause).message
  } finally {
    detailLoading.value = false
  }
}

async function loadDocuments() {
  const spaceId = workspaceStore.currentSpaceId
  if (spaceId === null) return
  controller?.abort()
  controller = new AbortController()
  documentLoading.value = true
  documentError.value = ''
  try {
    const page = await listArchivedDocuments(
      spaceId,
      documentPage.pageNum,
      documentPage.pageSize,
      controller.signal,
    )
    documents.value = page.records
    documentPage.total = page.total
  } catch (cause) {
    if (!controller.signal.aborted) documentError.value = normalizeApiError(cause).message
  } finally {
    if (!controller.signal.aborted) documentLoading.value = false
  }
}

async function loadDirectories() {
  const spaceId = workspaceStore.currentSpaceId
  if (spaceId === null) return
  controller?.abort()
  controller = new AbortController()
  directoryLoading.value = true
  directoryError.value = ''
  try {
    const page = await listArchivedDirectories(
      spaceId,
      directoryPage.pageNum,
      directoryPage.pageSize,
      controller.signal,
    )
    directories.value = page.records
    directoryPage.total = page.total
  } catch (cause) {
    if (!controller.signal.aborted) directoryError.value = normalizeApiError(cause).message
  } finally {
    if (!controller.signal.aborted) directoryLoading.value = false
  }
}

async function restoreArchivedDocument(id: EntityId) {
  restoringId.value = `document-${id}`
  try {
    await restoreDocument(id)
    ElMessage.success('文档已恢复')
    await loadDocuments()
  } catch (cause) {
    ElMessage.error(normalizeApiError(cause).message)
  } finally {
    restoringId.value = null
  }
}

async function restoreArchivedDirectory(id: EntityId) {
  restoringId.value = `directory-${id}`
  try {
    await restoreDirectory(id)
    ElMessage.success('目录已恢复')
    await loadDirectories()
  } catch (cause) {
    ElMessage.error(normalizeApiError(cause).message)
  } finally {
    restoringId.value = null
  }
}

function changeDocumentPage(value: number) {
  documentPage.pageNum = value
  void loadDocuments()
}

function changeDocumentPageSize(value: number) {
  documentPage.pageSize = value
  documentPage.pageNum = 1
  void loadDocuments()
}

function changeDirectoryPage(value: number) {
  directoryPage.pageNum = value
  void loadDirectories()
}

function changeDirectoryPageSize(value: number) {
  directoryPage.pageSize = value
  directoryPage.pageNum = 1
  void loadDirectories()
}

watch(activeTab, (tab) => {
  if (tab === 'documents' && !documents.value.length && !documentLoading.value) void loadDocuments()
  if (tab === 'directories' && !directories.value.length && !directoryLoading.value)
    void loadDirectories()
})

onMounted(loadDocuments)
onBeforeUnmount(() => controller?.abort())
</script>

<style scoped>
.trash-page {
  display: grid;
  gap: 20px;
}
.trash-table {
  padding: 4px;
}
.trash-meta {
  display: block;
  margin-top: 5px;
  color: var(--adw-text-muted);
  font-size: 12px;
}
.trash-document-link {
  padding: 0;
  border: 0;
  color: var(--adw-color-primary);
  background: transparent;
  cursor: pointer;
  font: inherit;
  text-align: left;
}
.trash-document-content {
  max-height: 60vh;
  margin: 0;
  overflow: auto;
  word-break: break-word;
  line-height: 1.7;
}
.trash-document-content:not(.markdown-preview__body) {
  white-space: pre-wrap;
}
.trash-document-content :deep(pre) {
  padding: 12px;
  overflow: auto;
  border-radius: 6px;
  background: var(--adw-surface-muted);
}
.trash-pagination {
  justify-self: end;
  margin-top: 16px;
}
</style>
