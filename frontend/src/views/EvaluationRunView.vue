<template>
  <section class="run-page" aria-label="评估运行">
    <header class="run-heading">
      <div>
        <h2>{{ resourceId ? `评估运行 #${resourceId}` : '评估运行列表' }}</h2>
        <p>每次回放保留独立 Attempt；每次评价保留独立 Result，历史记录保持只读。</p>
      </div>
      <div class="run-actions">
        <el-button v-if="resourceId" @click="router.push(basePath)">返回列表</el-button>
        <el-button :disabled="busy" @click="refresh">刷新</el-button>
        <el-button v-if="!resourceId && canRun" type="primary" @click="creating = true"
          >创建评估运行</el-button
        >
        <el-button
          v-if="run?.status === 'PAUSED' && canRun"
          :disabled="busy"
          @click="openAction('resume')"
          >恢复运行</el-button
        >
        <el-button
          v-if="run && canRun && !terminal && !run.cancelRequested"
          type="danger"
          plain
          :disabled="busy"
          @click="cancel"
          >取消运行</el-button
        >
      </div>
    </header>
    <p v-if="!canRead" role="alert">需要当前空间的评估读取权限。</p>
    <template v-else-if="!resourceId">
      <form class="run-filters" @submit.prevent="loadList(1)">
        <label
          >运行状态<select v-model="statusFilter" aria-label="运行状态">
            <option value="">全部状态</option>
            <option v-for="status in RUN_STATUSES" :key="status" :value="status">
              {{ status }}
            </option>
          </select></label
        >
        <label
          >来源范围<select v-model="sourceKind" aria-label="筛选来源类型">
            <option value="">全部来源</option>
            <option value="datasetVersionId">数据集版本</option>
            <option value="singleTestCaseVersionId">测试用例版本</option>
          </select></label
        >
        <label v-if="sourceKind"
          >来源版本 ID<input v-model="sourceId" aria-label="筛选来源版本 ID" placeholder="版本 ID"
        /></label>
        <label
          >创建起始<input
            v-model="createdFrom"
            type="datetime-local"
            aria-label="创建起始" /></label
        ><label
          >创建截止<input v-model="createdTo" type="datetime-local" aria-label="创建截止"
        /></label>
        <el-button native-type="submit">查询</el-button>
      </form>
      <DataState
        :loading="loading"
        :error="error"
        :empty="!list.records.length"
        empty-text="暂无符合条件的评估运行。可调整筛选或创建运行。"
        @retry="loadList(list.pageNum)"
      >
        <div class="run-table-wrap">
          <table class="run-table">
            <thead>
              <tr>
                <th>运行 / 来源</th>
                <th>状态</th>
                <th>用例进度</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in list.records" :key="String(row.id)">
                <td>
                  <RouterLink :to="`${basePath}/${row.id}`">运行 #{{ row.id }}</RouterLink>
                  <p>
                    {{
                      row.datasetVersionId
                        ? `${row.datasetName || '数据集名称不可用'} · v${row.datasetVersionNo ?? '不可用'}`
                        : `${row.testCaseName || '用例名称不可用'} · v${row.testCaseVersionNo ?? '不可用'}`
                    }}
                  </p>
                  <small v-if="row.experimentVariantId"
                    >Experiment Variant #{{ row.experimentVariantId }}</small
                  >
                </td>
                <td>
                  <EvaluationStatusTag domain="run" :status="row.status" />
                  <p v-if="row.pauseReason">{{ pauseExplanation(row.pauseReason) }}</p>
                  <small v-if="row.cancelRequested">已请求取消</small>
                </td>
                <td>
                  {{ row.completedCaseCount ?? '不可用' }} / {{ row.caseCount }} 已完成
                  <p>异常 {{ row.errorCaseCount ?? '不可用' }}</p>
                </td>
                <td>
                  <p>创建 {{ row.createdAt || '不可用' }}</p>
                  <p>开始 {{ row.startedAt || '尚未开始' }}</p>
                  <p>
                    结束
                    {{
                      activeRun(row.status) || row.status === 'PAUSED'
                        ? '尚未结束'
                        : row.finishedAt || '未提供'
                    }}
                  </p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <el-pagination
          v-if="list.total > PAGE_SIZE"
          layout="prev, pager, next"
          :total="list.total"
          :page-size="PAGE_SIZE"
          :current-page="list.pageNum"
          @current-change="loadList"
        />
      </DataState>
    </template>
    <DataState v-else :loading="loading" :error="error" @retry="loadDetail">
      <template v-if="run">
        <p v-if="pollError" role="alert">
          刷新失败：{{ pollError }}。保留上次读取结果，将自动重试；也可手动刷新。
        </p>
        <div class="run-summary">
          <article class="run-progress">
            <EvaluationStatusTag domain="run" :status="run.status" /><strong
              >{{ finishedCount }} / {{ run.caseCount }} 用例结束</strong
            ><progress
              :value="finishedCount"
              :max="Math.max(run.caseCount, 1)"
              aria-label="用例结束进度"
            />
            <p>已完成 {{ completedCount }} · 异常 {{ errorCount }} · 已取消 {{ canceledCount }}</p>
          </article>
          <article>
            <strong>冻结来源</strong>
            <p>
              {{ run.datasetVersionId ? 'DatasetVersion' : 'TestCaseVersion' }} #{{
                run.datasetVersionId || run.singleTestCaseVersionId || '不可用'
              }}
            </p>
            <p>
              开始 {{ run.startedAt || '尚未开始' }}<br />结束
              {{ terminal ? run.finishedAt || '未提供' : '尚未结束' }}
            </p>
          </article>
          <article v-if="run.status === 'PAUSED' || run.cancelRequested" class="run-warning">
            <strong>{{ run.cancelRequested ? '取消请求已记录' : '运行已暂停' }}</strong>
            <p>
              {{
                run.status === 'PAUSED'
                  ? pauseExplanation(run.pauseReason)
                  : '正在等待在途执行收敛；已完成的执行不会撤销。'
              }}
            </p>
            <p v-if="run.reconciliationFailureCount !== null">
              对账失败 {{ run.reconciliationFailureCount }} 次
            </p>
          </article>
        </div>
        <p v-if="actionError" role="alert">{{ actionError }}</p>
        <div class="run-detail-layout">
          <section class="run-cases" aria-label="用例与重试历史">
            <header>
              <h3>用例与重试历史</h3>
              <label><input v-model="onlyErrors" type="checkbox" /> 仅看异常</label>
            </header>
            <div class="run-case-list">
              <button
                v-for="item in visibleCases"
                :key="String(item.id)"
                class="run-case"
                :aria-pressed="String(item.id) === selectedCaseId"
                @click="chooseCase(item)"
              >
                <span
                  ><strong>用例版本 #{{ item.testCaseVersionId || '不可用' }}</strong
                  ><small>CaseRun #{{ item.id }} · Attempt {{ item.attemptNo }}</small></span
                ><EvaluationStatusTag domain="attempt" :status="item.status" />
              </button>
              <p v-if="!visibleCases.length">没有符合筛选的用例。</p>
            </div>
            <el-pagination
              v-if="filteredCases.length > PAGE_SIZE"
              small
              layout="prev, pager, next"
              :total="filteredCases.length"
              :page-size="PAGE_SIZE"
              :current-page="casePage"
              @current-change="casePage = $event"
            />
          </section>
          <EvaluationCasePanel
            v-if="selectedCase"
            :key="`${spaceId}/${run.id}/${selectedCase.id}`"
            :space-id="spaceId"
            :run-id="String(run.id)"
            :case-run="selectedCase"
            :revision="revision"
            :can-run="canRun"
            :can-read-task="canReadTask"
            :cancel-requested="!!run.cancelRequested"
            :busy="busy"
            :initial-attempt-id="queryId(route.query.attemptId)"
            :initial-result-id="queryId(route.query.resultId)"
            @retry-replay="openAction('replay')"
            @retry-evaluation="openAction('evaluation', $event)"
            @feedback-saved="loadDetail(true)"
          />
          <p v-else>请选择一个用例查看 Attempt 和评估结果。</p>
        </div>
      </template>
    </DataState>
    <EvaluationRunCreate
      v-if="creating && canRun"
      :space-id="spaceId"
      :can-run="canRun"
      @close="creating = false"
      @created="created"
    />
    <el-dialog v-model="actionDialog" :title="actionTitle" width="560px">
      <p>{{ actionDescription }}</p>
      <label class="run-ttl"
        >执行授权有效期（秒）<input
          v-model.number="ttl"
          aria-label="重试或恢复授权有效期（秒）"
          type="number"
          :min="WORKER_TTL_MIN"
          :max="WORKER_TTL_MAX"
          step="1"
      /></label>
      <p>允许 300～86400 秒。历史记录保持不变，新的尝试会单独记录。</p>
      <template #footer
        ><el-button @click="actionDialog = false">取消</el-button
        ><el-button
          type="primary"
          :disabled="!validWorkerTtl(ttl) || busy"
          @click="performAction"
          >{{ actionTitle }}</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElButton, ElDialog, ElMessage, ElMessageBox, ElPagination } from 'element-plus'
import DataState from '@/shared/components/DataState.vue'
import { normalizeApiError } from '@/api/errors'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import EvaluationStatusTag from '@/features/evaluation/components/EvaluationStatusTag.vue'
import EvaluationRunCreate from '@/features/evaluation/components/EvaluationRunCreate.vue'
import EvaluationCasePanel from '@/features/evaluation/components/EvaluationCasePanel.vue'
import * as api from '@/features/evaluation/api/evaluation-api'
import type {
  EvaluationRun,
  EvaluationRunSummary,
  EvaluationCaseRun,
  EvaluationPage,
  EvaluationRunStatus,
} from '@/features/evaluation/types'
import {
  activeRun,
  activeAttempt,
  RUN_STATUSES,
  RUN_POLL_INTERVAL,
  RUN_POLL_MAX_DELAY,
  WORKER_TTL_DEFAULT,
  WORKER_TTL_MIN,
  WORKER_TTL_MAX,
  validWorkerTtl,
  pauseExplanation,
} from '@/features/evaluation/run'
const props = defineProps<{ resourceId?: string }>()
const route = useRoute(),
  router = useRouter(),
  workspace = useWorkspaceStore()
const PAGE_SIZE = 10
const spaceId = computed(() => String(route.params.spaceId))
const scoped = computed(() => String(workspace.currentSpaceId) === spaceId.value)
const canRead = computed(
  () => scoped.value && workspace.hasPermission(SPACE_PERMISSIONS.EVALUATION_READ),
)
const canRun = computed(
  () => canRead.value && workspace.hasPermission(SPACE_PERMISSIONS.EVALUATION_RUN),
)
const canReadTask = computed(
  () => canRead.value && workspace.hasPermission(SPACE_PERMISSIONS.TASK_READ),
)
const basePath = computed(() => `/spaces/${spaceId.value}/evaluation/runs`)
const list = ref<EvaluationPage<EvaluationRunSummary>>({
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: PAGE_SIZE,
})
const run = ref<EvaluationRun | null>(null),
  loading = ref(false),
  error = ref(''),
  pollError = ref('')
const busy = ref(false),
  actionError = ref(''),
  creating = ref(false),
  revision = ref(0)
const statusFilter = ref<EvaluationRunStatus | ''>(''),
  sourceKind = ref<'' | 'datasetVersionId' | 'singleTestCaseVersionId'>(''),
  sourceId = ref(''),
  createdFrom = ref(''),
  createdTo = ref('')
const selectedCaseId = ref(''),
  onlyErrors = ref(false),
  casePage = ref(1)
const selectedCase = computed(() =>
  run.value?.cases.find((item) => String(item.id) === selectedCaseId.value),
)
const filteredCases = computed(
  () =>
    run.value?.cases.filter(
      (item) => !onlyErrors.value || ['REPLAY_FAILED', 'EVALUATOR_FAILED'].includes(item.status),
    ) || [],
)
const visibleCases = computed(() =>
  filteredCases.value.slice((casePage.value - 1) * PAGE_SIZE, casePage.value * PAGE_SIZE),
)
const completedCount = computed(
  () => run.value?.cases.filter((item) => item.status === 'COMPLETED').length || 0,
)
const errorCount = computed(
  () =>
    run.value?.cases.filter((item) => ['REPLAY_FAILED', 'EVALUATOR_FAILED'].includes(item.status))
      .length || 0,
)
const canceledCount = computed(
  () => run.value?.cases.filter((item) => item.status === 'CANCELED').length || 0,
)
const finishedCount = computed(() => completedCount.value + errorCount.value + canceledCount.value)
const terminal = computed(
  () => !!run.value && !activeRun(run.value.status) && run.value.status !== 'PAUSED',
)
const actionDialog = ref(false),
  action = ref<'resume' | 'replay' | 'evaluation'>('resume'),
  ttl = ref(WORKER_TTL_DEFAULT),
  targetAttemptId = ref(''),
  targetCaseId = ref('')
const actionTitle = computed(
  () =>
    ({ resume: '恢复运行', replay: '重放模型与工具', evaluation: '仅重试评估器' })[action.value],
)
const actionDescription = computed(
  () =>
    ({
      resume: '恢复暂停运行，可能下发尚未完成的隔离回放任务并消耗 Token。',
      replay:
        '为当前 CaseRun 创建新的 Attempt 和 Task，重新调用模型与工具并消耗 Token，产生新的隔离候选产物。',
      evaluation: '复用当前 Attempt 的执行证据，只追加 EvaluationResult，不重新调用模型与工具。',
    })[action.value],
)
let readRequest: AbortController | undefined,
  mutationRequest: AbortController | undefined,
  timer: ReturnType<typeof setTimeout> | undefined,
  failures = 0,
  generation = 0
function queryId(value: unknown): string | undefined {
  return typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value) ? value : undefined
}
function ensureRun(value: EvaluationRun) {
  if (String(value.spaceId) !== spaceId.value || String(value.id) !== props.resourceId)
    throw new Error('运行身份或空间归属不一致')
}
function stopTimer() {
  if (timer) clearTimeout(timer)
  timer = undefined
}
function schedule() {
  stopTimer()
  if (run.value && activeRun(run.value.status) && canRead.value && !document.hidden && !busy.value)
    timer = setTimeout(
      () => void loadDetail(true),
      Math.min(RUN_POLL_MAX_DELAY, RUN_POLL_INTERVAL * 2 ** failures),
    )
}
async function loadList(pageNum: number) {
  if (!canRead.value || props.resourceId) return
  readRequest?.abort()
  const pending = new AbortController()
  readRequest = pending
  loading.value = true
  error.value = ''
  list.value.records = []
  try {
    if (sourceKind.value && !queryId(sourceId.value)) throw new Error('请输入有效来源版本 ID')
    if (createdFrom.value && createdTo.value && createdFrom.value > createdTo.value)
      throw new Error('创建起始不能晚于截止')
    const value = await api.searchEvaluationRuns(
      {
        spaceId: spaceId.value,
        status: statusFilter.value || undefined,
        ...(sourceKind.value ? { [sourceKind.value]: sourceId.value } : {}),
        createdFrom: createdFrom.value || undefined,
        createdTo: createdTo.value || undefined,
        pageNum,
        pageSize: PAGE_SIZE,
      },
      pending.signal,
    )
    if (pending.signal.aborted) return
    if (value.records.some((item) => String(item.spaceId) !== spaceId.value))
      throw new Error('运行空间归属不一致')
    list.value = value
  } catch (e) {
    if (!pending.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!pending.signal.aborted) loading.value = false
  }
}
async function loadDetail(quiet = false) {
  if (!canRead.value || !props.resourceId || busy.value) return
  stopTimer()
  readRequest?.abort()
  const pending = new AbortController()
  readRequest = pending
  if (!quiet) {
    loading.value = true
    error.value = ''
  }
  try {
    const value = await api.getEvaluationRun(props.resourceId, pending.signal)
    if (pending.signal.aborted) return
    ensureRun(value)
    run.value = value
    failures = 0
    pollError.value = ''
    error.value = ''
    revision.value++
    const requested = queryId(route.query.caseRunId)
    if (requested && !value.cases.some((item) => String(item.id) === requested))
      throw new Error('用例不属于当前运行')
    if (!value.cases.some((item) => String(item.id) === selectedCaseId.value))
      selectedCaseId.value = requested || String(value.cases[0]?.id || '')
  } catch (e) {
    if (!pending.signal.aborted) {
      const fault = normalizeApiError(e)
      failures = Math.min(failures + 1, 3)
      if (quiet && run.value && fault.code !== 40300 && fault.code !== 40400)
        pollError.value = fault.message
      else {
        error.value = fault.message
        run.value = null
      }
    }
  } finally {
    if (!pending.signal.aborted) {
      loading.value = false
      schedule()
    }
  }
}
function refresh() {
  if (busy.value) return
  void (props.resourceId ? loadDetail() : loadList(list.value.pageNum))
}
function chooseCase(item: EvaluationCaseRun) {
  selectedCaseId.value = String(item.id)
  void router.replace({
    query: {
      ...route.query,
      caseRunId: String(item.id),
      attemptId: undefined,
      resultId: undefined,
    },
  })
}
async function created(value: EvaluationRun) {
  creating.value = false
  ElMessage.success('评估运行已创建')
  await router.push(`${basePath.value}/${value.id}`)
}
function openAction(kind: 'resume' | 'replay' | 'evaluation', attemptId?: string) {
  if (!canRun.value || busy.value || !run.value) return
  action.value = kind
  targetCaseId.value = String(selectedCase.value?.id || '')
  targetAttemptId.value = attemptId || ''
  ttl.value = WORKER_TTL_DEFAULT
  actionDialog.value = true
}
function validAction() {
  if (!canRun.value || !run.value) return false
  if (action.value === 'resume') return run.value.status === 'PAUSED'
  const current = run.value.cases.find((item) => String(item.id) === targetCaseId.value)
  if (!current) return false
  return action.value === 'replay'
    ? !run.value.cancelRequested && !activeAttempt(current.status)
    : String(current.currentAttemptId) === targetAttemptId.value &&
        ['COMPLETED', 'EVALUATOR_FAILED'].includes(current.status) &&
        !!current.executionTaskId
}
function beginMutation() {
  stopTimer()
  readRequest?.abort()
  mutationRequest?.abort()
  const pending = new AbortController()
  mutationRequest = pending
  busy.value = true
  actionError.value = ''
  return pending
}
async function performAction() {
  if (!validAction() || !validWorkerTtl(ttl.value) || busy.value) return
  const pending = beginMutation()
  actionDialog.value = false
  try {
    const value =
      action.value === 'resume'
        ? await api.resumeEvaluationRun(
            props.resourceId!,
            { workerCapabilityTtlSeconds: ttl.value },
            pending.signal,
          )
        : action.value === 'replay'
          ? await api.retryCaseReplay(
              targetCaseId.value,
              { workerCapabilityTtlSeconds: ttl.value },
              pending.signal,
            )
          : await api.retryCaseEvaluation(
              targetAttemptId.value,
              { workerCapabilityTtlSeconds: ttl.value },
              pending.signal,
            )
    if (pending.signal.aborted) return
    ensureRun(value)
    run.value = value
    revision.value++
    ElMessage.success(`${actionTitle.value}请求已处理`)
  } catch (e) {
    if (!pending.signal.aborted) actionError.value = normalizeApiError(e).message
  } finally {
    if (!pending.signal.aborted) {
      busy.value = false
      void loadDetail(true)
    }
  }
}
async function cancel() {
  if (!canRun.value || !run.value || terminal.value || run.value.cancelRequested || busy.value)
    return
  const currentGeneration = generation
  try {
    await ElMessageBox.confirm(
      '请求取消在途执行，已完成的模型调用和产物不会撤销。取消可能需要等待对账收敛。',
      '取消评估运行',
      { confirmButtonText: '请求取消', cancelButtonText: '继续运行', type: 'warning' },
    )
  } catch {
    return
  }
  if (currentGeneration !== generation || !canRun.value || !run.value) return
  if (terminal.value || run.value.cancelRequested) {
    ElMessage.info(terminal.value ? '运行已结束，无需取消' : '取消请求已记录')
    return
  }
  const pending = beginMutation()
  try {
    const value = await api.cancelEvaluationRun(props.resourceId!, pending.signal)
    if (pending.signal.aborted) return
    ensureRun(value)
    run.value = value
    revision.value++
    ElMessage.success('取消请求已记录')
  } catch (e) {
    if (!pending.signal.aborted) actionError.value = normalizeApiError(e).message
  } finally {
    if (!pending.signal.aborted) {
      busy.value = false
      void loadDetail(true)
    }
  }
}
function visibilityChanged() {
  stopTimer()
  if (document.hidden) {
    readRequest?.abort()
    loading.value = false
  } else if (!run.value || activeRun(run.value.status)) void loadDetail(!!run.value)
}
watch(
  () => [props.resourceId, spaceId.value, canRead.value],
  () => {
    generation++
    stopTimer()
    readRequest?.abort()
    mutationRequest?.abort()
    run.value = null
    busy.value = false
    creating.value = false
    actionDialog.value = false
    actionError.value = ''
    pollError.value = ''
    selectedCaseId.value = ''
    onlyErrors.value = false
    casePage.value = 1
    statusFilter.value = ''
    sourceKind.value = ''
    sourceId.value = ''
    createdFrom.value = ''
    createdTo.value = ''
    failures = 0
    void (props.resourceId ? loadDetail() : loadList(1))
  },
  { immediate: true },
)
watch(canRun, () => {
  actionDialog.value = false
  if (!canRun.value) {
    mutationRequest?.abort()
    busy.value = false
    creating.value = false
    schedule()
  }
})
watch(onlyErrors, () => {
  casePage.value = 1
})
watch(
  () => route.query.caseRunId,
  () => {
    const id = queryId(route.query.caseRunId)
    if (id && run.value?.cases.some((item) => String(item.id) === id)) selectedCaseId.value = id
  },
)
onMounted(() => document.addEventListener('visibilitychange', visibilityChanged))
onBeforeUnmount(() => {
  stopTimer()
  readRequest?.abort()
  mutationRequest?.abort()
  document.removeEventListener('visibilitychange', visibilityChanged)
})
</script>
<style scoped>
.run-page {
  display: grid;
  gap: var(--adw-space-5);
}
.run-heading,
.run-actions,
.run-cases header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--adw-space-3);
  flex-wrap: wrap;
}
h2,
h3 {
  margin: 0;
}
h2 {
  font-size: var(--adw-font-size-subtitle);
}
p,
small {
  color: var(--adw-text-secondary);
  line-height: 1.7;
  overflow-wrap: anywhere;
}
p {
  margin: 6px 0 0;
}
small {
  display: block;
}
.run-actions .el-button {
  margin-left: 0;
}
.run-filters {
  display: flex;
  align-items: end;
  gap: var(--adw-space-3);
  flex-wrap: wrap;
}
label {
  display: grid;
  gap: 6px;
  font-size: var(--adw-font-size-caption);
}
input:not([type='checkbox']),
select {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  padding: 8px;
  color: var(--adw-text-primary);
  background: var(--adw-surface);
}
.run-table-wrap {
  overflow-x: auto;
}
.run-table {
  width: 100%;
  border-collapse: collapse;
}
th,
td {
  text-align: left;
  border-bottom: 1px solid var(--adw-border-color);
  padding: var(--adw-space-3);
  vertical-align: top;
}
th {
  color: var(--adw-text-secondary);
  font-size: var(--adw-font-size-caption);
}
.run-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--adw-space-4);
}
.run-summary article {
  padding: var(--adw-space-4);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
}
.run-progress {
  border-left: 3px solid var(--adw-color-primary) !important;
  display: grid;
  gap: var(--adw-space-2);
}
progress {
  width: 100%;
  accent-color: var(--adw-color-primary);
}
.run-warning {
  background: var(--adw-color-warning-soft);
}
.run-detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--adw-space-4);
  align-items: start;
}
.run-cases {
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  min-width: 0;
}
.run-cases header {
  padding: var(--adw-space-4);
  border-bottom: 1px solid var(--adw-border-color);
}
.run-cases label {
  display: block;
}
.run-case-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
}
.run-case {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-2);
  width: 100%;
  text-align: left;
  padding: var(--adw-space-4);
  border: 0;
  border-bottom: 1px solid var(--adw-border-color);
  background: transparent;
  cursor: pointer;
  color: var(--adw-text-primary);
  overflow-wrap: anywhere;
}
.run-case[aria-pressed='true'] {
  background: var(--adw-color-primary-soft);
  border-left: 3px solid var(--adw-color-primary);
}
.run-case strong {
  font-size: var(--adw-font-size-body);
}
.run-ttl {
  margin-top: var(--adw-space-4);
}
[role='alert'] {
  color: var(--adw-color-danger);
}
button:focus-visible,
input:focus-visible,
select:focus-visible {
  outline: 2px solid var(--adw-color-primary);
  outline-offset: 2px;
}
@media (max-width: 1100px) {
  .run-detail-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .run-case-list {
    grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  }
}
</style>
