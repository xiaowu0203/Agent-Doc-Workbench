<template>
  <section class="detail-page">
    <PageHeader :title="task?.name || '任务执行详情'" :description="headerDescription">
      <template #breadcrumb>
        <button class="back-link" type="button" @click="router.push(`/spaces/${spaceId}/tasks`)">
          任务
        </button>
        <span> / {{ task?.taskNo || '详情' }}</span>
      </template>
      <template #actions>
        <el-button v-if="canRun" type="primary" :loading="running" @click="triggerRun"
          >运行任务</el-button
        >
        <el-button v-if="canRerun" type="primary" :loading="rerunning" @click="rerun"
          >重新运行</el-button
        >
        <el-button v-if="canTerminate" type="danger" plain :loading="terminating" @click="terminate"
          >终止任务</el-button
        >
      </template>
    </PageHeader>

    <DataState :loading="loading" :error="error" @retry="loadDetail">
      <template v-if="detail && task">
        <div class="status-line">
          <span class="status-pill" :class="`status-${task.status.toLowerCase()}`">{{
            statusLabel(task.status)
          }}</span>
          <span>Agent：{{ detail.agentName || `#${task.agentId}` }}</span>
          <span class="refresh-state">{{
            refreshing ? '正在刷新…' : isActive ? '每 3 秒自动刷新' : '执行已结束'
          }}</span>
        </div>

        <div class="metric-grid">
          <article class="surface-card metric-card metric-token">
            <span>已用 Token</span
            ><strong
              >{{ formatNumber(usedTokens) }}
              <small>/ {{ formatNumber(task.tokenBudget) }}</small></strong
            >
            <p>{{ detail.tokensEstimated ? '包含本地估算值' : '模型用量统计' }}</p>
          </article>
          <article class="surface-card metric-card metric-duration">
            <span>执行耗时</span><strong>{{ executionDuration }}</strong>
            <p>超时限制 {{ formatLimit(detail.execution?.executionTimeoutSeconds, '秒') }}</p>
          </article>
          <article class="surface-card metric-card metric-tools">
            <span>工具调用</span><strong>{{ detail.execution?.toolCalls.length ?? 0 }}</strong>
            <p>{{ successfulToolCount }} 次成功</p>
          </article>
          <article class="surface-card metric-card metric-iterations">
            <span>模型迭代</span
            ><strong
              >{{ detail.execution?.modelCalls.length ?? 0 }}
              <small>/ {{ detail.execution?.maxIterations ?? '—' }}</small></strong
            >
            <p>以模型调用审计计数</p>
          </article>
        </div>

        <div class="execution-layout">
          <article class="surface-card trail-card">
            <div class="card-heading">
              <div>
                <h2>执行轨迹</h2>
                <p>最新轮次优先，默认展开最近 2 轮</p>
              </div>
              <div class="trail-heading-tools">
                <div class="trail-filters" role="tablist" aria-label="轨迹筛选">
                  <button
                    v-for="filter in trailFilters"
                    :key="filter.value"
                    class="trail-filter"
                    :class="{ 'trail-filter-active': trailFilter === filter.value }"
                    type="button"
                    role="tab"
                    :aria-selected="trailFilter === filter.value"
                    @click="trailFilter = filter.value"
                  >
                    {{ filter.label }} <small>{{ filter.count }}</small>
                  </button>
                </div>
                <span>{{ visibleRounds.length }} / {{ filteredRounds.length }} 轮</span>
              </div>
            </div>
            <div v-if="trailMeta.length" class="trail-meta">
              <div v-for="item in trailMeta" :key="item.key" class="trail-meta-item">
                <span class="trail-dot" :class="`trail-${item.tone}`"></span>
                <time>{{ formatClock(item.time) }}</time>
                <strong>{{ item.title }}</strong>
                <span>{{ item.detail }}</span>
              </div>
            </div>
            <div v-if="visibleRounds.length" class="round-list">
              <section v-for="round in visibleRounds" :key="round.id" class="trail-round">
                <button class="round-header" type="button" @click="toggleRound(round.id)">
                  <span class="round-chevron" :class="{ expanded: isRoundExpanded(round.id) }"
                    >▸</span
                  >
                  <span class="round-title">第 {{ round.number }} 轮</span>
                  <span class="round-summary"
                    >{{ round.events.length }} 个事件 · {{ formatClock(round.startTime)
                    }}<template v-if="round.endTime"
                      >—{{ formatClock(round.endTime) }}</template
                    ></span
                  >
                  <span class="round-state">{{ isRoundExpanded(round.id) ? '收起' : '展开' }}</span>
                </button>
                <div v-if="isRoundExpanded(round.id)" class="round-cards">
                  <article
                    v-for="item in round.events"
                    :key="item.key"
                    class="round-card"
                    :class="`round-card-${item.tone}`"
                  >
                    <div class="round-card-heading">
                      <span class="trail-dot" :class="`trail-${item.tone}`"></span>
                      <strong :title="item.title">{{ item.title }}</strong>
                    </div>
                    <div class="round-card-time">
                      {{ formatClock(item.time) }} <span>{{ item.duration }}</span>
                    </div>
                    <p :title="item.detail">{{ item.detail }}</p>
                    <small v-if="item.digest">{{ item.digest }}</small>
                    <span class="round-card-status">{{ trailStatusLabel(item) }}</span>
                  </article>
                </div>
              </section>
            </div>
            <div v-else class="empty-block">
              {{ trail.length ? '当前筛选没有匹配的执行轮次' : '任务尚未生成执行审计记录' }}
            </div>
            <button
              v-if="filteredRounds.length > visibleRounds.length"
              class="trail-more"
              type="button"
              @click="showAllRounds = true"
            >
              展开其他 {{ filteredRounds.length - visibleRounds.length }} 轮
            </button>
            <button
              v-else-if="showAllRounds && filteredRounds.length > 2"
              class="trail-more"
              type="button"
              @click="showAllRounds = false"
            >
              收起较早轮次
            </button>
          </article>

          <aside class="side-column">
            <article class="surface-card snapshot-card">
              <div class="card-heading">
                <div>
                  <h2>本次执行快照</h2>
                  <p>配置已冻结，后续修改不影响本次执行</p>
                </div>
              </div>
              <div v-if="detail.execution" class="snapshot-list">
                <section>
                  <h3>Prompt</h3>
                  <p>系统提示词与任务指令已固化</p>
                  <code>{{ shortHash(detail.execution.promptHash) }}</code>
                </section>
                <section>
                  <h3>Skill</h3>
                  <p>
                    绑定 {{ detail.execution.skill.boundSkills.length }} · 选中
                    {{ detail.execution.skill.selectedSkillVersionIds.length }} · 激活
                    {{ activatedSkillCount }}
                  </p>
                  <small>{{ skillModeLabel }}</small>
                </section>
                <section>
                  <h3>工具与 MCP</h3>
                  <p>
                    Workbench {{ localToolCount }} 个工具 · 外部 MCP
                    {{ detail.execution.externalMcps.length }} 个服务
                  </p>
                  <small>共 {{ detail.execution.toolDefinitions.length }} 个工具定义</small>
                </section>
                <section>
                  <h3>模型</h3>
                  <p>
                    {{
                      detail.execution.model.displayName || detail.execution.model.modelKey || '—'
                    }}
                  </p>
                  <small>配置版本 {{ detail.execution.model.configVersion ?? '—' }}</small>
                </section>
              </div>
              <div v-else class="empty-block compact">等待 Agent 冻结执行上下文</div>
            </article>

            <article class="surface-card output-card">
              <div class="card-heading">
                <div><h2>输出预览</h2></div>
              </div>
              <template v-if="detail.output">
                <strong>{{ outputTitle }}</strong>
                <p>{{ task.resultSummary || '任务已生成业务结果。' }}</p>
                <el-button
                  v-if="detail.output.type === 'DRAFT_DOCUMENT'"
                  type="primary"
                  plain
                  @click="openOutputDocument"
                  >打开草稿文档</el-button
                >
                <template v-else>
                  <el-button
                    v-if="canOpenChangeRequest"
                    type="primary"
                    plain
                    @click="openOutputChangeRequest"
                    >查看变更审批</el-button
                  >
                </template>
              </template>
              <template v-else
                ><strong>{{ isActive ? '正在等待任务产物' : '未生成业务产物' }}</strong>
                <p>{{ task.resultSummary || '执行完成后将在这里显示结果引用。' }}</p></template
              >
              <el-alert
                v-if="task.errorMessage"
                type="error"
                :closable="false"
                :title="task.errorMessage"
              />
            </article>
          </aside>
        </div>

        <article class="surface-card instruction-card">
          <h2>任务指令</h2>
          <p>{{ task.instruction }}</p>
          <div v-if="task.focusRegions.length" class="focus-regions">
            <div v-for="(region, index) in task.focusRegions" :key="`${region.start}-${index}`">
              <strong>区域 {{ index + 1 }} · {{ region.length }} 字符</strong>
              <p>{{ region.textPreview }}</p>
              <small>{{ region.instruction }}</small>
            </div>
          </div>
        </article>
      </template>
    </DataState>
  </section>
</template>

<script setup lang="ts">
import { ElAlert, ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { normalizeApiError } from '@/api/errors'
import {
  getTaskExecutionDetail,
  rerunTask,
  runTask,
  terminateTask,
} from '@/features/task/api/task-api'
import type { TaskExecutionDetail, TaskStatus } from '@/features/task/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

interface TrailEvent {
  key: string
  time: string
  title: string
  detail: string
  digest: string | null
  duration: string
  tone: 'success' | 'active' | 'error' | 'neutral'
  kind: 'task' | 'snapshot' | 'model' | 'tool'
  order: number
}
interface TrailRound {
  id: string
  number: number
  startTime: string
  endTime: string | null
  events: TrailEvent[]
}
type TrailFilter = 'ALL' | 'MODEL' | 'TOOL' | 'ERROR'
const route = useRoute(),
  router = useRouter(),
  workspace = useWorkspaceStore()
const detail = ref<TaskExecutionDetail | null>(null),
  loading = ref(false),
  refreshing = ref(false),
  running = ref(false),
  terminating = ref(false),
  rerunning = ref(false),
  error = ref(''),
  trailFilter = ref<TrailFilter>('ALL'),
  showAllRounds = ref(false),
  expandedRoundIds = ref<Set<string>>(new Set())
let controller: AbortController | null = null,
  pollTimer: number | null = null
const spaceId = computed(() => route.params.spaceId as string),
  taskId = computed(() => route.params.taskId as string),
  task = computed(() => detail.value?.task ?? null)
const activeStatuses: TaskStatus[] = [
  'PENDING',
  'DISPATCHED',
  'RUNNING',
  'WAITING_INPUT',
  'WAITING_AUTH',
  'CANCELING',
]
const isActive = computed(() => Boolean(task.value && activeStatuses.includes(task.value.status)))
const canTerminate = computed(() =>
  Boolean(
    task.value &&
    activeStatuses.includes(task.value.status) &&
    workspace.hasPermission(SPACE_PERMISSIONS.TASK_TERMINATE),
  ),
)
const canRun = computed(() =>
  Boolean(
    task.value?.status === 'PENDING' && workspace.hasPermission(SPACE_PERMISSIONS.TASK_CREATE),
  ),
)
const canOpenChangeRequest = computed(() =>
  workspace.hasPermission(SPACE_PERMISSIONS.CHANGE_REQUEST_READ),
)
const canRerun = computed(() =>
  Boolean(
    task.value?.status === 'FAILED' && workspace.hasPermission(SPACE_PERMISSIONS.TASK_CREATE),
  ),
)
const headerDescription = computed(() =>
  task.value
    ? `${task.value.taskNo} · ${detail.value?.agentName || `Agent #${task.value.agentId}`}`
    : '查看真实执行轨迹与冻结快照',
)
const successfulToolCount = computed(
  () =>
    detail.value?.execution?.toolCalls.filter((item) => item.status === 'SUCCEEDED').length ?? 0,
)
const usedTokens = computed(() => {
  if (task.value?.tokensUsed != null) return task.value.tokensUsed
  const execution = detail.value?.execution
  return execution?.inputTokens != null && execution.outputTokens != null
    ? execution.inputTokens + execution.outputTokens
    : null
})
const localToolCount = computed(
  () =>
    detail.value?.execution?.toolDefinitions.filter(
      (item) => item.source !== 'MCP_REMOTE' || item.sourceKey === 'workbench',
    ).length ?? 0,
)
const activatedSkillCount = computed(
  () =>
    new Set(
      detail.value?.execution?.toolCalls
        .map((item) => item.skillVersionId)
        .filter((id) => id != null) ?? [],
    ).size,
)
const skillModeLabel = computed(() => {
  const skill = detail.value?.execution?.skill
  if (!skill) return '—'
  return skill.configuredMode === skill.effectiveMode
    ? `选择模式 ${skill.effectiveMode || '—'}`
    : `${skill.configuredMode || '—'} → ${skill.effectiveMode || '—'}`
})
const executionDuration = computed(() => {
  const start = detail.value?.execution?.startedAt || task.value?.startTime || null,
    end = detail.value?.execution?.finishedAt || task.value?.endTime || null
  return durationBetween(start, end || (isActive.value ? new Date().toISOString() : null))
})
const outputTitle = computed(() =>
  detail.value?.output?.type === 'CHANGE_REQUEST' ? '已生成变更请求' : '已更新草稿文档',
)
const trail = computed<TrailEvent[]>(() => {
  if (!task.value) return []
  const events: TrailEvent[] = [
    {
      key: 'task-created',
      time: task.value.createdAt,
      title: '任务已创建',
      detail: `任务 ${task.value.taskNo} 已进入执行队列`,
      digest: null,
      duration: '—',
      tone: 'success',
      kind: 'task',
      order: 0,
    },
  ]
  const execution = detail.value?.execution
  if (execution) {
    events.push({
      key: 'snapshot',
      time: execution.createdAt,
      title: '执行上下文已冻结',
      detail: 'Agent、模型、Skill 与工具配置已形成不可变快照',
      digest: `快照 ${shortHash(execution.executionSnapshotHash)}`,
      duration: '—',
      tone: 'success',
      kind: 'snapshot',
      order: 1,
    })
    execution.modelCalls.forEach((call) =>
      events.push({
        key: `model-${call.sequenceNo}`,
        time: call.startedAt,
        title: `模型调用 #${call.sequenceNo}`,
        detail: `${call.modelKey} · ${call.status}`,
        digest: call.responseSha256
          ? `响应 ${shortHash(call.responseSha256)} · ${formatBytes(call.responseSize)}`
          : call.errorType,
        duration: durationBetween(call.startedAt, call.finishedAt),
        tone: auditTone(call.status),
        kind: 'model',
        order: 100000 + call.sequenceNo,
      }),
    )
    execution.toolCalls.forEach((call) =>
      events.push({
        key: `tool-${call.sequenceNo}`,
        time: call.startedAt,
        title: toolSourceLabel(call.toolSource, call.toolSourceKey, call.mcpServerId),
        detail: `${toolDisplayName(call.toolName)} · ${call.status}`,
        digest: call.resultSha256
          ? `结果 ${shortHash(call.resultSha256)} · ${formatBytes(call.resultSize)}`
          : call.errorType,
        duration: durationBetween(call.startedAt, call.finishedAt),
        tone: auditTone(call.status),
        kind: 'tool',
        order: 200000 + call.sequenceNo,
      }),
    )
  }
  if (task.value.endTime)
    events.push({
      key: 'task-ended',
      time: task.value.endTime,
      title: statusLabel(task.value.status),
      detail: task.value.resultSummary || task.value.errorMessage || '任务执行已结束',
      digest: null,
      duration: '—',
      tone: task.value.status === 'COMPLETED' ? 'success' : 'error',
      kind: 'task',
      order: 300000,
    })
  return events.sort((a, b) => {
    const timeDiff = new Date(b.time).getTime() - new Date(a.time).getTime()
    return timeDiff || b.order - a.order
  })
})
const trailMeta = computed(() =>
  trail.value
    .filter((item) => {
      if (item.kind === 'model' || item.kind === 'tool') return false
      return trailFilter.value === 'ALL' || (trailFilter.value === 'ERROR' && item.tone === 'error')
    })
    .sort(compareTrailAsc),
)
const trailRounds = computed<TrailRound[]>(() => {
  const callEvents = trail.value
    .filter((item) => item.kind === 'model' || item.kind === 'tool')
    .sort(compareTrailAsc)
  const rounds: TrailRound[] = []
  let current: TrailRound | null = null
  callEvents.forEach((item) => {
    if (item.kind === 'model' || !current) {
      current = {
        id: `round-${item.key}`,
        number: rounds.length + 1,
        startTime: item.time,
        endTime: null,
        events: [],
      }
      rounds.push(current)
    }
    current.events.push(item)
    current.endTime = item.time
  })
  return rounds.reverse()
})
const filteredRounds = computed<TrailRound[]>(() => {
  const matches = (item: TrailEvent) => {
    if (trailFilter.value === 'MODEL') return item.kind === 'model'
    if (trailFilter.value === 'TOOL') return item.kind === 'tool'
    if (trailFilter.value === 'ERROR') return item.tone === 'error'
    return true
  }
  return trailRounds.value
    .map((round) => ({ ...round, events: round.events.filter(matches) }))
    .filter((round) => round.events.length > 0)
})
const visibleRounds = computed(() =>
  showAllRounds.value ? filteredRounds.value : filteredRounds.value.slice(0, 2),
)
const trailFilters = computed(() => [
  { value: 'ALL' as TrailFilter, label: '全部', count: trail.value.length },
  {
    value: 'MODEL' as TrailFilter,
    label: '模型',
    count: trail.value.filter((item) => item.kind === 'model').length,
  },
  {
    value: 'TOOL' as TrailFilter,
    label: '工具',
    count: trail.value.filter((item) => item.kind === 'tool').length,
  },
  {
    value: 'ERROR' as TrailFilter,
    label: '异常',
    count: trail.value.filter((item) => item.tone === 'error').length,
  },
])
watch(
  trailRounds,
  (rounds) => {
    if (expandedRoundIds.value.size === 0 && rounds.length > 0) {
      expandedRoundIds.value = new Set(rounds.slice(0, 2).map((round) => round.id))
    }
  },
  { immediate: true },
)
watch(taskId, () => {
  trailFilter.value = 'ALL'
  showAllRounds.value = false
  expandedRoundIds.value = new Set()
})

onMounted(loadDetail)
onBeforeUnmount(stopPolling)
function stopPolling() {
  controller?.abort()
  if (pollTimer !== null) window.clearTimeout(pollTimer)
  pollTimer = null
}
function schedulePoll() {
  if (pollTimer !== null) window.clearTimeout(pollTimer)
  pollTimer = isActive.value ? window.setTimeout(() => loadDetail(true), 3000) : null
}
async function loadDetail(background = false) {
  controller?.abort()
  controller = new AbortController()
  if (background) refreshing.value = true
  else loading.value = true
  if (!background) error.value = ''
  try {
    detail.value = await getTaskExecutionDetail(taskId.value, controller.signal)
  } catch (e) {
    if (!controller.signal.aborted && !detail.value) error.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) {
      loading.value = false
      refreshing.value = false
      schedulePoll()
    }
  }
}
async function terminate() {
  try {
    await ElMessageBox.confirm('确定终止这个任务吗？', '终止任务', { type: 'warning' })
    stopPolling()
    terminating.value = true
    await terminateTask(taskId.value)
    await loadDetail()
    ElMessage.success('任务已终止')
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(normalizeApiError(e).message)
  } finally {
    terminating.value = false
    schedulePoll()
  }
}
async function triggerRun() {
  try {
    running.value = true
    await runTask(taskId.value)
    ElMessage.success('任务已重新提交执行队列')
    await loadDetail()
  } catch (e) {
    ElMessage.error(normalizeApiError(e).message)
  } finally {
    running.value = false
  }
}
async function rerun() {
  try {
    await ElMessageBox.confirm('将复用原任务输入并创建一次新的执行，确定继续吗？', '重新运行任务', {
      type: 'warning',
    })
    stopPolling()
    rerunning.value = true
    const rerunResult = await rerunTask(taskId.value)
    ElMessage.success('新任务已进入执行队列')
    await router.replace(`/spaces/${spaceId.value}/tasks/${rerunResult.id}`)
    await loadDetail()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(normalizeApiError(e).message)
  } finally {
    rerunning.value = false
    schedulePoll()
  }
}
function openOutputDocument() {
  const output = detail.value?.output
  if (output) router.push(`/spaces/${spaceId.value}/documents/${output.documentId}`)
}
function openOutputChangeRequest() {
  const output = detail.value?.output
  if (!output || output.type !== 'CHANGE_REQUEST') return
  router.push({
    name: 'space-approvals',
    params: { spaceId: spaceId.value },
    query: { changeRequestId: String(output.id) },
  })
}
function auditTone(status: string): TrailEvent['tone'] {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'error'
  return status === 'STARTED' ? 'active' : 'neutral'
}
function compareTrailAsc(a: TrailEvent, b: TrailEvent) {
  const timeDiff = new Date(a.time).getTime() - new Date(b.time).getTime()
  return timeDiff || a.order - b.order
}
function isRoundExpanded(roundId: string) {
  return expandedRoundIds.value.has(roundId)
}
function toggleRound(roundId: string) {
  const next = new Set(expandedRoundIds.value)
  if (next.has(roundId)) next.delete(roundId)
  else next.add(roundId)
  expandedRoundIds.value = next
}
function trailStatusLabel(item: TrailEvent) {
  if (item.tone === 'success') return 'SUCCEEDED'
  if (item.tone === 'error') return 'FAILED'
  if (item.tone === 'active') return 'RUNNING'
  return '—'
}
const labels: Record<TaskStatus, string> = {
  PENDING: '待运行',
  DISPATCHED: '已分发',
  RUNNING: '运行中',
  WAITING_INPUT: '等待输入',
  WAITING_AUTH: '等待授权',
  CANCELING: '取消中',
  COMPLETED: '已完成',
  TERMINATED: '已终止',
  FAILED: '异常失败',
}
function statusLabel(value: TaskStatus) {
  return labels[value]
}
function formatNumber(value: number | null) {
  return value == null ? '—' : value.toLocaleString('zh-CN')
}
function toolSourceLabel(
  source: string,
  sourceKey: string | null,
  mcpServerId: string | number | null,
) {
  if (source === 'MCP_REMOTE' && (sourceKey === 'workbench' || mcpServerId == null)) {
    return '调用 Workbench MCP'
  }
  if (source === 'MCP_REMOTE') return '调用外部 MCP'
  return '调用工具'
}
function toolDisplayName(toolName: string) {
  const names: Record<string, string> = {
    workbench_get_task_context: '获取任务上下文',
    workbench_read_document_fragment: '读取文档片段',
    workbench_apply_draft_changes: '应用草稿变更',
    workbench_propose_changes: '提交变更提案',
  }
  return names[toolName] || toolName
}
function formatLimit(value: number | null | undefined, unit: string) {
  return value == null ? '—' : `${value} ${unit}`
}
function formatClock(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}
function shortHash(value: string | null) {
  return value ? `${value.slice(0, 10)}…` : '—'
}
function formatBytes(value: number | null) {
  return value == null ? '—' : value < 1024 ? `${value} B` : `${(value / 1024).toFixed(1)} KB`
}
function durationBetween(start: string | null, end: string | null) {
  if (!start || !end) return '—'
  const ms = Math.max(0, new Date(end).getTime() - new Date(start).getTime())
  if (ms < 1000) return `${ms}ms`
  const seconds = Math.floor(ms / 1000)
  return seconds < 60 ? `${seconds}s` : `${Math.floor(seconds / 60)}m ${seconds % 60}s`
}
</script>

<style scoped>
.detail-page {
  display: grid;
  gap: 18px;
}
.back-link {
  padding: 0;
  border: 0;
  color: var(--adw-color-primary);
  background: none;
  cursor: pointer;
}
.status-line {
  display: flex;
  align-items: center;
  gap: 14px;
  color: var(--adw-text-secondary);
}
.refresh-state {
  margin-left: auto;
  color: var(--adw-text-tertiary);
  font-size: 13px;
}
.status-pill {
  padding: 5px 11px;
  border-radius: 6px;
  background: var(--adw-color-primary-soft);
  color: var(--adw-color-primary);
  font-weight: 600;
}
.status-running,
.status-dispatched,
.status-pending,
.status-waiting_input,
.status-waiting_auth,
.status-canceling {
  background: var(--adw-color-warning-soft);
  color: var(--adw-color-warning);
}
.status-completed {
  background: var(--adw-color-success-soft);
  color: var(--adw-color-success);
}
.status-failed,
.status-terminated {
  background: #fff0f0;
  color: var(--adw-color-danger);
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.metric-card {
  position: relative;
  overflow: hidden;
  padding: 18px 20px;
  border-top: 3px solid var(--metric-color);
}
.metric-card::after {
  position: absolute;
  right: -16px;
  bottom: -26px;
  width: 82px;
  height: 82px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--metric-color) 9%, transparent);
  content: '';
}
.metric-card > span {
  color: var(--adw-text-secondary);
}
.metric-card strong {
  display: block;
  margin-top: 8px;
  font-size: 25px;
}
.metric-card strong small {
  color: var(--adw-text-secondary);
  font-size: 15px;
  font-weight: 500;
}
.metric-card p {
  margin: 5px 0 0;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.metric-token {
  --metric-color: var(--adw-color-success);
}
.metric-duration {
  --metric-color: var(--adw-color-primary);
}
.metric-tools {
  --metric-color: var(--adw-color-accent);
}
.metric-iterations {
  --metric-color: var(--adw-color-warning);
}
.execution-layout {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: start;
}
.trail-card,
.snapshot-card,
.output-card,
.instruction-card {
  padding: 20px;
}
.card-heading {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.card-heading h2,
.instruction-card h2 {
  margin: 0;
  font-size: 18px;
}
.card-heading p {
  margin: 4px 0 0;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.card-heading > span {
  color: var(--adw-text-tertiary);
  font-size: 13px;
}
.trail-heading-tools {
  display: grid;
  justify-items: end;
  gap: 8px;
  color: var(--adw-text-tertiary);
  font-size: 13px;
}
.trail-filters {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
}
.trail-filter,
.trail-more {
  border: 0;
  border-radius: 6px;
  color: var(--adw-text-secondary);
  background: var(--adw-bg-muted, #f5f7fa);
  cursor: pointer;
  font: inherit;
}
.trail-filter {
  padding: 5px 8px;
  font-size: 12px;
}
.trail-filter small {
  color: var(--adw-text-tertiary);
}
.trail-filter-active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.trail-filter-active small {
  color: inherit;
}
.trail-meta {
  display: grid;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: 8px;
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.trail-meta-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}
.trail-meta-item .trail-dot {
  margin-top: 0;
}
.round-list {
  border: 1px solid var(--adw-border-color-light);
  border-radius: 9px;
  overflow: hidden;
}
.trail-round + .trail-round {
  border-top: 1px solid var(--adw-border-color-light);
}
.round-header {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 9px;
  padding: 13px 15px;
  border: 0;
  color: var(--adw-text-primary);
  background: var(--adw-bg-muted, #f8fafc);
  cursor: pointer;
  font: inherit;
  text-align: left;
}
.round-header:hover {
  background: var(--adw-color-primary-soft);
}
.round-chevron {
  color: var(--adw-color-primary);
  transition: transform 0.15s ease;
}
.round-chevron.expanded {
  transform: rotate(90deg);
}
.round-title {
  font-weight: 700;
}
.round-summary {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.round-state {
  margin-left: auto;
  color: var(--adw-color-primary);
  font-size: 12px;
}
.round-cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 12px;
  background: var(--adw-surface-subtle, #fbfcfe);
}
.round-card {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 148px;
  padding: 13px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: 9px;
  background: white;
  box-shadow: 0 2px 8px rgb(15 23 42 / 4%);
}
.round-card-success {
  border-color: color-mix(in srgb, var(--adw-color-success) 28%, white);
}
.round-card-error {
  border-color: color-mix(in srgb, var(--adw-color-danger) 55%, white);
  background: color-mix(in srgb, var(--adw-color-danger) 5%, white);
}
.round-card-heading {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  min-height: 20px;
}
.round-card-heading .trail-dot {
  flex: 0 0 auto;
  margin-top: 4px;
}
.round-card-heading strong {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.round-card-time {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.round-card-time span {
  color: var(--adw-text-secondary);
}
.round-card p {
  display: -webkit-box;
  min-height: 34px;
  margin: 9px 0 5px;
  overflow: hidden;
  color: var(--adw-text-secondary);
  font-size: 13px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.round-card small {
  display: block;
  overflow: hidden;
  color: var(--adw-text-tertiary);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.round-card-status {
  align-self: flex-start;
  margin-top: auto;
  transform: translateY(4px);
  padding: 3px 8px;
  border-radius: 999px;
  color: var(--adw-color-success);
  background: var(--adw-color-success-soft);
  font-size: 11px;
  font-weight: 600;
}
.round-card-error .round-card-status {
  color: var(--adw-color-danger);
  background: #ffe8e8;
}
.trail-dot {
  width: 10px;
  height: 10px;
  margin-top: 4px;
  border: 2px solid var(--adw-text-tertiary);
  border-radius: 50%;
  background: white;
}
.trail-success {
  border-color: var(--adw-color-success);
  background: var(--adw-color-success);
}
.trail-active {
  border-color: var(--adw-color-warning);
}
.trail-error {
  border-color: var(--adw-color-danger);
  background: var(--adw-color-danger);
}
.trail-more {
  display: block;
  width: 100%;
  margin-top: 10px;
  padding: 9px;
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.side-column {
  display: grid;
  gap: 16px;
}
.snapshot-list section {
  padding: 14px 0;
  border-top: 1px solid var(--adw-border-color-light);
}
.snapshot-list section:first-child {
  padding-top: 0;
  border-top: 0;
}
.snapshot-list h3 {
  margin: 0;
  font-size: 15px;
}
.snapshot-list p {
  margin: 6px 0;
  color: var(--adw-text-secondary);
}
.snapshot-list small,
.snapshot-list code {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.output-card > strong {
  display: block;
  margin-bottom: 8px;
}
.output-card > p {
  color: var(--adw-text-secondary);
  line-height: 1.6;
}
.output-card .el-alert {
  margin-top: 14px;
}
.output-card > small {
  color: var(--adw-color-warning);
}
.instruction-card > p {
  white-space: pre-wrap;
  line-height: 1.7;
}
.focus-regions {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}
.focus-regions > div {
  padding: 12px;
  border: 1px solid var(--adw-border-color);
  border-radius: 8px;
}
.focus-regions p {
  margin: 6px 0;
  color: var(--adw-text-secondary);
}
.focus-regions small {
  color: var(--adw-color-primary);
}
.empty-block {
  padding: 46px 20px;
  color: var(--adw-text-tertiary);
  text-align: center;
}
.empty-block.compact {
  padding: 24px 10px;
}
@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: 1fr 1fr;
  }
  .execution-layout {
    grid-template-columns: 1fr;
  }
  .round-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 640px) {
  .metric-grid {
    grid-template-columns: 1fr;
  }
  .status-line {
    flex-wrap: wrap;
  }
  .refresh-state {
    width: 100%;
    margin-left: 0;
  }
  .trail-heading-tools {
    justify-items: start;
  }
  .trail-filters {
    justify-content: flex-start;
  }
  .round-header {
    flex-wrap: wrap;
  }
  .round-summary {
    flex: 1 0 100%;
    margin-left: 19px;
  }
  .round-cards {
    grid-template-columns: 1fr;
  }
}
</style>
