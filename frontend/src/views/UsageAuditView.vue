<template>
  <section class="usage-page">
    <PageHeader title="用量与审计" description="追踪 Token 消耗、预算状态与脱敏执行记录">
      <template #breadcrumb>
        <span class="usage-breadcrumb">洞察 / 用量与审计</span>
      </template>
      <template #actions>
        <el-select v-model="presetDays" class="period-select" @change="applyPreset">
          <el-option label="近 7 天" :value="7" />
          <el-option label="近 30 天" :value="30" />
        </el-select>
        <el-button v-if="canExport" :loading="exporting" @click="exportRecords">
          <el-icon><Download /></el-icon>
          导出执行记录
        </el-button>
      </template>
    </PageHeader>

    <div class="filter-bar surface-card">
      <label>
        <span>Agent</span>
        <el-select v-model="filters.agentId" clearable placeholder="全部">
          <el-option
            v-for="agent in agents"
            :key="agent.id"
            :label="agent.name"
            :value="agent.id"
          />
        </el-select>
      </label>
      <label>
        <span>模型</span>
        <el-select v-model="filters.modelId" clearable placeholder="全部">
          <el-option
            v-for="model in models"
            :key="model.id"
            :label="model.displayName"
            :value="model.id"
          />
        </el-select>
      </label>
      <label>
        <span>执行状态</span>
        <el-select v-model="filters.status" clearable placeholder="全部">
          <el-option
            v-for="option in statusOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </label>
      <label class="filter-bar__date">
        <span>日期范围</span>
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :clearable="false"
        />
      </label>
      <el-button type="primary" :loading="loading" @click="applyFilters">查询</el-button>
      <el-button :disabled="loading" @click="resetFilters">重置</el-button>
    </div>

    <DataState :loading="loading && !dashboard" :error="errorMessage" @retry="loadUsage">
      <template v-if="dashboard">
        <div class="metric-grid">
          <UsageMetricCard
            title="总 Token"
            :value="formatTokens(dashboard.summary.tokens)"
            :change="changeLabel(dashboard.summary.tokens, dashboard.previousSummary.tokens)"
            tone="blue"
          >
            <Coin />
          </UsageMetricCard>
          <UsageMetricCard
            title="预估成本"
            :value="formatCost(dashboard.summary.estimatedCost)"
            :change="
              changeLabel(dashboard.summary.estimatedCost, dashboard.previousSummary.estimatedCost)
            "
            tone="green"
          >
            <Money />
          </UsageMetricCard>
          <UsageMetricCard
            title="执行任务"
            :value="formatInteger(dashboard.summary.executedTasks)"
            :change="
              changeLabel(dashboard.summary.executedTasks, dashboard.previousSummary.executedTasks)
            "
            tone="purple"
          >
            <Checked />
          </UsageMetricCard>
          <UsageMetricCard
            title="工具调用"
            :value="formatInteger(dashboard.summary.toolCalls)"
            :change="changeLabel(dashboard.summary.toolCalls, dashboard.previousSummary.toolCalls)"
            tone="orange"
          >
            <Tools />
          </UsageMetricCard>
        </div>

        <div class="insight-grid">
          <article class="chart-card surface-card">
            <header class="card-header">
              <div>
                <h2>Token 消耗趋势</h2>
                <p>{{ dashboard.startDate }} 至 {{ dashboard.endDate }}</p>
              </div>
            </header>
            <EChartCanvas
              v-if="hasTrendData"
              class="usage-chart"
              role="img"
              aria-label="Token 消耗趋势图"
              :option="trendChartOption"
            />
            <div v-else class="panel-empty">当前筛选范围还没有 Token 用量</div>
          </article>

          <article class="source-card surface-card">
            <header class="card-header">
              <div>
                <h2>能力来源分布</h2>
                <p>按工具实际调用次数统计</p>
              </div>
            </header>
            <div v-if="dashboard.summary.toolCalls" class="source-content">
              <EChartCanvas
                class="source-chart"
                role="img"
                aria-label="能力来源分布环图"
                :option="sourceChartOption"
              />
              <div class="source-legend">
                <div v-for="source in normalizedSources" :key="source.source">
                  <i :style="{ background: source.color }"></i>
                  <span>{{ source.label }}</span>
                  <strong>{{ source.percent }}%</strong>
                  <small>{{ source.calls }} 次</small>
                </div>
              </div>
            </div>
            <div v-else class="panel-empty">当前范围没有工具调用</div>
            <div class="budget-summary">
              <div>
                <span>本月预算</span>
                <strong>{{ budgetLabel }}</strong>
              </div>
              <el-progress
                v-if="budgetPercent !== null"
                :percentage="Math.min(100, budgetPercent)"
                :stroke-width="8"
                :show-text="false"
              />
              <small>{{ budgetDescription }}</small>
            </div>
          </article>
        </div>

        <article class="records-card surface-card">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="执行记录" name="executions">
              <el-table :data="taskPage.records" stripe @row-click="openExecution">
                <el-table-column prop="name" label="任务" min-width="190">
                  <template #default="{ row }">
                    <div class="task-cell">
                      <strong>{{ row.name }}</strong>
                      <span>{{ row.taskNo }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="agentName" label="Agent" min-width="135">
                  <template #default="{ row }">{{ row.agentName || `#${row.agentId}` }}</template>
                </el-table-column>
                <el-table-column prop="tokensUsed" label="Token" width="120">
                  <template #default="{ row }">{{ formatTokens(row.tokensUsed) }}</template>
                </el-table-column>
                <el-table-column label="耗时" width="105">
                  <template #default="{ row }">{{
                    durationLabel(row.startTime, row.endTime)
                  }}</template>
                </el-table-column>
                <el-table-column prop="status" label="状态" width="115">
                  <template #default="{ row }">
                    <el-tag :type="statusType(row.status)" effect="light">{{
                      statusLabel(row.status)
                    }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="startTime" label="执行时间" min-width="170">
                  <template #default="{ row }">{{
                    formatDateTime(row.startTime || row.createdAt)
                  }}</template>
                </el-table-column>
                <el-table-column label="操作" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="primary" @click.stop="openExecution(row)"
                      >查看审计</el-button
                    >
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="!taskPage.records.length" class="panel-empty">
                当前筛选范围没有执行记录
              </div>
              <el-pagination
                v-if="taskPage.total"
                class="records-pagination"
                background
                layout="total, sizes, prev, pager, next"
                :total="taskPage.total"
                :page-size="taskPage.pageSize"
                :current-page="taskPage.pageNum"
                :page-sizes="[10, 20, 50]"
                @current-change="changeTaskPage"
                @size-change="changeTaskPageSize"
              />
            </el-tab-pane>
            <el-tab-pane label="工具调用" name="tools">
              <div v-if="dashboard.summary.toolCalls" class="tool-usage-panel">
                <div class="tool-usage-panel__summary">
                  <span>当前周期共</span>
                  <strong>{{ formatInteger(dashboard.summary.toolCalls) }}</strong>
                  <span>次工具调用</span>
                </div>
                <p class="tool-usage-panel__hint">
                  下方展示当前执行记录页对应的脱敏工具调用明细，可点击“查看审计”查看完整执行上下文。
                </p>
                <DataState
                  :loading="toolCallsLoading"
                  :error="toolCallsError"
                  :empty="!toolCallsLoading && !toolCallRows.length"
                  empty-text="当前执行记录页没有工具调用明细"
                  @retry="loadToolCalls"
                >
                  <el-table :data="toolCallRows" stripe>
                    <el-table-column prop="taskName" label="任务" min-width="190">
                      <template #default="{ row }">
                        <div class="task-cell">
                          <strong>{{ row.taskName }}</strong>
                          <span>{{ row.taskNo }}</span>
                        </div>
                      </template>
                    </el-table-column>
                    <el-table-column prop="agentName" label="Agent" min-width="135" />
                    <el-table-column prop="toolName" label="工具" min-width="220">
                      <template #default="{ row }">
                        <strong>{{ row.toolName }}</strong>
                      </template>
                    </el-table-column>
                    <el-table-column prop="sourceLabel" label="来源" min-width="150" />
                    <el-table-column prop="status" label="状态" width="105">
                      <template #default="{ row }">
                        <el-tag :type="toolCallStatusType(row.status)" effect="light">
                          {{ toolCallStatusLabel(row.status) }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="startedAt" label="调用时间" min-width="175">
                      <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
                    </el-table-column>
                    <el-table-column label="详情" width="100" fixed="right">
                      <template #default="{ row }">
                        <el-button link type="primary" @click="openExecution(row.task)">
                          查看审计
                        </el-button>
                      </template>
                    </el-table-column>
                  </el-table>
                </DataState>
              </div>
              <div v-else class="panel-empty">当前筛选范围没有工具调用</div>
            </el-tab-pane>
            <el-tab-pane v-if="canReadAudit" label="审计事件" name="audit">
              <el-table :data="auditPage.records" stripe>
                <el-table-column prop="actorName" label="主体" min-width="130" />
                <el-table-column prop="actionName" label="操作" min-width="190" />
                <el-table-column prop="targetTypeName" label="目标类型" min-width="150" />
                <el-table-column prop="targetId" label="目标 ID" min-width="130" />
                <el-table-column prop="traceId" label="Trace ID" min-width="180">
                  <template #default="{ row }">{{ row.traceId || '—' }}</template>
                </el-table-column>
                <el-table-column prop="createdAt" label="时间" min-width="170">
                  <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
                </el-table-column>
              </el-table>
              <div v-if="!auditPage.records.length" class="panel-empty">
                当前筛选范围没有审计事件
              </div>
              <el-pagination
                v-if="auditPage.total"
                class="records-pagination"
                background
                layout="total, prev, pager, next"
                :total="auditPage.total"
                :page-size="auditPage.pageSize"
                :current-page="auditPage.pageNum"
                @current-change="changeAuditPage"
              />
            </el-tab-pane>
          </el-tabs>
        </article>
      </template>
    </DataState>

    <el-drawer v-model="drawerVisible" title="执行审计摘要" size="460px">
      <DataState :loading="detailLoading" :error="detailError">
        <div v-if="executionDetail" class="audit-drawer">
          <dl>
            <div>
              <dt>执行 ID</dt>
              <dd>{{ executionDetail.execution?.id || '—' }}</dd>
            </div>
            <div>
              <dt>Agent</dt>
              <dd>{{ executionDetail.agentName || '—' }}</dd>
            </div>
            <div>
              <dt>模型</dt>
              <dd>{{ executionDetail.execution?.model.displayName || '—' }}</dd>
            </div>
            <div>
              <dt>Prompt Hash</dt>
              <dd class="mono">{{ executionDetail.execution?.promptHash || '—' }}</dd>
            </div>
            <div>
              <dt>Skill 模式</dt>
              <dd>{{ executionDetail.execution?.skill.effectiveMode || '—' }}</dd>
            </div>
            <div>
              <dt>已选 Skill</dt>
              <dd>{{ executionDetail.execution?.skill.selectedSkillVersionIds.length || 0 }}</dd>
            </div>
            <div>
              <dt>外部 MCP</dt>
              <dd>{{ executionDetail.execution?.externalMcps.length || 0 }}</dd>
            </div>
          </dl>
          <section>
            <h3>工具调用（{{ executionDetail.execution?.toolCalls.length || 0 }}）</h3>
            <div
              v-for="call in executionDetail.execution?.toolCalls || []"
              :key="call.sequenceNo"
              class="tool-call-row"
            >
              <div>
                <strong>{{ call.toolName }}</strong>
                <span>{{ toolSourceLabel(call.toolSourceKey, call.toolSource) }}</span>
              </div>
              <el-tag
                :type="
                  call.status === 'SUCCEEDED'
                    ? 'success'
                    : call.status === 'FAILED'
                      ? 'danger'
                      : 'warning'
                "
                size="small"
              >
                {{ call.status }}
              </el-tag>
            </div>
            <div v-if="!executionDetail.execution?.toolCalls.length" class="drawer-empty">
              暂无工具调用
            </div>
          </section>
          <el-button type="primary" class="detail-link" @click="goToTaskDetail"
            >查看完整执行详情</el-button
          >
        </div>
      </DataState>
    </el-drawer>
  </section>
</template>

<script setup lang="ts">
import { Checked, Coin, Download, Money, Tools } from '@element-plus/icons-vue'
import {
  ElButton,
  ElDatePicker,
  ElDrawer,
  ElIcon,
  ElMessage,
  ElOption,
  ElPagination,
  ElProgress,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTabPane,
  ElTabs,
  ElTag,
} from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import { listAgents, listModels, type AgentOption } from '@/features/agent/api/agent-api'
import type { ModelOption } from '@/features/agent/types'
import { getTaskExecutionDetail, searchTasks } from '@/features/task/api/task-api'
import type { TaskExecutionDetail, TaskListItem, TaskPage, TaskStatus } from '@/features/task/types'
import {
  exportUsageRecords,
  queryAuditLogs,
  queryUsageDashboard,
} from '@/features/usage/api/usage-api'
import type { AuditLogPage, ToolSource, UsageDashboard } from '@/features/usage/types'
import EChartCanvas from '@/features/usage/components/EChartCanvas.vue'
import UsageMetricCard, { type MetricChange } from '@/features/usage/components/UsageMetricCard.vue'
import { buildUsageChartData } from '@/features/usage/usage-chart'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

type ToolCallItem = NonNullable<TaskExecutionDetail['execution']>['toolCalls'][number]

interface UsageToolCallRow extends ToolCallItem {
  task: TaskListItem
  taskName: string
  taskNo: string
  agentName: string
  sourceLabel: string
}

const router = useRouter()
const workspaceStore = useWorkspaceStore()
const dashboard = ref<UsageDashboard | null>(null)
const loading = ref(false)
const exporting = ref(false)
const errorMessage = ref('')
const presetDays = ref(7)
const dateRange = ref<[string, string]>(presetRange(7))
const agents = ref<AgentOption[]>([])
const models = ref<ModelOption[]>([])
const filters = reactive<{
  agentId?: string | number
  modelId?: string | number
  status?: TaskStatus
}>({})
const taskPage = ref<TaskPage>({ records: [], total: 0, pageNum: 1, pageSize: 10 })
const auditPage = ref<AuditLogPage>({ records: [], total: 0, pageNum: 1, pageSize: 10 })
const activeTab = ref('executions')
const drawerVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const executionDetail = ref<TaskExecutionDetail | null>(null)
const selectedTaskId = ref<string | number | null>(null)
const toolCallsLoading = ref(false)
const toolCallsError = ref('')
const toolCallRows = ref<UsageToolCallRow[]>([])
const toolCallsLoadedKey = ref<string | null>(null)
let requestController: AbortController | null = null
let loadSequence = 0
let toolCallLoadSequence = 0

const canReadAudit = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.AUDIT_READ))
const canExport = computed(() => workspaceStore.hasPermission(SPACE_PERMISSIONS.USAGE_EXPORT))
const hasTrendData = computed(() => dashboard.value?.trend.some((item) => item.hasData) ?? false)
const statusOptions: Array<{ value: TaskStatus; label: string }> = [
  { value: 'COMPLETED', label: '已完成' },
  { value: 'RUNNING', label: '运行中' },
  { value: 'WAITING_INPUT', label: '等待输入' },
  { value: 'WAITING_AUTH', label: '等待授权' },
  { value: 'FAILED', label: '异常失败' },
  { value: 'TERMINATED', label: '已终止' },
]

const chartData = computed(() => buildUsageChartData(dashboard.value?.trend ?? []))
const trendChartOption = computed(() => ({
  animationDuration: 450,
  color: ['#2f6de0', '#32aaa5', '#f59e0b'],
  grid: { left: 74, right: 24, top: 44, bottom: 32 },
  legend: {
    top: 0,
    right: 0,
    itemWidth: 10,
    itemHeight: 10,
    itemGap: 14,
    textStyle: { color: '#68758b', fontSize: 12 },
    data: ['输入 Token', '输出 Token', '总 Token'],
  },
  tooltip: {
    trigger: 'axis',
    axisPointer: {
      type: 'cross',
      lineStyle: { color: '#aeb7c5', type: 'dashed' },
    },
    backgroundColor: '#ffffff',
    borderColor: '#e2e7ef',
    borderWidth: 1,
    textStyle: { color: '#182033', fontSize: 12 },
    valueFormatter: (value: number | null) =>
      value === null ? '暂无数据' : `${formatInteger(value)} Token`,
  },
  xAxis: {
    type: 'category',
    boundaryGap: false,
    data: chartData.value.dates,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: '#dfe3e9' } },
    axisLabel: { color: '#7b8495', hideOverlap: true },
  },
  yAxis: {
    type: 'value',
    name: 'Token',
    max: chartData.value.max,
    nameGap: 16,
    nameTextStyle: { color: '#7b8495', align: 'left' },
    axisLabel: { color: '#7b8495', formatter: compactNumber },
    splitLine: { lineStyle: { color: '#e8ebf0' } },
  },
  series: [
    {
      name: '输入 Token',
      type: 'line',
      smooth: 0.25,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2 },
      data: chartData.value.inputTokens,
    },
    {
      name: '输出 Token',
      type: 'line',
      smooth: 0.25,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2 },
      data: chartData.value.outputTokens,
    },
    {
      name: '总 Token',
      type: 'line',
      smooth: 0.25,
      symbol: 'circle',
      symbolSize: 5,
      lineStyle: { width: 2.5 },
      itemStyle: { borderColor: '#ffffff', borderWidth: 2 },
      data: chartData.value.totalTokens,
    },
  ],
}))

const sourceMeta: Record<ToolSource, { label: string; color: string }> = {
  WORKBENCH_MCP: { label: 'Workbench MCP', color: '#2f6de0' },
  EXTERNAL_MCP: { label: '外部 MCP', color: '#32aaa5' },
  SKILL_LOCAL: { label: 'Skill 本地工具', color: '#f39a38' },
}
const normalizedSources = computed(() => {
  const counts = new Map(
    dashboard.value?.toolSources.map((item) => [item.source, item.calls]) ?? [],
  )
  const total = dashboard.value?.summary.toolCalls || 0
  return (Object.keys(sourceMeta) as ToolSource[]).map((source) => ({
    source,
    ...sourceMeta[source],
    calls: counts.get(source) ?? 0,
    percent: total ? Math.round(((counts.get(source) ?? 0) / total) * 100) : 0,
  }))
})
const sourceChartOption = computed(() => ({
  animationDuration: 450,
  color: normalizedSources.value.map((item) => item.color),
  tooltip: { trigger: 'item', formatter: '{b}<br/>{c} 次（{d}%）' },
  graphic: [
    {
      type: 'text',
      left: 'center',
      top: '39%',
      style: {
        text: formatInteger(dashboard.value?.summary.toolCalls ?? 0),
        fill: '#182033',
        fontSize: 24,
        fontWeight: 700,
        textAlign: 'center',
      },
    },
    {
      type: 'text',
      left: 'center',
      top: '55%',
      style: {
        text: '次调用',
        fill: '#8992a3',
        fontSize: 12,
        textAlign: 'center',
      },
    },
  ],
  series: [
    {
      name: '能力来源',
      type: 'pie',
      radius: ['55%', '82%'],
      center: ['50%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: {
        show: true,
        position: 'outside',
        color: '#4b5565',
        fontSize: 11,
        formatter: '{b}\n{d}%',
      },
      labelLine: { show: true, length: 8, length2: 6, lineStyle: { color: '#b8c0cc' } },
      labelLayout: { hideOverlap: true },
      data: normalizedSources.value
        .filter((item) => item.calls > 0)
        .map((item) => ({ name: item.label, value: item.calls })),
    },
  ],
}))
const budgetPercent = computed(() => {
  const value = dashboard.value
  return value?.monthlyTokenBudget && value.monthlyTokenBudget > 0
    ? (value.monthlyUsedTokens / value.monthlyTokenBudget) * 100
    : null
})
const budgetLabel = computed(() => {
  if (!dashboard.value) return '—'
  return dashboard.value.monthlyTokenBudget === null
    ? '未设置'
    : `${formatTokens(dashboard.value.monthlyUsedTokens)} / ${formatTokens(dashboard.value.monthlyTokenBudget)}`
})
const budgetDescription = computed(() =>
  budgetPercent.value === null
    ? '当前空间尚未配置月度预算'
    : `已使用 ${budgetPercent.value.toFixed(1)}%`,
)

onMounted(async () => {
  await loadOptions()
  await loadUsage()
})

watch(
  () => workspaceStore.currentSpaceId,
  async () => {
    await loadOptions()
    await loadUsage()
  },
)

watch(activeTab, (tab) => {
  if (tab === 'tools') void loadToolCalls()
})

onBeforeUnmount(() => requestController?.abort())

async function loadOptions() {
  const spaceId = workspaceStore.currentSpaceId
  if (spaceId === null) return
  const [agentResult, modelResult] = await Promise.allSettled([
    listAgents(spaceId),
    listModels(false),
  ])
  agents.value = agentResult.status === 'fulfilled' ? agentResult.value : []
  models.value = modelResult.status === 'fulfilled' ? modelResult.value : []
}

async function loadUsage() {
  const spaceId = workspaceStore.currentSpaceId
  if (spaceId === null) return
  requestController?.abort()
  const controller = new AbortController()
  requestController = controller
  const sequence = ++loadSequence
  loading.value = true
  errorMessage.value = ''
  const [startDate, endDate] = dateRange.value
  try {
    const [usage, tasks, audits] = await Promise.all([
      queryUsageDashboard(
        {
          spaceId,
          startDate,
          endDate,
          agentId: filters.agentId,
          modelId: filters.modelId,
          status: filters.status,
        },
        controller.signal,
      ),
      loadTasks(spaceId, taskPage.value.pageNum, taskPage.value.pageSize, controller.signal),
      canReadAudit.value
        ? queryAuditLogs(
            {
              spaceId,
              createdFrom: `${startDate}T00:00:00`,
              createdTo: `${addDays(endDate, 1)}T00:00:00`,
              pageNum: auditPage.value.pageNum,
              pageSize: auditPage.value.pageSize,
            },
            controller.signal,
          )
        : Promise.resolve({ records: [], total: 0, pageNum: 1, pageSize: 10 }),
    ])
    if (sequence !== loadSequence) return
    dashboard.value = usage
    taskPage.value = tasks
    auditPage.value = audits
    if (activeTab.value === 'tools') void loadToolCalls()
  } catch (error) {
    if (controller.signal.aborted) return
    errorMessage.value = normalizeApiError(error).message
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function loadTasks(
  spaceId: string | number,
  pageNum: number,
  pageSize: number,
  signal?: AbortSignal,
) {
  const [startDate, endDate] = dateRange.value
  return searchTasks(
    {
      spaceId,
      pageNum,
      pageSize,
      agentId: filters.agentId,
      modelId: filters.modelId,
      status: filters.status,
      startedFrom: `${startDate}T00:00:00`,
      startedTo: `${addDays(endDate, 1)}T00:00:00`,
    },
    signal,
  )
}

function applyPreset(days: number) {
  dateRange.value = presetRange(days)
  taskPage.value.pageNum = 1
  auditPage.value.pageNum = 1
  void loadUsage()
}

function applyFilters() {
  taskPage.value.pageNum = 1
  auditPage.value.pageNum = 1
  void loadUsage()
}

function resetFilters() {
  filters.agentId = undefined
  filters.modelId = undefined
  filters.status = undefined
  presetDays.value = 7
  dateRange.value = presetRange(7)
  applyFilters()
}

function changeTaskPage(page: number) {
  taskPage.value.pageNum = page
  void loadUsage()
}

function changeTaskPageSize(size: number) {
  taskPage.value.pageNum = 1
  taskPage.value.pageSize = size
  void loadUsage()
}

function changeAuditPage(page: number) {
  auditPage.value.pageNum = page
  void loadUsage()
}

async function loadToolCalls() {
  const records = taskPage.value.records
  const recordsKey = records.map((task) => String(task.id)).join(',')
  if (recordsKey === toolCallsLoadedKey.value && !toolCallsError.value) return
  const sequence = ++toolCallLoadSequence
  toolCallsLoading.value = true
  toolCallsError.value = ''
  toolCallRows.value = []
  if (!records.length) {
    toolCallsLoading.value = false
    return
  }
  const results = await Promise.allSettled(records.map((task) => getTaskExecutionDetail(task.id)))
  if (sequence !== toolCallLoadSequence) return
  const rows: UsageToolCallRow[] = []
  let failed = false
  results.forEach((result, index) => {
    if (result.status === 'rejected') {
      failed = true
      return
    }
    const task = records[index]
    result.value.execution?.toolCalls.forEach((call) => {
      rows.push({
        ...call,
        task,
        taskName: task.name,
        taskNo: task.taskNo,
        agentName: task.agentName || `#${task.agentId}`,
        sourceLabel: toolSourceLabel(call.toolSourceKey, call.toolSource),
      })
    })
  })
  toolCallRows.value = rows
  if (failed && !rows.length) {
    toolCallsError.value = '工具调用明细加载失败，可点击重试'
  } else {
    toolCallsLoadedKey.value = recordsKey
  }
  toolCallsLoading.value = false
}

async function openExecution(row: unknown) {
  const task = row as TaskListItem
  selectedTaskId.value = task.id
  drawerVisible.value = true
  detailLoading.value = true
  detailError.value = ''
  executionDetail.value = null
  try {
    executionDetail.value = await getTaskExecutionDetail(task.id)
  } catch (error) {
    detailError.value = normalizeApiError(error).message
  } finally {
    detailLoading.value = false
  }
}

function goToTaskDetail() {
  if (selectedTaskId.value === null || workspaceStore.currentSpaceId === null) return
  void router.push(`/spaces/${workspaceStore.currentSpaceId}/tasks/${selectedTaskId.value}`)
}

async function exportRecords() {
  const spaceId = workspaceStore.currentSpaceId
  if (spaceId === null || !canExport.value) return
  exporting.value = true
  try {
    const csv = await exportUsageRecords({
      spaceId,
      agentId: filters.agentId,
      modelId: filters.modelId,
      status: filters.status,
      startedFrom: `${dateRange.value[0]}T00:00:00`,
      startedTo: `${addDays(dateRange.value[1], 1)}T00:00:00`,
    })
    const url = URL.createObjectURL(csv)
    const link = document.createElement('a')
    link.href = url
    link.download = `usage-executions-${dateRange.value[0]}-${dateRange.value[1]}.csv`
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    exporting.value = false
  }
}

function presetRange(days: number): [string, string] {
  const end = new Date()
  const start = new Date(end)
  start.setDate(start.getDate() - days + 1)
  return [localDate(start), localDate(end)]
}

function localDate(date: Date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function addDays(value: string, days: number) {
  const date = new Date(`${value}T00:00:00`)
  date.setDate(date.getDate() + days)
  return localDate(date)
}

function formatTokens(value: number | null | undefined) {
  if (value === null || value === undefined) return '—'
  return new Intl.NumberFormat('zh-CN', {
    notation: value >= 100000 ? 'compact' : 'standard',
    maximumFractionDigits: 1,
  }).format(value)
}

function formatInteger(value: number) {
  return new Intl.NumberFormat('zh-CN').format(value)
}

function compactNumber(value: number) {
  return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 1 }).format(
    value,
  )
}

function formatCost(value: number | null) {
  return value === null ? '—' : `¥ ${value.toFixed(2)}`
}

function formatDateTime(value: string | null | undefined) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value.replace('T', ' ')
    : date.toLocaleString('zh-CN', { hour12: false })
}

function changeLabel(current: number | null, previous: number | null): MetricChange {
  if (current === null || previous === null) {
    return { label: '暂无对比数据', direction: 'none', arrow: '', tone: 'muted' }
  }
  if (previous === 0) {
    return current === 0
      ? { label: '持平', direction: 'flat', arrow: '→', tone: 'neutral' }
      : { label: '新增', direction: 'up', arrow: '↑', tone: 'positive' }
  }
  const percent = ((current - previous) / previous) * 100
  if (percent === 0) return { label: '持平', direction: 'flat', arrow: '→', tone: 'neutral' }
  return {
    label: `${Math.abs(percent).toFixed(1)}%`,
    direction: percent > 0 ? 'up' : 'down',
    arrow: percent > 0 ? '↑' : '↓',
    tone: percent > 0 ? 'positive' : 'negative',
  }
}

function durationLabel(start: string | null, end: string | null) {
  if (!start) return '—'
  const duration = Math.max(
    0,
    ((end ? new Date(end) : new Date()).getTime() - new Date(start).getTime()) / 1000,
  )
  if (duration >= 60) return `${Math.floor(duration / 60)}m${Math.round(duration % 60)}s`
  return `${Math.round(duration)}s`
}

function statusLabel(status: TaskStatus) {
  return (
    statusOptions.find((item) => item.value === status)?.label ||
    (
      { PENDING: '待运行', DISPATCHED: '已分发', CANCELING: '取消中' } as Partial<
        Record<TaskStatus, string>
      >
    )[status] ||
    status
  )
}

function statusType(status: TaskStatus): 'success' | 'warning' | 'danger' | 'info' | 'primary' {
  if (status === 'COMPLETED') return 'success'
  if (status === 'FAILED') return 'danger'
  if (status === 'TERMINATED') return 'info'
  if (status === 'RUNNING') return 'primary'
  return 'warning'
}

function toolSourceLabel(sourceKey: string | null, source: string) {
  if (source === 'WORKBENCH_MCP') return 'Workbench MCP'
  if (source === 'EXTERNAL_MCP') return '外部 MCP'
  if (source === 'SKILL_LOCAL') return 'Skill 本地工具'
  return sourceKey === 'workbench' ? 'Workbench MCP' : sourceKey || '外部 MCP'
}

function toolCallStatusLabel(status: string) {
  return status === 'SUCCEEDED' ? '成功' : status === 'FAILED' ? '失败' : '执行中'
}

function toolCallStatusType(status: string): 'success' | 'warning' | 'danger' | 'info' {
  return status === 'SUCCEEDED' ? 'success' : status === 'FAILED' ? 'danger' : 'warning'
}
</script>

<style scoped>
.usage-page {
  display: grid;
  gap: var(--adw-space-5);
}
.usage-breadcrumb {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.period-select {
  width: 116px;
}
.filter-bar {
  display: grid;
  grid-template-columns: repeat(3, minmax(150px, 1fr)) minmax(280px, 1.6fr) auto auto;
  align-items: end;
  gap: var(--adw-space-4);
  padding: var(--adw-space-4);
}
.filter-bar label {
  display: grid;
  gap: 7px;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.filter-bar__date :deep(.el-date-editor) {
  width: 100%;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--adw-space-4);
}
.insight-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(290px, 0.9fr);
  gap: var(--adw-space-4);
}
.chart-card,
.source-card,
.records-card {
  min-width: 0;
  padding: var(--adw-space-5);
}
.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--adw-space-4);
}
.card-header h2 {
  margin: 0;
  font-size: 17px;
}
.card-header p {
  margin: 5px 0 0;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.usage-chart {
  height: 270px;
  margin-top: var(--adw-space-3);
}
.source-content {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--adw-space-6);
  min-height: 225px;
}
.source-chart {
  width: 170px;
  height: 170px;
  flex: 0 0 auto;
}
.source-legend {
  display: grid;
  gap: 12px;
  min-width: 150px;
}
.source-legend > div {
  display: grid;
  grid-template-columns: 9px 1fr auto;
  align-items: center;
  gap: 7px;
  font-size: 12px;
}
.source-legend i {
  width: 9px;
  height: 9px;
  border-radius: 2px;
}
.source-legend small {
  grid-column: 2 / 4;
  color: var(--adw-text-tertiary);
}
.tool-usage-panel {
  min-height: 420px;
  padding: var(--adw-space-2) 0 var(--adw-space-4);
}
.tool-usage-panel__summary {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: var(--adw-space-4);
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.tool-usage-panel__summary strong {
  color: var(--adw-text-primary);
  font-size: 24px;
  letter-spacing: -0.02em;
}
.tool-usage-panel__hint {
  margin: -8px 0 var(--adw-space-4);
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.budget-summary {
  display: grid;
  gap: 7px;
  padding-top: var(--adw-space-4);
  border-top: 1px solid var(--adw-border-color-light);
}
.budget-summary > div {
  display: flex;
  justify-content: space-between;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.budget-summary strong {
  color: var(--adw-text-primary);
}
.budget-summary small {
  color: var(--adw-text-tertiary);
}
.records-card {
  padding-top: 7px;
}
.records-pagination {
  justify-content: flex-end;
  margin-top: var(--adw-space-4);
}
.task-cell {
  display: grid;
  gap: 3px;
}
.task-cell span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.panel-empty {
  display: grid;
  min-height: 150px;
  place-content: center;
  color: var(--adw-text-tertiary);
  font-size: 13px;
}
.audit-drawer {
  display: grid;
  gap: var(--adw-space-5);
}
.audit-drawer dl {
  display: grid;
  gap: 0;
  margin: 0;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
}
.audit-drawer dl > div {
  display: grid;
  grid-template-columns: 115px 1fr;
  gap: 10px;
  padding: 11px 13px;
  border-bottom: 1px solid var(--adw-border-color-light);
}
.audit-drawer dl > div:last-child {
  border-bottom: 0;
}
.audit-drawer dt {
  color: var(--adw-text-secondary);
}
.audit-drawer dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
}
.audit-drawer h3 {
  margin: 0 0 10px;
  font-size: 15px;
}
.tool-call-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 0;
  border-bottom: 1px solid var(--adw-border-color-light);
}
.tool-call-row > div {
  display: grid;
  gap: 3px;
}
.tool-call-row span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.drawer-empty {
  padding: 20px 0;
  color: var(--adw-text-tertiary);
  text-align: center;
}
.detail-link {
  width: 100%;
}
@media (max-width: 1200px) {
  .filter-bar {
    grid-template-columns: repeat(3, 1fr);
  }
  .filter-bar__date {
    grid-column: span 2;
  }
  .metric-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .insight-grid {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 720px) {
  .filter-bar,
  .metric-grid {
    grid-template-columns: 1fr;
  }
  .filter-bar__date {
    grid-column: auto;
  }
  .source-content {
    flex-direction: column;
  }
  .card-header {
    flex-direction: column;
  }
}
</style>
