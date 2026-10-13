<template>
  <section class="online-experiment" aria-label="受控线上实验">
    <header class="header">
      <div>
        <h2>{{ experiment?.name || (resourceId ? `线上实验 #${resourceId}` : '线上实验') }}</h2>
        <p>同一文档固定在一组，首个接受任务作为主分析；实际任务不会重复运行另一组。</p>
      </div>
      <div class="actions">
        <el-button v-if="resourceId" @click="router.push(basePath)">返回列表</el-button
        ><el-button :disabled="loading" @click="refresh">刷新</el-button
        ><el-button v-if="!resourceId && canCreate" type="primary" @click="creating = true"
          >创建草案</el-button
        >
      </div>
    </header>
    <p v-if="!canRead" role="alert">当前空间缺少实验读取权限。</p>
    <p v-if="resourceId && canReview">
      <RouterLink :to="`${basePath}/${resourceId}/review`">进入原始产物评价</RouterLink
      >。本页已显示组别，进入后应声明非盲态；独立评审者应直接使用隐藏分组评审链接。
    </p>
    <p v-if="pollError" role="status">刷新失败：{{ pollError }}。保留上次结果，正在退避重试。</p>
    <template v-if="!resourceId && canRead">
      <form class="filters" @submit.prevent="loadList(1)">
        <label
          >状态<select v-model="statusFilter" aria-label="线上实验状态">
            <option value="">全部状态</option>
            <option v-for="status in ONLINE_STATUSES" :key="status" :value="status">
              {{ onlineStatus(status) }}
            </option>
          </select></label
        ><el-button native-type="submit">查询</el-button>
      </form>
      <DataState
        :loading="loading"
        :error="error"
        :empty="!list.records.length"
        empty-text="暂无线上实验。OWNER 可创建冻结草案，再进行预检。"
        @retry="refresh"
      >
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>实验 / Agent</th>
                <th>状态</th>
                <th>文档 / Task</th>
                <th>Token / 未决</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in list.records" :key="item.id">
                <td>
                  <RouterLink :to="`${basePath}/${item.id}`">{{ item.name }}</RouterLink
                  ><small>Agent #{{ item.agentId }}</small>
                </td>
                <td>
                  {{ onlineStatus(item.status)
                  }}<small v-for="code in item.reasonCodes" :key="code">{{
                    onlineIssue(code)
                  }}</small>
                </td>
                <td>
                  范围 {{ onlineFact(item.documentCount) }} / 参与
                  {{ onlineFact(item.participatingDocumentCount)
                  }}<small>Task {{ onlineFact(item.assignedTaskCount) }}</small>
                </td>
                <td>
                  授权 {{ onlineFact(item.authorizedTokenBudget)
                  }}<small
                    >实际 {{ onlineFact(item.consumedTokens) }} / 未决
                    {{ onlineFact(item.unknownTaskCount) }}</small
                  >
                </td>
                <td>
                  {{ item.createdAt || '创建时间未知'
                  }}<small>对账 {{ item.lastReconciledAt || '尚未确认' }}</small>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <el-pagination
          :current-page="list.pageNum"
          :page-size="10"
          :total="list.total"
          layout="prev, pager, next"
          @current-change="loadList"
        />
      </DataState>
    </template>
    <DataState
      v-else-if="resourceId && canRead"
      :loading="loading && !experiment"
      :error="error"
      @retry="refresh"
    >
      <template v-if="experiment">
        <p v-if="experiment.manifestSchemaVersion !== 2" role="alert">
          历史协议仅供查看，不能启动或操作。
        </p>
        <section aria-label="线上运行事实">
          <h3>{{ onlineStatus(experiment.status) }}</h3>
          <p v-for="code in experiment.reasonCodes" :key="code" class="warning">
            {{ onlineIssue(code) }}
          </p>
          <div class="table-wrap">
            <table>
              <tbody>
                <tr>
                  <th>范围与分配</th>
                  <td>
                    范围 {{ onlineFact(experiment.documentCount) }} 文档 / 参与
                    {{ onlineFact(experiment.participatingDocumentCount) }} 文档 /
                    {{ onlineFact(experiment.assignedTaskCount) }} Task
                  </td>
                </tr>
                <tr>
                  <th>候选概率</th>
                  <td>
                    {{
                      experiment.candidateWeightBps === null
                        ? '未知'
                        : `${experiment.candidateWeightBps / 100}%`
                    }}，实际 Task 比例不必相同
                  </td>
                </tr>
                <tr>
                  <th>Token 账本</th>
                  <td>
                    授权 {{ onlineFact(experiment.authorizedTokenBudget) }} / 实际
                    {{ onlineFact(experiment.consumedTokens) }} / 未结算预留
                    {{ onlineFact(experiment.reservedTokenBudget) }}
                  </td>
                </tr>
                <tr>
                  <th>占槽 / 未决</th>
                  <td>
                    基线 {{ onlineFact(experiment.baselineSlotCount) }} / 候选
                    {{ onlineFact(experiment.candidateSlotCount) }} / 未决任务
                    {{ onlineFact(experiment.unknownTaskCount) }}
                  </td>
                </tr>
                <tr>
                  <th>实际窗口</th>
                  <td>
                    开始 {{ experiment.startedAt || '未开始'
                    }}<small
                      >分配截止 {{ experiment.assignmentDeadline || '未开始' }} / 观察截止
                      {{ experiment.observationDeadline || '未开始' }}</small
                    >
                  </td>
                </tr>
                <tr>
                  <th>对账</th>
                  <td>
                    {{ experiment.lastReconciledAt || '尚未确认' }}。已发取消请求不代表远端已停止。
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <p>{{ ONLINE_FALLBACK_NOTICE }}</p>
          <OnlineExperimentActions
            v-if="isOwner"
            :experiment="experiment"
            :preflight="preflight"
            :allowed="canOperate"
            :can-terminate="canTerminate"
            @changed="changed"
          />
        </section>
        <section v-if="isOwner" aria-label="启动前预检">
          <h3>启动前预检</h3>
          <el-button
            :disabled="checking || loading || experiment.manifestSchemaVersion !== 2"
            @click="check"
            >{{ checking ? '正在预检' : '重新预检' }}</el-button
          >
          <p v-if="preflightError" role="alert">{{ preflightError }}</p>
          <template v-if="preflight">
            <p>
              {{
                preflight.startable ? '预检允许启动，仍需确认副作用和授权' : '当前不能启动'
              }}。有效至 {{ preflight.expiresAt }}。
            </p>
            <p>
              基线 {{ preflight.baselineDocumentCount }} 文档 / 候选
              {{ preflight.candidateDocumentCount }} 文档；最大计划预留
              {{ preflight.plannedUpperTokenBudget }} Token；历史估算
              {{ preflight.historicalEstimate || '不可用' }}。
            </p>
            <p>
              分配诊断：{{
                preflight.srm.status === 'NOT_ENOUGH_UNITS'
                  ? '样本不足，SRM 未启用；协议核查不等于统计诊断通过'
                  : preflight.srm.status
              }}<span v-if="preflight.srm.pValue !== null"
                >（分配诊断 p={{ preflight.srm.pValue }}）</span
              >
            </p>
            <ul>
              <li v-for="(issue, index) in preflight.issues" :key="index">
                {{ issue.severity }}：{{ onlineIssue(issue.code)
                }}<span v-if="issue.documentId">，文档 #{{ issue.documentId }}</span
                ><span v-if="issue.ruleKey">，规则 {{ issue.ruleKey }}</span>
              </li>
            </ul>
          </template>
        </section>
        <section aria-label="线上分配观察">
          <h3>已接受分配</h3>
          <p>保留未落库、失败、取消和未知项。下钻 Task 时重新检查权限。</p>
          <p v-if="assignmentError" role="alert">{{ assignmentError }}</p>
          <div class="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>文档 / Task</th>
                  <th>固定组 / 顺序</th>
                  <th>任务 / 派发 / 槽</th>
                  <th>取消 / 结算</th>
                  <th>Token / 未决</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in assignments.records" :key="item.id">
                  <td>
                    文档 #{{ item.documentId
                    }}<small
                      ><RouterLink
                        v-if="canTaskRead"
                        :to="`/spaces/${spaceId}/tasks/${item.taskId}`"
                        >Task #{{ item.taskId }}</RouterLink
                      ><span v-else>Task #{{ item.taskId }}</span></small
                    >
                  </td>
                  <td>
                    {{ item.variant }}<small>{{ onlineFact(item.acceptedSequence) }}</small>
                  </td>
                  <td>
                    {{ onlineFact(item.taskConfirmationStatus)
                    }}<small
                      >{{ onlineFact(item.dispatchStatus) }} /
                      {{ onlineFact(item.slotStatus) }}</small
                    >
                  </td>
                  <td>
                    {{ onlineFact(item.cancelStatus)
                    }}<small>{{ onlineFact(item.settlementStatus) }}</small>
                  </td>
                  <td>
                    预留 {{ onlineFact(item.reservedTokenBudget) }} / 实际
                    {{ onlineFact(item.consumedTokens)
                    }}<small>{{
                      item.reasonCode ? onlineIssue(item.reasonCode) : item.factStatus || '未知'
                    }}</small>
                  </td>
                </tr>
                <tr v-if="!assignments.records.length">
                  <td colspan="5">暂无已接受分配。</td>
                </tr>
              </tbody>
            </table>
          </div>
          <el-pagination
            :current-page="assignments.pageNum"
            :page-size="10"
            :total="assignments.total"
            layout="prev, pager, next"
            @current-change="loadAssignments"
          />
        </section>
        <OnlineReportPanel
          :experiment="experiment"
          :readable="canRead"
          :manageable="canManageReport"
          :can-recalculate="canRecalculate"
          @decided="refresh"
        />
        <p>{{ ONLINE_DESCRIPTIVE_NOTICE }}</p>
        <OnlinePromptComparison
          :experiment="experiment"
          :allowed="isOwner && workspace.hasPermission(P.AGENT_MANAGE)"
        />
        <details>
          <summary>冻结身份与分析要求</summary>
          <p>实验 #{{ experiment.id }} / Agent #{{ experiment.agentId }}</p>
          <p>清单摘要：{{ experiment.manifestHash || '不可用' }}</p>
          <p>
            基线 {{ experiment.baseline?.hash || '不可用' }} / 候选
            {{ experiment.candidate?.hash || '不可用' }}
          </p>
          <p>非 Prompt 依赖摘要：{{ experiment.dependencyHash || '不可用' }}</p>
          <p>
            最小有价值差：{{ experiment.analysisPlan?.mdeAbsoluteRatio || '不可用' }}；{{
              experiment.analysisPlan?.mdeReason || '理由不可用'
            }}
          </p>
          <p>
            人工质量依据：{{
              experiment.analysisPlan?.humanQualityRequired
                ? '要求每组至少 80% 有效盲态覆盖'
                : '未预注册为必要依据'
            }}
          </p>
        </details>
      </template>
    </DataState>
    <OnlineExperimentCreate
      v-if="creating && canCreate"
      :open="creating"
      :space-id="spaceId"
      :allowed="canCreate"
      @close="creating = false"
      @created="created"
    />
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElButton, ElPagination } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import DataState from '@/shared/components/DataState.vue'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS as P } from '@/shared/constants/permissions'
import OnlineExperimentActions from '@/features/evaluation/online/OnlineExperimentActions.vue'
import OnlineExperimentCreate from '@/features/evaluation/online/OnlineExperimentCreate.vue'
import OnlinePromptComparison from '@/features/evaluation/online/OnlinePromptComparison.vue'
import OnlineReportPanel from '@/features/evaluation/online/OnlineReportPanel.vue'
import {
  getOnlineExperiment,
  getOnlinePreflight,
  searchOnlineAssignments,
  searchOnlineExperiments,
} from '@/features/evaluation/online/api'
import {
  ONLINE_DESCRIPTIVE_NOTICE,
  ONLINE_FALLBACK_NOTICE,
  ONLINE_POLL_INTERVAL,
  ONLINE_POLL_MAX_DELAY,
  ONLINE_STATUSES,
  onlineFact,
  onlineIdentity,
  onlineIssue,
  onlineStatus,
  onlineStateVersion,
  validOnlineId,
} from '@/features/evaluation/online/presentation'
import type {
  OnlineActionResult,
  OnlineAssignment,
  OnlineExperiment,
  OnlinePage,
  OnlinePreflight,
  OnlineSummary,
} from '@/features/evaluation/online/types'

const props = defineProps<{ resourceId?: string }>()
const route = useRoute(),
  router = useRouter(),
  workspace = useWorkspaceStore()
const spaceId = computed(() => String(route.params.spaceId || '')),
  basePath = computed(() => `/spaces/${spaceId.value}/evaluation/online-experiments`)
const scoped = computed(() => String(workspace.currentSpaceId) === spaceId.value)
const canRead = computed(
  () =>
    scoped.value &&
    workspace.hasPermission(P.SPACE_READ) &&
    workspace.hasPermission(P.EVALUATION_READ),
)
const isOwner = computed(
  () => canRead.value && workspace.currentPermissions?.role?.roleKey === 'OWNER',
)
const canCreate = computed(
  () =>
    isOwner.value &&
    workspace.hasPermission(P.EVALUATION_MANAGE) &&
    workspace.hasPermission(P.AGENT_MANAGE) &&
    workspace.hasPermission(P.AGENT_READ) &&
    workspace.hasPermission(P.DOCUMENT_READ),
)
const canOperate = computed(() => isOwner.value && workspace.hasPermission(P.EVALUATION_RUN)),
  canTerminate = computed(() => canOperate.value && workspace.hasPermission(P.TASK_TERMINATE)),
  canTaskRead = computed(() => canRead.value && workspace.hasPermission(P.TASK_READ))
const canReview = computed(
  () =>
    canRead.value &&
    [P.EVALUATION_RUN, P.TASK_READ, P.DOCUMENT_READ].every(workspace.hasPermission),
)
const canManageReport = computed(
  () => isOwner.value && workspace.hasPermission(P.EVALUATION_MANAGE),
)
const canRecalculate = computed(
  () =>
    canManageReport.value &&
    workspace.hasPermission(P.TASK_READ) &&
    workspace.hasPermission(P.USAGE_READ),
)
function emptyPage<T>(): OnlinePage<T> {
  return { records: [], total: 0, pageNum: 1, pageSize: 10 }
}
const list = ref(emptyPage<OnlineSummary>()),
  assignments = ref(emptyPage<OnlineAssignment>()),
  experiment = ref<OnlineExperiment | null>(null),
  preflight = ref<OnlinePreflight | null>(null)
const loading = ref(false),
  checking = ref(false),
  error = ref(''),
  pollError = ref(''),
  preflightError = ref(''),
  assignmentError = ref(''),
  statusFilter = ref(''),
  creating = ref(false)
let read: AbortController | undefined,
  assignmentRead: AbortController | undefined,
  proofRead: AbortController | undefined,
  timer: ReturnType<typeof setTimeout> | undefined,
  failures = 0
function stopTimer() {
  clearTimeout(timer)
}
function clearProof() {
  proofRead?.abort()
  preflight.value = null
  checking.value = false
  preflightError.value = ''
}
function ensureScope(value: { id: string; spaceId: string }) {
  if (value.spaceId !== spaceId.value || !validOnlineId(value.id))
    throw new Error('实验身份或空间不一致')
}
async function loadList(pageNum: number) {
  if (!canRead.value) return
  read?.abort()
  const controller = new AbortController()
  read = controller
  loading.value = true
  error.value = ''
  try {
    const value = await searchOnlineExperiments(
      { spaceId: spaceId.value, status: statusFilter.value || undefined, pageNum, pageSize: 10 },
      controller.signal,
    )
    if (controller.signal.aborted || !canRead.value) return
    value.records.forEach(ensureScope)
    list.value = value
  } catch (e) {
    if (!controller.signal.aborted) {
      error.value = normalizeApiError(e).message
      list.value = emptyPage()
    }
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}
async function loadAssignments(pageNum: number) {
  if (!props.resourceId || !canRead.value) return
  assignmentRead?.abort()
  const controller = new AbortController()
  assignmentRead = controller
  assignmentError.value = ''
  try {
    const value = await searchOnlineAssignments(
      props.resourceId,
      { pageNum, pageSize: 10 },
      controller.signal,
    )
    if (controller.signal.aborted || !canRead.value) return
    if (
      value.records.some(
        (item) => item.spaceId !== spaceId.value || item.experimentId !== props.resourceId,
      )
    )
      throw new Error('分配身份或空间不一致')
    assignments.value = value
  } catch (e) {
    if (!controller.signal.aborted) {
      assignmentError.value = normalizeApiError(e).message
      assignments.value = emptyPage()
    }
  }
}
function schedule() {
  stopTimer()
  if (
    canRead.value &&
    experiment.value &&
    ['ACTIVE', 'PAUSED', 'STOPPING'].includes(experiment.value.status) &&
    !document.hidden
  )
    timer = setTimeout(
      () => void loadDetail(true),
      Math.min(ONLINE_POLL_MAX_DELAY, ONLINE_POLL_INTERVAL * 2 ** failures),
    )
}
async function loadDetail(quiet = false) {
  if (!canRead.value || !props.resourceId) return
  if (!validOnlineId(props.resourceId)) {
    error.value = '实验 ID 无效'
    return
  }
  stopTimer()
  read?.abort()
  const controller = new AbortController()
  read = controller
  if (!quiet) {
    loading.value = true
    error.value = ''
    clearProof()
  }
  try {
    const value = await getOnlineExperiment(props.resourceId, controller.signal)
    if (controller.signal.aborted || !canRead.value) return
    ensureScope(value)
    onlineStateVersion(value.stateVersion)
    if (value.id !== props.resourceId) throw new Error('实验身份不一致')
    if (experiment.value && onlineIdentity(experiment.value) !== onlineIdentity(value)) clearProof()
    experiment.value = value
    error.value = ''
    pollError.value = ''
    failures = 0
    await loadAssignments(assignments.value.pageNum)
  } catch (e) {
    if (!controller.signal.aborted) {
      const fault = normalizeApiError(e)
      failures = Math.min(3, failures + 1)
      if (
        quiet &&
        experiment.value &&
        fault.status !== 403 &&
        fault.status !== 404 &&
        ![40300, 40400].includes(fault.code ?? 0)
      )
        pollError.value = fault.message
      else {
        error.value = fault.message
        experiment.value = null
        assignments.value = emptyPage()
        clearProof()
      }
    }
  } finally {
    if (!controller.signal.aborted) {
      loading.value = false
      schedule()
    }
  }
}
async function check() {
  if (!isOwner.value || !experiment.value || checking.value) return
  clearProof()
  const value = experiment.value,
    identity = onlineIdentity(value),
    controller = new AbortController()
  proofRead = controller
  checking.value = true
  try {
    const proof = await getOnlinePreflight(value.id, controller.signal)
    if (
      controller.signal.aborted ||
      !isOwner.value ||
      !experiment.value ||
      identity !== onlineIdentity(experiment.value)
    )
      return
    if (proof.experimentId !== value.id || proof.manifestHash !== value.manifestHash)
      throw new Error('预检身份变化，请刷新后重试')
    preflight.value = proof
  } catch (e) {
    if (!controller.signal.aborted) preflightError.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) checking.value = false
  }
}
function refresh() {
  void (props.resourceId ? loadDetail() : loadList(list.value.pageNum))
}
function changed(result: OnlineActionResult) {
  if (!experiment.value || result.experimentId !== experiment.value.id) return
  experiment.value = {
    ...experiment.value,
    status: result.status,
    stateVersion: result.stateVersion,
    reasonCodes: result.reasonCode ? [result.reasonCode] : [],
  }
  clearProof()
  void loadDetail()
}
function created(value: OnlineExperiment) {
  creating.value = false
  void router.push(`${basePath.value}/${value.id}`)
}
function visibility() {
  if (document.hidden) {
    stopTimer()
    read?.abort()
    assignmentRead?.abort()
    loading.value = false
  } else if (props.resourceId && canRead.value) void loadDetail(true)
}
document.addEventListener('visibilitychange', visibility)
watch(
  [spaceId, () => props.resourceId, canRead],
  () => {
    stopTimer()
    read?.abort()
    assignmentRead?.abort()
    clearProof()
    list.value = emptyPage()
    assignments.value = emptyPage()
    experiment.value = null
    error.value = ''
    pollError.value = ''
    creating.value = false
    loading.value = false
    failures = 0
    if (canRead.value) refresh()
  },
  { immediate: true },
)
watch(isOwner, (owner) => {
  if (!owner) clearProof()
})
watch(canCreate, (allowed) => {
  if (!allowed) creating.value = false
})
onBeforeUnmount(() => {
  stopTimer()
  read?.abort()
  assignmentRead?.abort()
  proofRead?.abort()
  document.removeEventListener('visibilitychange', visibility)
})
</script>
<style scoped>
.online-experiment {
  display: grid;
  gap: 24px;
}
.header,
.actions,
.filters {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.header h2 {
  margin: 0;
  font-size: var(--adw-font-size-subtitle);
}
p,
small {
  color: var(--adw-text-secondary);
  line-height: 1.6;
}
.table-wrap {
  overflow-x: auto;
}
table {
  width: 100%;
  border-collapse: collapse;
  text-align: left;
}
th,
td {
  padding: 12px;
  border-bottom: 1px solid var(--adw-border-color-light);
  vertical-align: top;
}
th {
  background: var(--adw-surface-muted);
  font-weight: 600;
}
small {
  display: block;
}
details {
  overflow-wrap: anywhere;
}
label {
  display: flex;
  gap: 8px;
  align-items: center;
}
select {
  padding: 6px;
  font: inherit;
}
[role='alert'],
.warning {
  color: var(--adw-color-danger);
}
section + section {
  border-top: 1px solid var(--adw-border-color);
  padding-top: 20px;
}
a {
  color: var(--adw-color-primary);
}
</style>
