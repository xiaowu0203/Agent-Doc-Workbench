<template>
  <section class="approval-page">
    <PageHeader title="变更审批" description="审阅 Agent 或成员提交的文档变更，确认后写入正式版本">
      <template #breadcrumb><span>工作台 / 变更审批</span></template>
      <template #actions><el-button :icon="Refresh" @click="loadQueue">刷新</el-button></template>
    </PageHeader>

    <div class="approval-summary">
      <div>
        <strong>{{ stats.pendingCount }}</strong
        ><span>待处理</span>
      </div>
      <div>
        <strong>{{ stats.assignedToMeCount }}</strong
        ><span>我认领的</span>
      </div>
      <div>
        <strong>{{ stats.unassignedCount }}</strong
        ><span>未认领</span>
      </div>
      <div>
        <strong>{{ stats.pendingCountAsOfYesterday }}</strong
        ><span>昨日以前</span>
      </div>
    </div>

    <div class="approval-toolbar surface-card">
      <el-segmented v-model="statusFilter" :options="statusOptions" @change="applyFilters" />
      <el-checkbox v-model="assignedToMe" @change="applyFilters">只看我认领</el-checkbox>
      <span class="approval-toolbar__spacer" />
      <template v-if="hasPendingSelection && canReview">
        <span>已选 {{ selectedIds.length }} 项</span>
        <el-button size="small" @click="runBatch(canMerge ? 'accept' : 'approve')">
          {{ canMerge ? '批量接受并合并' : '批量通过' }}
        </el-button>
        <el-button size="small" type="danger" plain @click="runBatch('reject')">批量拒绝</el-button>
      </template>
      <el-button v-if="hasApprovedSelection && canMerge" size="small" @click="runBatch('merge')"
        >批量合并</el-button
      >
    </div>

    <div class="approval-workbench surface-card">
      <aside class="approval-queue">
        <header class="pane-heading">
          <strong>审批队列</strong><span>{{ page.total }} 条</span>
        </header>
        <DataState
          :loading="queueLoading"
          :error="queueError"
          :empty="!requests.length"
          empty-text="当前筛选条件下暂无变更"
          @retry="loadQueue"
        >
          <div class="queue-list">
            <div
              v-for="item in requests"
              :key="String(item.id)"
              class="queue-card"
              :class="{ 'is-active': String(item.id) === String(selectedId) }"
              role="button"
              tabindex="0"
              @click="selectRequest(item.id)"
              @keyup.enter="selectRequest(item.id)"
            >
              <el-checkbox
                :model-value="selectedIds.some((id) => String(id) === String(item.id))"
                @click.stop
                @change="toggleBatch(item.id, Boolean($event))"
              />
              <span class="queue-card__body">
                <span class="queue-card__top">
                  <strong>{{ item.documentTitle || '未命名文档' }}</strong>
                  <el-tag size="small" :type="statusType(item.status)">{{
                    statusLabel(item.status)
                  }}</el-tag>
                </span>
                <span
                  class="queue-card__summary"
                  :title="item.summary || item.taskName || '未填写变更摘要'"
                >
                  {{ item.summary || item.taskName || '未填写变更摘要' }}
                </span>
                <span class="queue-card__meta">
                  {{ item.agentName || item.assignedReviewerName || '成员提交' }}
                  · {{ formatTime(item.createdAt) }}
                  <i v-if="item.revisionNo > 1">第 {{ item.revisionNo }} 版</i>
                </span>
              </span>
            </div>
          </div>
        </DataState>
        <footer v-if="page.total > page.pageSize" class="queue-pagination">
          <el-pagination
            v-model:current-page="page.pageNum"
            small
            layout="prev, pager, next"
            :page-size="page.pageSize"
            :total="page.total"
            @change="loadQueue"
          />
        </footer>
      </aside>

      <main class="approval-diff">
        <DataState
          :loading="detailLoading"
          :error="detailError"
          :empty="!detail"
          empty-text="从左侧选择一条变更请求"
          @retry="reloadDetail"
        >
          <template v-if="detail">
            <header class="diff-heading">
              <div>
                <div class="diff-heading__title">
                  <h2>{{ detail.documentTitle }}</h2>
                  <el-tag :type="statusType(detail.status)">{{
                    statusLabel(detail.status)
                  }}</el-tag>
                </div>
                <p :title="detail.summary || detail.taskResultSummary || '未填写变更摘要'">
                  {{ detail.summary || detail.taskResultSummary || '未填写变更摘要' }}
                </p>
              </div>
              <el-radio-group v-model="diffMode" size="small">
                <el-radio-button value="inline">行内</el-radio-button>
                <el-radio-button value="split">并排</el-radio-button>
              </el-radio-group>
            </header>

            <el-alert
              v-if="detail.conflicted"
              type="error"
              :closable="false"
              show-icon
              title="文档版本已变化，当前请求不能直接合并"
              :description="`请求基于 v${detail.baseVersion}，当前文档为 v${detail.currentVersion}。请退回重做，或刷新详情后重新评估。`"
            />
            <div class="version-strip">
              <span
                >基线版本 <b>v{{ detail.baseVersion }}</b></span
              >
              <span
                >当前版本 <b>v{{ detail.currentVersion }}</b></span
              >
              <span v-if="detail.expectedVersion"
                >合并后 <b>v{{ detail.expectedVersion }}</b></span
              >
              <span>+{{ diffStats.added }} / -{{ diffStats.removed }} 行</span>
            </div>

            <el-input
              v-if="decisionMode === 'EDITED' && detail.status === 'PENDING'"
              v-model="editedContent"
              class="content-editor"
              type="textarea"
              :autosize="{ minRows: 18, maxRows: 36 }"
              placeholder="编辑最终要写入文档的 Markdown"
            />
            <DocumentDiff
              v-else
              v-model:selected-keys="selectedHunkKeys"
              :old-content="detail.baseContent"
              :new-content="displayContent"
              :mode="diffMode"
              :selectable="detail.status === 'PENDING' && decisionMode === 'PARTIAL'"
              :commentable="canReview"
              @comment="addHunkComment"
            />
          </template>
        </DataState>
      </main>

      <aside class="approval-inspector">
        <header class="pane-heading"><strong>审批信息</strong></header>
        <DataState :loading="detailLoading" :empty="!detail" empty-text="暂无审批详情">
          <template v-if="detail">
            <section class="inspector-section source-card">
              <h3>来源</h3>
              <dl>
                <dt>触发用户</dt>
                <dd>{{ detail.triggeredByName || '—' }}</dd>
                <dt>Agent</dt>
                <dd>{{ detail.agentName || '非 Agent 提交' }}</dd>
                <dt>任务</dt>
                <dd>
                  <span v-if="detail.sourceTaskId" class="task-reference">
                    <button class="text-link" @click="openTask(detail.sourceTaskId)">
                      {{ detail.taskNo || `任务 ${detail.sourceTaskId}` }}
                    </button>
                    <span v-if="detail.taskName" class="task-reference__name">
                      {{ detail.taskName }}
                    </span>
                  </span>
                  <span v-else>—</span>
                </dd>
                <dt>Token</dt>
                <dd>
                  {{ detail.tokensUsed == null ? '—' : formatTokens(detail.tokensUsed)
                  }}<small v-if="detail.tokensEstimated">（估算）</small>
                </dd>
                <dt>提交时间</dt>
                <dd>{{ formatTime(detail.createdAt) }}</dd>
              </dl>
            </section>

            <section v-if="detail.status === 'PENDING' && canReview" class="inspector-section">
              <h3>处理方式</h3>
              <el-radio-group v-model="decisionMode" class="decision-modes">
                <el-radio value="ALL">接受全部</el-radio>
                <el-radio value="PARTIAL">部分接受</el-radio>
                <el-radio value="EDITED">修改后接受</el-radio>
              </el-radio-group>
              <p v-if="decisionMode === 'PARTIAL'" class="selection-tip">
                已选择 {{ selectedHunkKeys.length }} / {{ diffHunks.length }} 个变更块
              </p>
            </section>

            <section class="inspector-section">
              <h3>审批意见</h3>
              <el-input
                v-model="reviewComment"
                type="textarea"
                :rows="4"
                maxlength="500"
                show-word-limit
                :disabled="detail.status !== 'PENDING'"
                placeholder="填写通过意见、拒绝原因或退回修改要求"
              />
              <div v-if="detail.status === 'PENDING' && canReview" class="decision-actions">
                <el-button v-if="!detail.assignedReviewerId" @click="claim">认领</el-button>
                <el-button v-else-if="isAssignedToMe" @click="unclaim">释放</el-button>
                <el-button
                  type="primary"
                  :loading="acting"
                  :disabled="detail.conflicted"
                  @click="accept"
                >
                  {{ canMerge ? '接受并合并' : '审批通过' }}
                </el-button>
                <el-button :loading="acting" @click="returnForRework">退回重改</el-button>
                <el-button type="danger" plain :loading="acting" @click="reject">拒绝</el-button>
              </div>
              <el-button
                v-if="detail.status === 'APPROVED' && canMerge"
                type="primary"
                :loading="acting"
                :disabled="detail.conflicted"
                @click="merge"
                >合并到正式文档</el-button
              >
              <p v-if="detail.reviewComment" class="saved-comment">
                审批意见：{{ detail.reviewComment }}
              </p>
              <button
                v-if="detail.reworkTaskId"
                class="text-link"
                @click="openTask(detail.reworkTaskId)"
              >
                查看重改任务
              </button>
            </section>

            <section class="inspector-section">
              <h3>讨论</h3>
              <div v-if="detail.comments.length" class="comment-list">
                <div v-for="comment in detail.comments" :key="String(comment.id)">
                  <strong>{{ comment.authorName || '成员' }}</strong>
                  <time>{{ formatTime(comment.createdAt) }}</time>
                  <em v-if="comment.changeKey">Diff 块</em>
                  <p>{{ comment.content }}</p>
                </div>
              </div>
              <el-input
                v-if="canReview"
                v-model="newComment"
                maxlength="500"
                placeholder="添加审批批注"
                @keyup.enter="addComment"
              />
            </section>

            <section class="inspector-section">
              <h3>处理记录</h3>
              <el-timeline class="audit-timeline">
                <el-timeline-item
                  v-for="item in detail.auditTrail"
                  :key="String(item.id)"
                  :timestamp="formatTime(item.createdAt)"
                >
                  <strong>{{ auditLabel(item.action) }}</strong>
                  <p>
                    {{ item.actorName || (item.actorType === 'AGENT' ? 'Agent' : '成员')
                    }}<span v-if="item.detail"> · {{ item.detail }}</span>
                  </p>
                </el-timeline-item>
              </el-timeline>
            </section>
          </template>
        </DataState>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import {
  ElAlert,
  ElButton,
  ElCheckbox,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElPagination,
  ElRadio,
  ElRadioButton,
  ElRadioGroup,
  ElSegmented,
  ElTag,
  ElTimeline,
  ElTimelineItem,
} from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import DocumentDiff from '@/diff/components/DocumentDiff.vue'
import {
  acceptChangeRequest,
  addChangeRequestComment,
  approveChangeRequest,
  batchAcceptChangeRequests,
  batchApproveChangeRequests,
  batchMergeChangeRequests,
  batchRejectChangeRequests,
  claimChangeRequest,
  getChangeRequest,
  getChangeRequestStats,
  mergeChangeRequest,
  rejectChangeRequest,
  returnChangeRequest,
  searchChangeRequests,
  unclaimChangeRequest,
} from '@/features/approval/api/approval-api'
import type {
  BatchChangeRequestResult,
  ChangeRequestDetail,
  ChangeRequestListItem,
  ChangeRequestResolutionType,
  ChangeRequestStats,
  ChangeRequestStatus,
} from '@/features/approval/types'
import { buildDiffHunks, resolveSelectedHunks } from '@/diff/utils/line-diff'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useAuthStore } from '@/stores/auth'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const workspaceStore = useWorkspaceStore()
const spaceId = computed(() => route.params.spaceId as string)
const canReview = computed(() =>
  workspaceStore.hasPermission(SPACE_PERMISSIONS.CHANGE_REQUEST_APPROVE),
)
const canMerge = computed(() =>
  workspaceStore.hasPermission(SPACE_PERMISSIONS.CHANGE_REQUEST_MERGE),
)

const requests = ref<ChangeRequestListItem[]>([])
const detail = ref<ChangeRequestDetail | null>(null)
const selectedId = ref<EntityId | null>(null)
const selectedIds = ref<EntityId[]>([])
const statusFilter = ref<'ALL' | ChangeRequestStatus>('PENDING')
const assignedToMe = ref(false)
const queueLoading = ref(false)
const detailLoading = ref(false)
const acting = ref(false)
const queueError = ref('')
const detailError = ref('')
const diffMode = ref<'inline' | 'split'>('inline')
const decisionMode = ref<ChangeRequestResolutionType>('ALL')
const selectedHunkKeys = ref<string[]>([])
const editedContent = ref('')
const reviewComment = ref('')
const newComment = ref('')
const page = reactive({ total: 0, pageNum: 1, pageSize: 20 })
const stats = reactive<ChangeRequestStats>({
  pendingCount: 0,
  pendingCountAsOfYesterday: 0,
  assignedToMeCount: 0,
  unassignedCount: 0,
})
let queueController: AbortController | null = null
let detailController: AbortController | null = null

const statusOptions = [
  { label: '待处理', value: 'PENDING' },
  { label: '已通过', value: 'APPROVED' },
  { label: '已合并', value: 'MERGED' },
  { label: '已退回', value: 'RETURNED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '全部', value: 'ALL' },
]

const diffHunks = computed(() =>
  detail.value ? buildDiffHunks(detail.value.baseContent, detail.value.proposedContent) : [],
)
const diffStats = computed(() =>
  diffHunks.value.reduce(
    (total, hunk) => ({
      added: total.added + hunk.added,
      removed: total.removed + hunk.removed,
    }),
    { added: 0, removed: 0 },
  ),
)
const displayContent = computed(
  () => detail.value?.resolvedContent ?? detail.value?.proposedContent ?? '',
)
const isAssignedToMe = computed(() =>
  Boolean(
    detail.value?.assignedReviewerId &&
    String(detail.value.assignedReviewerId) === String(authStore.user?.id),
  ),
)
const selectedRequests = computed(() =>
  requests.value.filter((item) => selectedIds.value.some((id) => String(id) === String(item.id))),
)
const hasPendingSelection = computed(() =>
  selectedRequests.value.some((item) => item.status === 'PENDING'),
)
const hasApprovedSelection = computed(() =>
  selectedRequests.value.some((item) => item.status === 'APPROVED'),
)

watch(spaceId, () => {
  page.pageNum = 1
  selectedId.value = null
  selectedIds.value = []
  void loadQueue()
})

watch(
  () => route.query.changeRequestId,
  (value) => {
    const requestedId = Array.isArray(value) ? value[0] : value
    if (requestedId) {
      selectedId.value = requestedId
      void loadDetail(requestedId)
    }
  },
)

onMounted(() => void loadQueue())
onBeforeUnmount(() => {
  queueController?.abort()
  detailController?.abort()
})

async function loadQueue(): Promise<void> {
  queueController?.abort()
  const controller = new AbortController()
  queueController = controller
  queueLoading.value = true
  queueError.value = ''
  try {
    const [result, currentStats] = await Promise.all([
      searchChangeRequests(
        {
          spaceId: spaceId.value,
          status: statusFilter.value === 'ALL' ? undefined : statusFilter.value,
          assignedToMe: assignedToMe.value || undefined,
          pageNum: page.pageNum,
          pageSize: page.pageSize,
        },
        controller.signal,
      ),
      getChangeRequestStats(spaceId.value, controller.signal),
    ])
    if (controller.signal.aborted || queueController !== controller) return
    requests.value = result.records
    page.total = result.total
    Object.assign(stats, currentStats)
    const requestedId = Array.isArray(route.query.changeRequestId)
      ? route.query.changeRequestId[0]
      : route.query.changeRequestId
    if (requestedId) {
      selectedId.value = requestedId
    } else {
      const remains = requests.value.some((item) => String(item.id) === String(selectedId.value))
      if (!remains) selectedId.value = requests.value[0]?.id ?? null
    }
    if (selectedId.value !== null) await loadDetail(selectedId.value)
    else detail.value = null
  } catch (error) {
    if (!controller.signal.aborted && queueController === controller) {
      queueError.value = normalizeApiError(error).message
    }
  } finally {
    if (!controller.signal.aborted && queueController === controller) queueLoading.value = false
  }
}

async function loadDetail(id: EntityId): Promise<void> {
  detailController?.abort()
  const controller = new AbortController()
  detailController = controller
  detailLoading.value = true
  detailError.value = ''
  try {
    const result = await getChangeRequest(id, controller.signal)
    if (controller.signal.aborted || detailController !== controller) return
    detail.value = result
    decisionMode.value = result.resolutionType ?? 'ALL'
    reviewComment.value = result.reviewComment ?? ''
    editedContent.value = result.resolvedContent ?? result.proposedContent
    const availableKeys = buildDiffHunks(result.baseContent, result.proposedContent).map(
      (hunk) => hunk.key,
    )
    selectedHunkKeys.value = result.acceptedChangeKeys.length
      ? result.acceptedChangeKeys
      : availableKeys
  } catch (error) {
    if (!controller.signal.aborted && detailController === controller) {
      detailError.value = normalizeApiError(error).message
    }
  } finally {
    if (!controller.signal.aborted && detailController === controller) detailLoading.value = false
  }
}

function applyFilters(): void {
  page.pageNum = 1
  selectedIds.value = []
  void loadQueue()
}

function selectRequest(id: EntityId): void {
  if (String(id) === String(selectedId.value)) return
  selectedId.value = id
  void loadDetail(id)
}

function reloadDetail(): void {
  if (selectedId.value !== null) void loadDetail(selectedId.value)
}

function toggleBatch(id: EntityId, selected: boolean): void {
  if (selected) selectedIds.value = [...selectedIds.value, id]
  else selectedIds.value = selectedIds.value.filter((current) => String(current) !== String(id))
}

async function claim(): Promise<void> {
  if (!detail.value) return
  await runAction(() => claimChangeRequest(detail.value!.id), '已认领')
}

async function unclaim(): Promise<void> {
  if (!detail.value) return
  await runAction(() => unclaimChangeRequest(detail.value!.id), '已释放认领')
}

async function accept(): Promise<void> {
  if (!detail.value) return
  if (!reviewComment.value.trim()) {
    ElMessage.warning('请先填写审批意见')
    return
  }
  if (decisionMode.value === 'PARTIAL' && selectedHunkKeys.value.length === 0) {
    ElMessage.warning('请至少选择一个要接受的变更块')
    return
  }
  const payload = {
    resolutionType: decisionMode.value,
    reviewComment: reviewComment.value.trim(),
    acceptedChangeKeys: decisionMode.value === 'PARTIAL' ? selectedHunkKeys.value : [],
    resolvedContent:
      decisionMode.value === 'PARTIAL'
        ? resolveSelectedHunks(
            detail.value.baseContent,
            detail.value.proposedContent,
            new Set(selectedHunkKeys.value),
          )
        : decisionMode.value === 'EDITED'
          ? editedContent.value
          : undefined,
  }
  await runAction(
    () =>
      canMerge.value
        ? acceptChangeRequest(detail.value!.id, payload)
        : approveChangeRequest(detail.value!.id, payload),
    canMerge.value ? '变更已接受并合并' : '变更已审批通过',
  )
}

async function reject(): Promise<void> {
  if (!detail.value) return
  const comment = reviewComment.value.trim()
  if (!comment) {
    ElMessage.warning('请先填写拒绝原因')
    return
  }
  await runAction(() => rejectChangeRequest(detail.value!.id, comment), '变更已拒绝')
}

async function returnForRework(): Promise<void> {
  if (!detail.value) return
  const comment = reviewComment.value.trim()
  if (!comment) {
    ElMessage.warning('请先填写需要修改的内容')
    return
  }
  await runAction(() => returnChangeRequest(detail.value!.id, comment), '已退回并创建重改任务')
}

async function merge(): Promise<void> {
  if (!detail.value) return
  await runAction(() => mergeChangeRequest(detail.value!.id), '变更已合并')
}

async function addComment(): Promise<void> {
  if (!detail.value || !newComment.value.trim()) return
  acting.value = true
  try {
    await addChangeRequestComment(detail.value.id, { content: newComment.value.trim() })
    newComment.value = ''
    await loadDetail(detail.value.id)
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    acting.value = false
  }
}

async function addHunkComment(changeKey: string): Promise<void> {
  if (!detail.value) return
  try {
    const prompt = await ElMessageBox.prompt('请输入对该变更块的批注', '添加 Diff 批注', {
      inputValidator: (value) => Boolean(value.trim()) || '批注不能为空',
      inputType: 'textarea',
    })
    await addChangeRequestComment(detail.value.id, {
      changeKey,
      content: prompt.value.trim(),
    })
    ElMessage.success('批注已添加')
    await loadDetail(detail.value.id)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(normalizeApiError(error).message)
  }
}

async function runAction(action: () => Promise<unknown>, successMessage: string): Promise<void> {
  acting.value = true
  try {
    await action()
    ElMessage.success(successMessage)
    await loadQueue()
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
    reloadDetail()
  } finally {
    acting.value = false
  }
}

async function runBatch(type: 'accept' | 'approve' | 'reject' | 'merge'): Promise<void> {
  const ids = selectedRequests.value
    .filter((item) => (type === 'merge' ? item.status === 'APPROVED' : item.status === 'PENDING'))
    .map((item) => item.id)
  if (!ids.length) return
  try {
    let result: BatchChangeRequestResult
    if (type === 'reject' || type === 'approve' || type === 'accept') {
      const rejecting = type === 'reject'
      const prompt = await ElMessageBox.prompt(
        rejecting ? '请输入统一拒绝原因' : '请输入统一审批意见',
        rejecting ? '批量拒绝' : '批量审批',
        {
          inputValidator: (value) => Boolean(value.trim()) || '内容不能为空',
        },
      )
      if (rejecting) {
        result = await batchRejectChangeRequests(ids, prompt.value.trim())
      } else if (type === 'accept') {
        result = await batchAcceptChangeRequests(ids, prompt.value.trim())
      } else {
        result = await batchApproveChangeRequests(ids, prompt.value.trim())
      }
    } else if (type === 'merge') {
      result = await batchMergeChangeRequests(ids)
    } else {
      return
    }
    if (result.failures.length)
      ElMessage.warning(`成功 ${result.succeededIds.length} 项，失败 ${result.failures.length} 项`)
    else ElMessage.success(`已处理 ${result.succeededIds.length} 项`)
    selectedIds.value = []
    await loadQueue()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(normalizeApiError(error).message)
  }
}

function openTask(taskId: EntityId): void {
  void router.push(`/spaces/${spaceId.value}/tasks/${taskId}`)
}

function statusLabel(status: ChangeRequestStatus): string {
  return {
    PENDING: '待处理',
    APPROVED: '已通过',
    REJECTED: '已拒绝',
    MERGED: '已合并',
    RETURNED: '已退回',
  }[status]
}

function statusType(
  status: ChangeRequestStatus,
): 'warning' | 'success' | 'danger' | 'info' | 'primary' {
  return {
    PENDING: 'warning',
    APPROVED: 'primary',
    REJECTED: 'danger',
    MERGED: 'success',
    RETURNED: 'info',
  }[status] as 'warning' | 'success' | 'danger' | 'info' | 'primary'
}

function auditLabel(action: string): string {
  return (
    {
      CHANGE_REQUEST_SUBMITTED: '提交变更',
      CHANGE_REQUEST_CLAIMED: '认领审批',
      CHANGE_REQUEST_UNCLAIMED: '释放认领',
      CHANGE_REQUEST_APPROVED: '审批通过',
      CHANGE_REQUEST_REJECTED: '拒绝变更',
      CHANGE_REQUEST_RETURNED: '退回重改',
      CHANGE_REQUEST_REWORK_CREATED: '创建重改任务',
      CHANGE_REQUEST_MERGED: '合并版本',
      CHANGE_REQUEST_COMMENTED: '添加批注',
    }[action] ?? action
  )
}

function formatTime(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function formatTokens(value: number): string {
  return value.toLocaleString('zh-CN')
}
</script>

<style scoped>
.approval-page {
  display: grid;
  gap: 20px;
  min-width: 0;
}
.approval-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
}
.approval-summary div {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 14px 18px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: 10px;
  background: #fff;
}
.approval-summary strong {
  font-size: 24px;
}
.approval-summary span {
  color: var(--adw-text-secondary);
}
.approval-toolbar {
  display: flex;
  min-height: 56px;
  align-items: center;
  gap: 14px;
  padding: 10px 14px;
}
.approval-toolbar__spacer {
  flex: 1;
}
.approval-workbench {
  display: grid;
  min-height: 680px;
  grid-template-columns: 300px minmax(420px, 1fr) 310px;
  overflow: hidden;
}
.approval-queue,
.approval-inspector {
  min-width: 0;
  background: #fff;
}
.approval-queue {
  border-right: 1px solid var(--adw-border-color);
}
.approval-inspector {
  border-left: 1px solid var(--adw-border-color);
  overflow-y: auto;
}
.approval-diff {
  min-width: 0;
  background: #f8fafc;
  overflow: auto;
}
.pane-heading {
  display: flex;
  height: 54px;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid var(--adw-border-color);
}
.pane-heading span {
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.queue-list {
  display: grid;
}
.queue-card {
  display: flex;
  width: 100%;
  gap: 10px;
  padding: 14px;
  border: 0;
  border-bottom: 1px solid var(--adw-border-color-light);
  color: inherit;
  background: #fff;
  text-align: left;
  cursor: pointer;
}
.queue-card:hover {
  background: #f8faff;
}
.queue-card.is-active {
  background: var(--adw-color-primary-soft);
  box-shadow: inset 3px 0 var(--adw-color-primary);
}
.queue-card__body,
.queue-card__top {
  display: flex;
  min-width: 0;
}
.queue-card__body {
  flex: 1;
  flex-direction: column;
  gap: 7px;
}
.queue-card__top {
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.queue-card__top strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.queue-card__summary {
  color: var(--adw-text-secondary);
  font-size: 13px;
  line-height: 1.55;
  overflow-wrap: anywhere;
  white-space: normal;
}
.queue-card__meta {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.queue-card__meta i {
  color: var(--adw-color-primary);
  font-style: normal;
}
.queue-pagination {
  display: flex;
  justify-content: center;
  padding: 12px;
}
.diff-heading {
  display: flex;
  min-height: 82px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  background: #fff;
  border-bottom: 1px solid var(--adw-border-color);
}
.diff-heading > div:first-child {
  min-width: 0;
  flex: 1 1 auto;
}
.diff-heading > .el-radio-group {
  flex: 0 0 auto;
  white-space: nowrap;
}
.diff-heading__title {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
}
.diff-heading__title h2 {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.diff-heading h2 {
  margin: 0;
  font-size: 18px;
}
.diff-heading p {
  margin: 7px 0 0;
  color: var(--adw-text-secondary);
  font-size: 13px;
  line-height: 1.55;
  overflow-wrap: anywhere;
  white-space: normal;
}
.approval-diff :deep(.el-alert) {
  margin: 14px 16px 0;
  width: auto;
}
.version-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  padding: 10px 18px;
  color: var(--adw-text-secondary);
  background: #fff;
  border-bottom: 1px solid var(--adw-border-color-light);
  font-size: 12px;
}
.version-strip b {
  color: var(--adw-text-primary);
}
.content-editor {
  padding: 16px;
}
.content-editor :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  line-height: 1.7;
}
.inspector-section {
  padding: 16px;
  border-bottom: 1px solid var(--adw-border-color-light);
}
.inspector-section h3 {
  margin: 0 0 12px;
  font-size: 14px;
}
.source-card dl {
  display: grid;
  grid-template-columns: 70px 1fr;
  gap: 9px;
  margin: 0;
  font-size: 13px;
}
.source-card dt {
  color: var(--adw-text-secondary);
}
.source-card dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}
.task-reference {
  display: grid;
  min-width: 0;
  gap: 3px;
}
.task-reference .text-link,
.task-reference__name {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-reference__name {
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.source-card small {
  color: var(--adw-text-secondary);
}
.text-link {
  padding: 0;
  border: 0;
  color: var(--adw-color-primary);
  background: transparent;
  cursor: pointer;
}
.decision-modes {
  display: grid;
  gap: 8px;
}
.selection-tip,
.saved-comment {
  color: var(--adw-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.decision-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
.decision-actions .el-button {
  flex: 0 0 auto;
  margin-left: 0;
  white-space: nowrap;
}
.comment-list {
  display: grid;
  gap: 12px;
  margin-bottom: 12px;
}
.comment-list div {
  padding-left: 10px;
  border-left: 2px solid var(--adw-border-color);
}
.comment-list strong {
  font-size: 12px;
}
.comment-list time {
  margin-left: 8px;
  color: var(--adw-text-tertiary);
  font-size: 11px;
}
.comment-list em {
  margin-left: 6px;
  padding: 1px 5px;
  border-radius: 4px;
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
  font-size: 10px;
  font-style: normal;
}
.comment-list p {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.55;
}
.audit-timeline {
  padding-left: 4px;
}
.audit-timeline :deep(.el-timeline-item__timestamp) {
  font-size: 11px;
}
.audit-timeline strong {
  font-size: 12px;
}
.audit-timeline p {
  margin: 3px 0 0;
  color: var(--adw-text-secondary);
  font-size: 11px;
}
@media (max-width: 1280px) {
  .approval-workbench {
    grid-template-columns: 270px minmax(400px, 1fr);
  }
  .approval-inspector {
    grid-column: 1 / -1;
    border-top: 1px solid var(--adw-border-color);
    border-left: 0;
  }
}
@media (max-width: 820px) {
  .approval-summary {
    grid-template-columns: repeat(2, 1fr);
  }
  .approval-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .approval-toolbar__spacer {
    display: none;
  }
  .approval-workbench {
    grid-template-columns: 1fr;
  }
  .approval-queue {
    border-right: 0;
  }
  .approval-diff {
    min-height: 520px;
  }
}
</style>
