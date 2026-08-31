<template>
  <section class="overview-page">
    <PageHeader
      :title="workspaceStore.currentSpace?.name || '空间总览'"
      description="文档、Agent 与执行活动一览"
    >
      <template #breadcrumb>
        <span class="overview-breadcrumb">工作台 / 空间总览</span>
      </template>
      <template #actions>
        <el-button :loading="loading" @click="loadOverview">刷新数据</el-button>
      </template>
    </PageHeader>

    <DataState :loading="loading" :error="errorMessage" @retry="loadOverview">
      <template v-if="!loading">
        <div class="overview-stats">
          <article v-if="canReadDocuments" class="stat-card stat-card--blue surface-card">
            <span class="stat-card__icon stat-card__icon--blue"
              ><el-icon><Document /></el-icon
            ></span>
            <div>
              <span class="stat-card__label">文档</span>
              <strong>{{ documentStats.totalCount }}</strong>
              <small>较上月 {{ formatDocumentChange }}</small>
            </div>
          </article>
          <article v-if="canReadTasks" class="stat-card stat-card--green surface-card">
            <span class="stat-card__icon stat-card__icon--green"
              ><el-icon><Tickets /></el-icon
            ></span>
            <div>
              <span class="stat-card__label">任务总数</span>
              <strong>{{ taskStats.totalCount }}</strong>
              <small>较昨日 {{ formatTaskChange }}</small>
            </div>
          </article>
          <article v-if="canReadChanges" class="stat-card stat-card--orange surface-card">
            <span class="stat-card__icon stat-card__icon--orange"
              ><el-icon><Checked /></el-icon
            ></span>
            <div>
              <span class="stat-card__label">待审批变更</span>
              <strong>{{ pendingChangeStats.pendingCount }}</strong>
              <small>较昨日 {{ formatPendingChange }}</small>
            </div>
          </article>
          <article v-if="canReadUsage" class="stat-card stat-card--teal surface-card">
            <span class="stat-card__icon stat-card__icon--teal"
              ><el-icon><Coin /></el-icon
            ></span>
            <div>
              <span class="stat-card__label">本月 Token</span>
              <div class="stat-card__token-value">
                <strong>{{ formatTokens(monthlyTokenBudget.usedTokens) }}</strong>
                <span v-if="monthlyTokenBudget.tokenBudget !== null" class="stat-card__token-budget">
                  / {{ formatTokens(monthlyTokenBudget.tokenBudget) }}
                </span>
              </div>
              <div v-if="tokenUsagePercent !== null" class="stat-card__progress" aria-hidden="true">
                <span :style="{ width: `${tokenProgressPercent}%` }"></span>
              </div>
              <small>{{ formatTokenUsage }}</small>
            </div>
          </article>
        </div>

        <div class="overview-grid">
          <article v-if="canReadDocuments" class="overview-panel overview-panel--documents surface-card">
            <header class="overview-panel__header">
              <div>
                <h2>最近文档</h2>
              </div>
              <button type="button" class="overview-panel__view-all">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </button>
            </header>
            <div v-if="recentDocumentPage.records.length" class="document-list">
              <div class="document-row document-row--header">
                <span>名称</span>
                <span>类型</span>
                <span>最近更新</span>
                <span>更新人</span>
              </div>
              <div v-for="item in recentDocumentPage.records" :key="item.id" class="document-row">
                <span class="document-row__name">
                  <el-icon
                    :class="
                      item.docType === 'FORMAL' ? 'document-row__formal' : 'document-row__draft'
                    "
                    ><Document
                  /></el-icon>
                  <span>{{ item.title }}</span>
                </span>
                <el-tag
                  class="document-type-tag"
                  size="small"
                  effect="light"
                  :class="
                    item.docType === 'FORMAL'
                      ? 'document-type-tag--formal'
                      : 'document-type-tag--draft'
                  "
                  :type="item.docType === 'FORMAL' ? 'primary' : 'info'"
                >
                  {{ item.docType === 'FORMAL' ? '正式' : '草稿' }}
                </el-tag>
                <span class="document-row__updated">{{ formatTime(item.updatedAt) }}</span>
                <span class="document-row__operator">{{ item.updatedByName || '—' }}</span>
              </div>
            </div>
            <div v-else class="overview-panel__empty">当前空间还没有文档</div>
            <el-config-provider v-if="recentDocumentPage.total > 0" :locale="zhCn">
              <el-pagination
                class="document-pagination"
                background
                layout="total, prev, pager, next"
                :total="recentDocumentPage.total"
                :page-size="RECENT_DOCUMENT_PAGE_SIZE"
                :current-page="recentDocumentPage.pageNum"
                :disabled="recentDocumentLoading"
                @current-change="changeRecentDocumentPage"
              />
            </el-config-provider>
          </article>

          <article v-if="canReadTasks" class="overview-panel overview-panel--activity surface-card">
            <header class="overview-panel__header">
              <div>
                <h2>执行动态</h2>
              </div>
              <button type="button" class="overview-panel__view-all">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </button>
            </header>
            <div v-if="taskPage.records.length" class="task-list">
              <div
                v-for="(task, index) in taskPage.records"
                :key="task.id"
                class="task-row"
                :class="{ 'task-row--last': index === taskPage.records.length - 1 }"
              >
                <div
                  class="task-row__marker"
                  :class="`task-row__marker--${task.status.toLowerCase()}`"
                >
                  <el-icon><component :is="taskActivityIcon(task.status)" /></el-icon>
                </div>
                <div class="task-row__content">
                  <div class="task-row__headline">
                    <strong>{{ task.name }}</strong>
                    <el-tag size="small" effect="light" :type="taskStatusType(task.status)">
                      {{ taskStatusLabel(task.status) }}
                    </el-tag>
                  </div>
                  <span>
                    Agent
                    <el-icon><User /></el-icon>
                    {{ task.operatorName || '—' }}
                  </span>
                </div>
                <time class="task-row__time">{{ formatActivityTime(task.activityAt) }}</time>
              </div>
            </div>
            <div v-else class="overview-panel__empty">当前空间还没有任务</div>
          </article>

          <article v-if="abilityCards.length" class="overview-panel surface-card">
            <header class="overview-panel__header">
              <div>
                <h2>Agent 能力概览</h2>
              </div>
              <button type="button" class="overview-panel__view-all">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </button>
            </header>
            <div class="ability-grid">
              <div v-for="card in abilityCards" :key="card.label" class="ability-card">
                <span :class="`ability-card__icon ability-card__icon--${card.tone}`">
                  <el-icon><component :is="card.icon" /></el-icon>
                </span>
                <div class="ability-card__body">
                  <div class="ability-card__summary">
                    <strong>{{ card.value }}</strong>
                    <span>{{ card.unit }}</span>
                  </div>
                  <span class="ability-card__status">{{ card.status }}</span>
                </div>
              </div>
            </div>
          </article>

          <article v-if="canReadChanges" class="overview-panel overview-panel--pending surface-card">
            <header class="overview-panel__header">
              <div>
                <h2>待处理事项</h2>
              </div>
              <button type="button" class="overview-panel__view-all">
                查看全部
                <el-icon><ArrowRight /></el-icon>
              </button>
            </header>
            <div class="pending-list">
              <div class="pending-row pending-row--action">
                <span class="pending-row__icon pending-row__icon--warning">
                  <el-icon><WarningFilled /></el-icon>
                </span>
                <div>
                  <strong>待审批变更</strong>
                  <span>有 {{ pendingChangeStats.pendingCount }} 条变更需要审批</span>
                </div>
                <span class="pending-row__value pending-row__value--warning">
                  {{ pendingChangeStats.pendingCount }} 条
                </span>
                <el-icon class="pending-row__arrow"><ArrowRight /></el-icon>
              </div>
              <div v-if="canReadUsage" class="pending-row pending-row--action">
                <span class="pending-row__icon pending-row__icon--danger">
                  <el-icon><WarningFilled /></el-icon>
                </span>
                <div>
                  <strong>预算预警</strong>
                  <span>{{ tokenBudgetWarning }}</span>
                </div>
                <span class="pending-row__value pending-row__value--danger">
                  {{ tokenUsagePercentLabel }}
                </span>
                <el-icon class="pending-row__arrow"><ArrowRight /></el-icon>
              </div>
            </div>
          </article>
        </div>
      </template>
    </DataState>
  </section>
</template>

<script setup lang="ts">
import {
  ArrowRight,
  Checked,
  CircleCheck,
  Clock,
  Coin,
  Cpu,
  Document,
  Management,
  Tickets,
  User,
  VideoPlay,
  WarningFilled,
} from '@element-plus/icons-vue'
import { ElButton, ElConfigProvider, ElIcon, ElPagination, ElTag } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import {
  getAgentOverviewStats,
  getDocumentStats,
  getMonthlyTokenBudget,
  getPendingChangeStats,
  getTaskStats,
  listRecentDocuments,
  listTaskActivities,
  type AgentOverviewStats,
  type DocumentStats,
  type MonthlyTokenBudget,
  type PendingChangeStats,
  type PageResult,
  type RecentDocument,
  type TaskActivitySummary,
  type TaskStats,
} from '@/features/workspace/api/space-overview-api'
import PageHeader from '@/shared/components/PageHeader.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import DataState from '@/shared/components/DataState.vue'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute()
const workspaceStore = useWorkspaceStore()
const RECENT_DOCUMENT_PAGE_SIZE = 5
const recentDocumentLoading = ref(false)
const loading = ref(true)
const errorMessage = ref('')
const recentDocumentPage = ref<PageResult<RecentDocument>>({
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: RECENT_DOCUMENT_PAGE_SIZE,
})
const documentStats = ref<DocumentStats>({ totalCount: 0, countAsOfLastMonth: 0 })
const taskStats = ref<TaskStats>({ totalCount: 0, countAsOfYesterday: 0 })
const pendingChangeStats = ref<PendingChangeStats>({
  pendingCount: 0,
  pendingCountAsOfYesterday: 0,
})
const TASK_ACTIVITY_PAGE_SIZE = 6
const taskPage = ref<PageResult<TaskActivitySummary>>({
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: TASK_ACTIVITY_PAGE_SIZE,
})
const monthlyTokenBudget = ref<MonthlyTokenBudget>({ usedTokens: 0, tokenBudget: null })
const agentOverviewStats = ref<AgentOverviewStats>({
  activeAgentCount: null,
  activeSkillCount: null,
  enabledMcpCount: null,
})
function createRequestController() {
  return new globalThis.AbortController()
}

let controller: ReturnType<typeof createRequestController> | null = null
let loadSequence = 0

const canReadDocuments = computed(() =>
  workspaceStore.hasPermission(SPACE_PERMISSIONS.DOCUMENT_READ),
)
const canReadTasks = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.TASK_READ))
const canReadChanges = computed(() =>
  workspaceStore.hasPermission(SPACE_PERMISSIONS.CHANGE_REQUEST_READ),
)
const canReadUsage = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.USAGE_READ))
const canReadAgents = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.AGENT_READ))
const canReadSkills = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.SKILL_READ))
const canReadMcp = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.MCP_READ))
const formatDocumentChange = computed(() => {
  const change = documentStats.value.totalCount - documentStats.value.countAsOfLastMonth
  return formatSignedChange(change)
})
const formatTaskChange = computed(() =>
  formatSignedChange(taskStats.value.totalCount - taskStats.value.countAsOfYesterday),
)
const formatPendingChange = computed(() =>
  formatSignedChange(
    pendingChangeStats.value.pendingCount - pendingChangeStats.value.pendingCountAsOfYesterday,
  ),
)
const tokenUsagePercent = computed(() => {
  const budget = monthlyTokenBudget.value.tokenBudget
  return budget !== null && budget > 0
    ? (monthlyTokenBudget.value.usedTokens / budget) * 100
    : null
})
const tokenProgressPercent = computed(() =>
  tokenUsagePercent.value === null
    ? 0
    : Math.min(100, Math.max(0, tokenUsagePercent.value)),
)
const formatTokenUsage = computed(() => {
  const value = tokenUsagePercent.value
  if (value === null) return '未设置预算'
  return `${formatPercent(value)}% 已使用`
})
const tokenUsagePercentLabel = computed(() =>
  tokenUsagePercent.value === null ? '—' : `${formatPercent(tokenUsagePercent.value)}%`,
)
const tokenBudgetWarning = computed(() =>
  tokenUsagePercent.value === null
    ? '本月 Token 尚未设置预算'
    : `本月 Token 使用量已达 ${tokenUsagePercentLabel.value}，请注意控制成本`,
)
const abilityCards = computed(() =>
  [
    !canReadAgents.value || agentOverviewStats.value.activeAgentCount === null
      ? null
      : {
          label: 'Agent',
          unit: '个 Agent',
          status: '运行正常',
          value: agentOverviewStats.value.activeAgentCount,
          tone: 'blue',
          icon: Cpu,
        },
    !canReadSkills.value || agentOverviewStats.value.activeSkillCount === null
      ? null
      : {
          label: 'Skill',
          unit: '个 Skill',
          status: '已启用',
          value: agentOverviewStats.value.activeSkillCount,
          tone: 'purple',
          icon: Management,
        },
    !canReadMcp.value || agentOverviewStats.value.enabledMcpCount === null
      ? null
      : {
          label: 'MCP',
          unit: '个外部 MCP',
          status: '已连接',
          value: agentOverviewStats.value.enabledMcpCount,
          tone: 'teal',
          icon: Coin,
        },
  ].filter((card): card is NonNullable<typeof card> => card !== null),
)

async function loadOverview(): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  if (!spaceId) {
    loading.value = false
    return
  }
  controller?.abort()
  controller = createRequestController()
  const currentSequence = ++loadSequence
  const signal = controller.signal
  loading.value = true
  errorMessage.value = ''

  try {
    const empty = <T,>(): PageResult<T> => ({ records: [], total: 0, pageNum: 1, pageSize: 1 })
    const [documents, stats, taskSummary, tasks, pendingSummary, tokenBudget, agentStats] = await Promise.all([
      canReadDocuments.value
        ? listRecentDocuments(spaceId, signal, RECENT_DOCUMENT_PAGE_SIZE)
        : Promise.resolve({
            records: [],
            total: 0,
            pageNum: 1,
            pageSize: RECENT_DOCUMENT_PAGE_SIZE,
          }),
      canReadDocuments.value
        ? getDocumentStats(spaceId, signal)
        : Promise.resolve({ totalCount: 0, countAsOfLastMonth: 0 }),
      canReadTasks.value
        ? getTaskStats(spaceId, signal)
        : Promise.resolve({ totalCount: 0, countAsOfYesterday: 0 }),
      canReadTasks.value
        ? listTaskActivities(spaceId, signal, TASK_ACTIVITY_PAGE_SIZE)
        : Promise.resolve(empty<TaskActivitySummary>()),
      canReadChanges.value
        ? getPendingChangeStats(spaceId, signal)
        : Promise.resolve({ pendingCount: 0, pendingCountAsOfYesterday: 0 }),
      canReadUsage.value
        ? getMonthlyTokenBudget(spaceId, signal)
        : Promise.resolve({ usedTokens: 0, tokenBudget: null }),
      canReadAgents.value || canReadSkills.value || canReadMcp.value
        ? getAgentOverviewStats(spaceId, signal)
        : Promise.resolve({
            activeAgentCount: null,
            activeSkillCount: null,
            enabledMcpCount: null,
          }),
    ])
    if (signal.aborted || currentSequence !== loadSequence) return
    recentDocumentPage.value = documents
    documentStats.value = stats ?? { totalCount: 0, countAsOfLastMonth: 0 }
    taskStats.value = taskSummary ?? { totalCount: 0, countAsOfYesterday: 0 }
    taskPage.value = tasks
    pendingChangeStats.value = pendingSummary ?? {
      pendingCount: 0,
      pendingCountAsOfYesterday: 0,
    }
    monthlyTokenBudget.value = tokenBudget ?? { usedTokens: 0, tokenBudget: null }
    agentOverviewStats.value = agentStats ?? {
      activeAgentCount: null,
      activeSkillCount: null,
      enabledMcpCount: null,
    }
  } catch (error) {
    if (!signal.aborted && currentSequence === loadSequence)
      errorMessage.value = normalizeApiError(error).message
  } finally {
    if (!signal.aborted && currentSequence === loadSequence) loading.value = false
  }
}

async function changeRecentDocumentPage(pageNum: number): Promise<void> {
  const spaceId = workspaceStore.currentSpaceId
  if (!spaceId || pageNum === recentDocumentPage.value.pageNum) return
  recentDocumentLoading.value = true
  try {
    recentDocumentPage.value = await listRecentDocuments(
      spaceId,
      undefined,
      RECENT_DOCUMENT_PAGE_SIZE,
      pageNum,
    )
  } catch (error) {
    errorMessage.value = normalizeApiError(error).message
  } finally {
    recentDocumentLoading.value = false
  }
}

function formatSignedChange(change: number): string {
  return change > 0 ? `+${change}` : String(change)
}

function formatPercent(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1)
}

function formatTokens(tokens: number | null | undefined): string {
  if (tokens === null || tokens === undefined) return '—'
  if (tokens >= 1_000_000) {
    const value = tokens / 1_000_000
    return `${Number.isInteger(value) ? value : value.toFixed(1)}M`
  }
  if (tokens >= 1_000) {
    const value = tokens / 1_000
    return `${Number.isInteger(value) ? value : value.toFixed(1)}K`
  }
  return String(tokens)
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

function formatActivityTime(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  const time = new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
  const today = new Date()
  if (date.toDateString() === today.toDateString()) return time
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  if (date.toDateString() === yesterday.toDateString()) return `昨天 ${time}`
  return `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')} ${time}`
}

function taskStatusLabel(status: TaskActivitySummary['status']): string {
  return {
    PENDING: '待运行',
    RUNNING: '运行中',
    COMPLETED: '已完成',
    TERMINATED: '已终止',
    FAILED: '执行失败',
    DISPATCHED: '已分发',
    WAITING_INPUT: '等待输入',
    WAITING_AUTH: '等待授权',
    CANCELING: '取消中',
  }[status]
}

function taskStatusType(
  status: TaskActivitySummary['status'],
): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'RUNNING' || status === 'DISPATCHED') return 'primary'
  if (
    status === 'PENDING' ||
    status === 'WAITING_INPUT' ||
    status === 'WAITING_AUTH' ||
    status === 'CANCELING'
  )
    return 'warning'
  return 'info'
}

function taskActivityIcon(status: TaskActivitySummary['status']) {
  if (status === 'COMPLETED') return CircleCheck
  if (
    status === 'PENDING' ||
    status === 'WAITING_INPUT' ||
    status === 'WAITING_AUTH' ||
    status === 'CANCELING'
  )
    return Clock
  return status === 'FAILED' ? WarningFilled : status === 'TERMINATED' ? CircleCheck : VideoPlay
}

watch(() => route.params.spaceId, loadOverview, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<style scoped>
.overview-page {
  display: grid;
  gap: var(--adw-space-5);
}
.overview-breadcrumb {
  color: var(--adw-text-tertiary);
  font-size: var(--adw-font-size-caption);
}
.overview-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--adw-space-4);
}
.stat-card {
  display: flex;
  min-height: 124px;
  align-items: center;
  gap: var(--adw-space-4);
  padding: var(--adw-space-4);
}
.stat-card__icon {
  display: inline-flex;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 24px;
}
.stat-card__icon--blue {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.stat-card__icon--green {
  color: var(--adw-color-success);
  background: var(--adw-color-success-soft);
}
.stat-card__icon--orange {
  color: var(--adw-color-warning);
  background: var(--adw-color-warning-soft);
}
.stat-card__icon--teal {
  color: var(--adw-color-teal);
  background: var(--adw-color-teal-soft);
}
.stat-card__label {
  display: block;
  color: var(--adw-text-secondary);
}
.stat-card strong {
  display: block;
  margin: 4px 0;
  color: var(--adw-text-primary);
  font-size: 25px;
  line-height: 1;
}
.stat-card--blue strong {
  color: var(--adw-color-primary);
}
.stat-card--green strong {
  color: var(--adw-color-success);
}
.stat-card--orange strong {
  color: var(--adw-color-warning);
}
.stat-card--teal strong {
  color: var(--adw-color-teal);
}
.stat-card small {
  color: var(--adw-text-tertiary);
}
.stat-card__token-value {
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.stat-card__token-budget {
  color: var(--adw-text-tertiary);
  font-size: var(--adw-font-size-body);
}
.stat-card__progress {
  width: 180px;
  height: 8px;
  margin: 8px 0 6px;
  overflow: hidden;
  border-radius: 999px;
  background: var(--adw-border-color-light);
}
.stat-card__progress > span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--adw-color-teal);
  transition: width 180ms ease;
}
.overview-grid {
  display: grid;
  align-items: stretch;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--adw-space-4);
}
.overview-panel {
  min-height: 0;
  padding: var(--adw-space-4);
}
.overview-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding-bottom: var(--adw-space-3);
  border-bottom: 1px solid var(--adw-border-color-light);
}
.overview-panel__header h2 {
  margin: 0 0 3px;
  font-size: var(--adw-font-size-subtitle);
}
.overview-panel__header > div > span {
  color: var(--adw-text-tertiary);
  font-size: var(--adw-font-size-caption);
}
.overview-panel__header > .el-icon {
  color: var(--adw-color-primary);
  font-size: 22px;
}
.overview-panel--documents .overview-panel__header {
  align-items: center;
  padding-bottom: var(--adw-space-3);
  border-bottom: 0;
}
.overview-panel--activity .overview-panel__header {
  align-items: center;
  padding-bottom: var(--adw-space-3);
  border-bottom: 0;
}
.overview-panel__view-all {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 0;
  border: 0;
  color: var(--adw-color-primary);
  background: transparent;
  font: inherit;
  cursor: pointer;
}
.overview-panel__view-all .el-icon {
  font-size: 14px;
}
.document-list,
.task-list,
.pending-list {
  display: grid;
}
.document-row,
.task-row,
.pending-row {
  display: flex;
  min-height: 44px;
  align-items: center;
  gap: var(--adw-space-3);
  border-bottom: 1px solid var(--adw-border-color-light);
}
.document-row:last-child,
.task-row:last-child,
.pending-row:last-child {
  border-bottom: 0;
}
.document-row {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(0, 1.1fr) minmax(0, 1.4fr) minmax(0, 0.7fr);
  gap: 0;
  padding: 0 var(--adw-space-3);
  font-size: var(--adw-font-size-body);
}
.document-row--header {
  min-height: 42px;
  color: var(--adw-text-tertiary);
  font-size: var(--adw-font-size-body);
}
.document-row--header > span:nth-child(2) {
  text-align: center;
}
.document-row--header > span:nth-child(4) {
  text-align: right;
}
.overview-panel--documents .document-row:not(.document-row--header) {
  min-height: 48px;
}
.overview-panel--documents .document-list {
  overflow: hidden;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
}
.document-pagination {
  justify-content: flex-end;
  padding-top: var(--adw-space-3);
}
.document-row__name {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--adw-space-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.document-row__name > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.document-row__updated,
.document-row__operator {
  overflow: hidden;
  color: var(--adw-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.document-row__operator {
  text-align: right;
}
.document-type-tag.el-tag {
  justify-self: center;
  min-width: 46px;
  justify-content: center;
  border: 0;
  border-radius: 6px;
  font-size: var(--adw-font-size-caption);
}
.document-type-tag--formal.el-tag {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.document-type-tag--draft.el-tag {
  color: var(--adw-text-secondary);
  background: var(--adw-border-color-light);
}
.document-row__formal {
  color: var(--adw-color-primary);
}
.document-row__draft {
  color: var(--adw-color-success);
}
.overview-panel__empty {
  display: grid;
  min-height: 130px;
  place-items: center;
  color: var(--adw-text-tertiary);
}
.overview-panel__note {
  margin: var(--adw-space-3) 0 0;
  color: var(--adw-text-tertiary);
  font-size: var(--adw-font-size-caption);
}
.task-row__marker {
  display: inline-flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #ffffff;
  background: var(--adw-color-info);
}
.task-row__marker--running,
.task-row__marker--dispatched {
  background: var(--adw-color-primary);
}
.task-row__marker--pending,
.task-row__marker--waiting_input,
.task-row__marker--waiting_auth,
.task-row__marker--canceling {
  background: var(--adw-color-warning);
}
.task-row__marker--completed {
  background: var(--adw-color-success);
}
.task-row__marker--failed {
  background: var(--adw-color-danger);
}
.overview-panel--activity .task-row {
  position: relative;
  min-height: 52px;
  align-items: center;
  padding: 8px 0;
}
.overview-panel--activity .task-row:not(.task-row--last)::before {
  position: absolute;
  top: 40px;
  bottom: -1px;
  left: 15px;
  width: 2px;
  background: var(--adw-border-color-light);
  content: '';
}
.overview-panel--activity .task-row__marker {
  position: relative;
  z-index: 1;
  flex: 0 0 30px;
}
.task-row__content {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 3px;
}
.overview-panel--activity .task-row__headline {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: var(--adw-space-3);
}
.overview-panel--activity .task-row__headline strong {
  min-width: 0;
  font-size: var(--adw-font-size-body);
  font-weight: 600;
}
.overview-panel--activity .task-row__headline .el-tag {
  flex: 0 0 auto;
}
.task-row__content strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-row__content span,
.pending-row div span {
  color: var(--adw-text-tertiary);
  font-size: var(--adw-font-size-caption);
}
.task-row__content span .el-icon {
  margin: 0 3px 0 10px;
  vertical-align: -2px;
}
.task-row__time {
  margin-left: auto;
  color: var(--adw-text-tertiary);
  font-size: var(--adw-font-size-body);
  white-space: nowrap;
}
.pending-row__icon {
  display: inline-flex;
  width: 32px;
  height: 32px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: var(--adw-color-warning);
  background: #fff4df;
}
.overview-panel--pending .pending-list {
  overflow: hidden;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
}
.overview-panel--pending .pending-row {
  min-height: 70px;
  padding: 0 var(--adw-space-3);
}
.overview-panel--pending .pending-row:last-child {
  border-bottom: 0;
}
.pending-row__icon--warning {
  color: var(--adw-color-warning);
  background: var(--adw-color-warning-soft);
}
.pending-row__icon--danger {
  color: var(--adw-color-danger);
  background: #fff0f1;
}
.pending-row div {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 3px;
}
.pending-row div strong {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.pending-row__value {
  margin-left: auto;
  font-size: var(--adw-font-size-subtitle);
  white-space: nowrap;
}
.pending-row__value--warning {
  color: var(--adw-color-warning);
}
.pending-row__value--danger {
  color: var(--adw-color-danger);
}
.pending-row__arrow {
  margin-left: var(--adw-space-2);
  color: var(--adw-text-tertiary);
}
.ability-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  min-height: 128px;
  align-items: center;
  padding: 12px 0;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
}
.ability-card {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: center;
  gap: var(--adw-space-3);
  padding: 0 var(--adw-space-4);
  border-right: 1px solid var(--adw-border-color-light);
}
.ability-card:last-child {
  border-right: 0;
}
.ability-card__icon {
  display: inline-flex;
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 28px;
}
.ability-card__icon--blue {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.ability-card__icon--purple {
  color: var(--adw-color-accent);
  background: #f0ecff;
}
.ability-card__icon--teal {
  color: var(--adw-color-teal);
  background: var(--adw-color-teal-soft);
}
.ability-card__body {
  display: grid;
  min-width: 0;
  gap: 4px;
}
.ability-card__summary {
  display: flex;
  align-items: baseline;
  gap: 4px;
  white-space: nowrap;
}
.ability-card strong {
  color: var(--adw-text-primary);
  font-size: 30px;
  line-height: 1;
}
.ability-card__summary > span,
.ability-card__status {
  color: var(--adw-text-secondary);
  font-size: var(--adw-font-size-body);
  white-space: nowrap;
}
.ability-card__status {
  color: var(--adw-text-tertiary);
}
@media (max-width: 1100px) {
  .overview-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .overview-stats,
  .overview-grid {
    grid-template-columns: 1fr;
  }
  .overview-panel {
    min-height: 0;
  }
}
</style>
