<template>
  <section class="version-history-page">
    <PageHeader
      title="版本历史"
      description="每次内容变更都会生成不可变快照，可查看、比较并追溯来源"
    >
      <template #breadcrumb>
        <span
          >文档 / {{ document?.docType === 'FORMAL' ? '正式文档' : '草稿' }} /
          {{ document?.title || '文档' }} / 版本历史</span
        >
      </template>
      <template #actions>
        <el-button :icon="Back" @click="openEditor">返回编辑器</el-button>
      </template>
    </PageHeader>

    <DataState
      :loading="loading"
      :error="error"
      :empty="!versions.length"
      loading-text="正在加载版本历史"
      empty-text="该文档暂无版本记录"
      @retry="loadPage"
    >
      <div class="version-history-grid">
        <section class="timeline-panel surface-card">
          <header class="panel-heading">
            <strong>版本时间线</strong>
            <span>{{ versions.length }} 个版本</span>
          </header>
          <div class="version-timeline">
            <article
              v-for="version in versions"
              :key="String(version.id)"
              class="version-card"
              :class="{ 'is-active': version.versionNo === selectedVersionNo }"
              @click="selectVersion(version.versionNo)"
            >
              <span class="version-node">v{{ version.versionNo }}</span>
              <div class="version-card__content">
                <div class="version-card__title">
                  <el-tag v-if="version.versionNo === document?.version" size="small"
                    >当前版本</el-tag
                  >
                  <strong :title="version.changeSummary || sourceLabel(version.sourceType)">
                    {{ version.changeSummary || sourceLabel(version.sourceType) }}
                  </strong>
                </div>
                <dl>
                  <dt>来源</dt>
                  <dd>
                    <span :class="`source source--${version.sourceType.toLowerCase()}`">{{
                      sourceLabel(version.sourceType)
                    }}</span>
                  </dd>
                  <template v-if="version.agentName">
                    <dt>Agent</dt>
                    <dd>{{ version.agentName }}</dd>
                  </template>
                  <template v-else>
                    <dt>操作者</dt>
                    <dd>{{ version.actorName || '—' }}</dd>
                  </template>
                  <template v-if="version.reviewedByName">
                    <dt>审批人</dt>
                    <dd>{{ version.reviewedByName }}</dd>
                  </template>
                  <template v-if="version.sourceTaskId">
                    <dt>关联任务</dt>
                    <dd>
                      <button class="text-link" @click.stop="openTask(version.sourceTaskId)">
                        {{ version.taskNo || `任务 ${version.sourceTaskId}` }}
                      </button>
                    </dd>
                  </template>
                  <template v-if="version.tokensUsed != null">
                    <dt>Token</dt>
                    <dd>
                      {{ formatNumber(version.tokensUsed)
                      }}<small v-if="version.tokensEstimated">（估算）</small>
                    </dd>
                  </template>
                  <dt>时间</dt>
                  <dd>{{ formatTime(version.createdAt) }}</dd>
                </dl>
              </div>
              <div class="version-card__actions">
                <el-button size="small" @click.stop="selectVersion(version.versionNo)"
                  >查看</el-button
                >
                <el-button size="small" @click.stop="compareWithCurrent(version.versionNo)"
                  >与当前版本比较</el-button
                >
                <el-button
                  v-if="canEdit && version.versionNo !== document?.version"
                  size="small"
                  :loading="rollbackLoading && rollbackTarget === version.versionNo"
                  @click.stop="rollback(version.versionNo)"
                  >基于此版本创建回滚版本</el-button
                >
              </div>
            </article>
          </div>
        </section>

        <div class="version-history-main">
          <section class="detail-panel surface-card">
            <header class="panel-heading">
              <strong>版本详情</strong>
              <el-button
                v-if="selectedDetail?.executionAvailable && selectedDetail.sourceTaskId"
                size="small"
                :icon="Document"
                @click="openTask(selectedDetail.sourceTaskId)"
                >查看执行快照</el-button
              >
            </header>
            <DataState
              :loading="detailLoading"
              :error="detailError"
              :empty="!selectedDetail"
              @retry="reloadDetail"
            >
              <dl v-if="selectedDetail" class="detail-grid">
                <dt>版本号</dt>
                <dd>v{{ selectedDetail.versionNo }}</dd>
                <dt>内容 SHA-256</dt>
                <dd class="hash-value">
                  <code :title="selectedDetail.contentSha256 || ''">{{
                    selectedDetail.contentSha256 || '—'
                  }}</code
                  ><el-button v-if="selectedDetail.contentSha256" link @click="copyHash"
                    >复制</el-button
                  >
                </dd>
                <dt>字数</dt>
                <dd>{{ formatNumber(characterCount) }} 字</dd>
                <dt>来源</dt>
                <dd>{{ sourceLabel(selectedDetail.sourceType) }}</dd>
                <dt>操作者</dt>
                <dd>{{ selectedDetail.actorName || '—' }}</dd>
                <template v-if="selectedDetail.agentName"
                  ><dt>Agent</dt>
                  <dd>{{ selectedDetail.agentName }}</dd></template
                >
                <template v-if="selectedDetail.triggeredByName"
                  ><dt>触发用户</dt>
                  <dd>{{ selectedDetail.triggeredByName }}</dd></template
                >
                <template v-if="selectedDetail.reviewedByName"
                  ><dt>审批人</dt>
                  <dd>{{ selectedDetail.reviewedByName }}</dd>
                  <dt>审批时间</dt>
                  <dd>{{ formatTime(selectedDetail.reviewedAt) }}</dd></template
                >
                <template v-if="selectedDetail.rollbackFromVersion != null"
                  ><dt>回滚来源</dt>
                  <dd>v{{ selectedDetail.rollbackFromVersion }}</dd></template
                >
                <template v-if="selectedDetail.sourceTaskId"
                  ><dt>关联任务</dt>
                  <dd>
                    <button class="text-link" @click="openTask(selectedDetail.sourceTaskId)">
                      {{ selectedDetail.taskNo || `任务 ${selectedDetail.sourceTaskId}` }}</button
                    ><span v-if="selectedDetail.taskName"> · {{ selectedDetail.taskName }}</span>
                  </dd></template
                >
                <dt>Token 使用量</dt>
                <dd>
                  {{
                    selectedDetail.tokensUsed == null
                      ? '—'
                      : formatNumber(selectedDetail.tokensUsed)
                  }}<small v-if="selectedDetail.tokensEstimated">（估算）</small>
                </dd>
                <dt>生成时间</dt>
                <dd>{{ formatTime(selectedDetail.createdAt) }}</dd>
              </dl>
            </DataState>
          </section>

          <section class="compare-panel surface-card">
            <header class="compare-heading">
              <strong>版本对比</strong>
              <div class="compare-selectors">
                <el-select v-model="compareFrom" aria-label="对比源版本" @change="loadComparison">
                  <el-option
                    v-for="version in versions"
                    :key="`from-${version.versionNo}`"
                    :label="`v${version.versionNo}`"
                    :value="version.versionNo"
                  />
                </el-select>
                <span>到</span>
                <el-select v-model="compareTo" aria-label="对比目标版本" @change="loadComparison">
                  <el-option
                    v-for="version in versions"
                    :key="`to-${version.versionNo}`"
                    :label="`v${version.versionNo}`"
                    :value="version.versionNo"
                  />
                </el-select>
              </div>
            </header>
            <div v-if="comparison" class="diff-summary">
              <span
                >新增 <b>+{{ diffStats.added }}</b> 行</span
              >
              <span
                >删除 <i>-{{ diffStats.removed }}</i> 行</span
              >
              <span class="diff-summary__words">{{ signed(diffCharacterDelta) }} 字</span>
            </div>
            <DataState
              :loading="compareLoading"
              :error="compareError"
              :empty="!comparison"
              @retry="loadComparison"
            >
              <DocumentDiff
                v-if="comparison"
                v-model:selected-keys="selectedKeys"
                :old-content="comparison.from.content || ''"
                :new-content="comparison.to.content || ''"
                mode="inline"
              />
            </DataState>
          </section>
        </div>
      </div>
    </DataState>

    <el-alert
      class="history-note"
      type="info"
      :closable="false"
      show-icon
      title="回滚会创建新版本，原版本、审批记录和审计日志保持不变"
    />
  </section>
</template>

<script setup lang="ts">
import { Back, Document } from '@element-plus/icons-vue'
import { ElAlert, ElButton, ElMessage, ElMessageBox, ElOption, ElSelect, ElTag } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import DocumentDiff from '@/diff/components/DocumentDiff.vue'
import { buildDiffHunks } from '@/diff/utils/line-diff'
import {
  compareDocumentVersions,
  getDocument,
  getDocumentVersion,
  listDocumentVersions,
  rollbackDocumentVersion,
} from '@/features/document/api/document-api'
import type {
  DocumentDetail,
  DocumentVersion,
  DocumentVersionCompare,
  DocumentVersionDetail,
  DocumentVersionSourceType,
} from '@/features/document/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const loading = ref(false)
const error = ref('')
const document = ref<DocumentDetail | null>(null)
const versions = ref<DocumentVersion[]>([])
const selectedVersionNo = ref<number | null>(null)
const selectedDetail = ref<DocumentVersionDetail | null>(null)
const detailLoading = ref(false)
const detailError = ref('')
const compareFrom = ref<number | null>(null)
const compareTo = ref<number | null>(null)
const comparison = ref<DocumentVersionCompare | null>(null)
const compareLoading = ref(false)
const compareError = ref('')
const selectedKeys = ref<string[]>([])
const rollbackLoading = ref(false)
const rollbackTarget = ref<number | null>(null)
let pageController: AbortController | null = null
let detailController: AbortController | null = null
let compareController: AbortController | null = null

const canEdit = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.DOCUMENT_EDIT))
const documentId = computed<EntityId>(() => String(route.params.documentId))
const spaceId = computed<EntityId>(() => String(route.params.spaceId))
const characterCount = computed(
  () => (selectedDetail.value?.content || '').replace(/\s/g, '').length,
)
const diffHunks = computed(() =>
  comparison.value
    ? buildDiffHunks(comparison.value.from.content || '', comparison.value.to.content || '')
    : [],
)
const diffStats = computed(() =>
  diffHunks.value.reduce(
    (sum, hunk) => ({ added: sum.added + hunk.added, removed: sum.removed + hunk.removed }),
    { added: 0, removed: 0 },
  ),
)
const diffCharacterDelta = computed(() => {
  if (!comparison.value) return 0
  return (
    (comparison.value.to.content || '').replace(/\s/g, '').length -
    (comparison.value.from.content || '').replace(/\s/g, '').length
  )
})

onMounted(loadPage)
onBeforeUnmount(() => {
  pageController?.abort()
  detailController?.abort()
  compareController?.abort()
})

async function loadPage(): Promise<void> {
  pageController?.abort()
  const controller = new AbortController()
  pageController = controller
  loading.value = true
  error.value = ''
  try {
    const [documentDetail, page] = await Promise.all([
      getDocument(documentId.value, controller.signal),
      listAllDocumentVersions(documentId.value, controller.signal),
    ])
    if (controller.signal.aborted) return
    document.value = documentDetail
    versions.value = [...page.records].sort((left, right) => right.versionNo - left.versionNo)
    const selected =
      versions.value.find((version) => version.versionNo === documentDetail.version) ||
      versions.value[0]
    selectedVersionNo.value = selected?.versionNo ?? null
    const latest = versions.value[0]
    const previous = versions.value[1] || latest
    compareFrom.value = previous?.versionNo ?? null
    compareTo.value = latest?.versionNo ?? null
    await Promise.all([reloadDetail(), loadComparison()])
  } catch (loadError) {
    if (!controller.signal.aborted) error.value = normalizeApiError(loadError).message
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

async function listAllDocumentVersions(
  id: EntityId,
  signal: AbortSignal,
): Promise<{ records: DocumentVersion[]; total: number }> {
  const first = await listDocumentVersions(id, 100, signal, 1)
  const records = [...first.records]
  const pageCount = Math.ceil(first.total / first.pageSize)
  for (let pageNum = 2; pageNum <= pageCount; pageNum += 1) {
    const page = await listDocumentVersions(id, first.pageSize, signal, pageNum)
    records.push(...page.records)
  }
  return { records, total: first.total }
}

async function selectVersion(versionNo: number): Promise<void> {
  if (selectedVersionNo.value === versionNo && selectedDetail.value) return
  selectedVersionNo.value = versionNo
  await reloadDetail()
}

async function reloadDetail(): Promise<void> {
  if (selectedVersionNo.value === null) return
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  detailLoading.value = true
  detailError.value = ''
  try {
    selectedDetail.value = await getDocumentVersion(
      documentId.value,
      selectedVersionNo.value,
      controller.signal,
    )
  } catch (loadError) {
    if (!controller.signal.aborted) detailError.value = normalizeApiError(loadError).message
  } finally {
    if (!controller.signal.aborted) detailLoading.value = false
  }
}

async function loadComparison(): Promise<void> {
  if (compareFrom.value === null || compareTo.value === null) return
  compareController?.abort()
  const controller = new AbortController()
  compareController = controller
  compareLoading.value = true
  compareError.value = ''
  selectedKeys.value = []
  try {
    comparison.value = await compareDocumentVersions(
      documentId.value,
      compareFrom.value,
      compareTo.value,
      controller.signal,
    )
  } catch (loadError) {
    if (!controller.signal.aborted) compareError.value = normalizeApiError(loadError).message
  } finally {
    if (!controller.signal.aborted) compareLoading.value = false
  }
}

function compareWithCurrent(versionNo: number): void {
  compareFrom.value = versionNo
  compareTo.value = document.value?.version ?? versions.value[0]?.versionNo ?? versionNo
  void loadComparison()
}

async function rollback(versionNo: number): Promise<void> {
  if (!document.value || !canEdit.value || versionNo === document.value.version) return
  try {
    await ElMessageBox.confirm(
      `将 v${versionNo} 的内容保存为新的最新版本，原有历史不会被覆盖。确定继续吗？`,
      '创建回滚版本',
      { type: 'warning', confirmButtonText: '确认回滚', cancelButtonText: '取消' },
    )
    rollbackLoading.value = true
    rollbackTarget.value = versionNo
    await rollbackDocumentVersion(document.value.id, versionNo, document.value.version)
    ElMessage.success(`已基于 v${versionNo} 创建新的回滚版本`)
    await loadPage()
  } catch (rollbackError) {
    if (rollbackError !== 'cancel' && rollbackError !== 'close')
      ElMessage.error(normalizeApiError(rollbackError).message)
  } finally {
    rollbackLoading.value = false
    rollbackTarget.value = null
  }
}

function openEditor(): void {
  void router.push({
    name: 'space-documents',
    params: { spaceId: spaceId.value, documentId: documentId.value },
  })
}

function openTask(taskId: EntityId | null): void {
  if (taskId === null) return
  void router.push({ name: 'space-task-detail', params: { spaceId: spaceId.value, taskId } })
}

async function copyHash(): Promise<void> {
  if (!selectedDetail.value?.contentSha256) return
  await globalThis.navigator.clipboard.writeText(selectedDetail.value.contentSha256)
  ElMessage.success('SHA-256 已复制')
}

function sourceLabel(source: DocumentVersionSourceType): string {
  return {
    UNKNOWN: '历史版本',
    CREATE: '创建文档',
    HUMAN_EDIT: '人工编辑',
    AGENT_DRAFT: 'Agent 草稿',
    APPROVAL_MERGE: 'Agent 变更审批',
    ROLLBACK: '版本回滚',
  }[source]
}

function formatNumber(value: number): string {
  return new Intl.NumberFormat('zh-CN').format(value)
}
function signed(value: number): string {
  return value > 0 ? `+${formatNumber(value)}` : formatNumber(value)
}
function formatTime(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        hour12: false,
      }).format(date)
}
</script>

<style scoped>
.version-history-page {
  display: grid;
  gap: 18px;
}
.version-history-page :deep(.page-header__copy > span) {
  display: block;
  margin-bottom: 12px;
  color: var(--adw-text-tertiary);
  font-size: 13px;
}
.version-history-grid {
  display: grid;
  grid-template-columns: minmax(420px, 0.9fr) minmax(520px, 1.4fr);
  gap: 18px;
  align-items: start;
}
.timeline-panel,
.detail-panel,
.compare-panel {
  min-width: 0;
  overflow: hidden;
}
.panel-heading,
.compare-heading {
  display: flex;
  min-height: 58px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid var(--adw-border-color-light);
}
.panel-heading > span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.version-timeline {
  position: relative;
  display: grid;
  gap: 12px;
  padding: 14px;
}
.version-timeline::before {
  position: absolute;
  top: 34px;
  bottom: 34px;
  left: 44px;
  width: 1px;
  background: #cbd5e1;
  content: '';
}
.version-card {
  position: relative;
  display: grid;
  min-width: 0;
  grid-template-columns: 86px minmax(0, 1fr) 168px;
  gap: 12px;
  padding: 14px 12px;
  border: 1px solid var(--adw-border-color);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
}
.version-card.is-active {
  border-color: #7aa2ff;
  box-shadow: 0 0 0 2px rgb(36 91 219 / 8%);
}
.version-node {
  position: relative;
  z-index: 1;
  display: inline-flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: #8d98aa;
  font-size: 13px;
  font-weight: 700;
}
.version-card.is-active .version-node {
  background: var(--adw-color-primary);
}
.version-card__content {
  min-width: 0;
}
.version-card__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  margin-bottom: 9px;
}
.version-card__title strong {
  min-width: 0;
  overflow-wrap: anywhere;
  line-height: 1.45;
}
.version-card dl {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 5px 8px;
  margin: 0;
  font-size: 12px;
}
.version-card dt {
  color: var(--adw-text-tertiary);
}
.version-card dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}
.version-card small,
.detail-grid small {
  color: var(--adw-text-tertiary);
}
.version-card__actions {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: stretch;
  gap: 7px;
}
.version-card__actions :deep(.el-button) {
  width: 100%;
  margin-left: 0;
  white-space: normal;
}
.source {
  color: var(--adw-text-secondary);
}
.source--approval_merge,
.source--agent_draft {
  color: var(--adw-color-primary);
}
.source--human_edit {
  color: var(--adw-color-success);
}
.version-history-main {
  display: grid;
  min-width: 0;
  gap: 18px;
}
.detail-grid {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  gap: 10px 14px;
  margin: 0;
  padding: 16px;
  font-size: 13px;
}
.detail-grid dt {
  color: var(--adw-text-secondary);
}
.detail-grid dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}
.hash-value {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}
.hash-value code {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.text-link {
  padding: 0;
  border: 0;
  color: var(--adw-color-primary);
  background: transparent;
  cursor: pointer;
}
.compare-heading {
  align-items: center;
}
.compare-selectors {
  display: flex;
  min-width: 320px;
  align-items: center;
  gap: 9px;
}
.compare-selectors :deep(.el-select) {
  flex: 1;
}
.diff-summary {
  display: flex;
  gap: 28px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--adw-border-color-light);
  background: var(--adw-surface-muted);
  font-size: 12px;
}
.diff-summary b {
  color: var(--adw-color-success);
}
.diff-summary i {
  color: var(--adw-color-danger);
  font-style: normal;
  font-weight: 700;
}
.diff-summary__words {
  margin-left: auto;
}
.compare-panel :deep(.document-diff) {
  max-height: 620px;
  overflow: auto;
}
.history-note {
  width: auto;
}
@media (max-width: 1180px) {
  .version-history-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 720px) {
  .version-card {
    grid-template-columns: 58px minmax(0, 1fr);
  }
  .version-card__actions {
    grid-column: 2;
  }
  .compare-heading {
    align-items: flex-start;
    flex-direction: column;
    padding-block: 12px;
  }
  .compare-selectors {
    width: 100%;
    min-width: 0;
  }
  .detail-grid {
    grid-template-columns: 90px minmax(0, 1fr);
  }
}
</style>
