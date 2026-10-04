<template>
  <section class="experiment" aria-label="离线实验">
    <header class="experiment-header">
      <div>
        <h2>{{ resourceId ? `离线实验 #${resourceId}` : '离线实验列表' }}</h2>
        <p>在冻结数据集上比较 Baseline 与 Prompt 候选；不修改生产 Agent，不分配线上流量。</p>
      </div>
      <div class="actions">
        <el-button v-if="resourceId" @click="router.push(basePath)">返回列表</el-button
        ><el-button :disabled="busy" @click="refresh">刷新</el-button
        ><el-button v-if="!resourceId && canCreate" type="primary" @click="creating = true"
          >创建离线实验</el-button
        >
      </div>
    </header>
    <p v-if="actionError" role="alert">{{ actionError }}</p>
    <p v-if="pollError" role="status">
      刷新失败：{{ pollError }}。页面保留上次结果，正在退避重试。
    </p>
    <template v-if="!resourceId">
      <form class="filters" @submit.prevent="loadList(1)">
        <label
          >状态<select v-model="statusFilter" aria-label="实验状态">
            <option value="">全部状态</option>
            <option v-for="status in EXPERIMENT_STATUSES" :key="status">{{ status }}</option>
          </select></label
        ><label
          >数据集版本 ID<input
            v-model="datasetFilter"
            aria-label="筛选数据集版本 ID"
            inputmode="numeric" /></label
        ><label
          >创建起始<input
            v-model="createdFrom"
            aria-label="实验创建起始"
            type="datetime-local" /></label
        ><label
          >创建截止<input
            v-model="createdTo"
            aria-label="实验创建截止"
            type="datetime-local" /></label
        ><el-button native-type="submit">查询</el-button>
      </form>
      <DataState
        :loading="loading"
        :error="error"
        :empty="!list.records.length"
        empty-text="尚无离线实验。选择已发布数据集，创建 Prompt 候选后预检。"
        @retry="refresh"
        ><div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>实验 / 冻结来源</th>
                <th>状态 / Variant</th>
                <th>授权 Token / 结论</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in list.records" :key="String(item.id)">
                <td>
                  <RouterLink :to="`${basePath}/${item.id}`">实验 #{{ item.id }}</RouterLink>
                  <p>
                    {{ item.datasetName || '数据集名称不可用' }} · v{{
                      item.datasetVersionNo ?? '未提供'
                    }}
                  </p>
                  <small>版本 #{{ item.datasetVersionId || '未提供' }}</small>
                </td>
                <td>
                  <EvaluationStatusTag domain="experiment" :status="item.status" />
                  <p>{{ item.linkedRunCount ?? '未提供' }} Run / {{ item.variantCount }} Variant</p>
                  <p v-if="item.failureCode">{{ item.failureCode }}</p>
                </td>
                <td>
                  {{ fact(item.authorizedTokenBudget) }}
                  <p>
                    {{ item.decision || '尚无结论'
                    }}<span v-if="item.decisionReportRevision">
                      · Revision {{ item.decisionReportRevision }}</span
                    >
                  </p>
                </td>
                <td>
                  创建 {{ item.createdAt || '未提供' }}
                  <p>开始 {{ item.startedAt || '未开始' }}</p>
                  <p>
                    结束
                    {{
                      activeExperiment(item.status) || ['CREATED', 'PAUSED'].includes(item.status)
                        ? '尚未结束'
                        : item.finishedAt || '未提供'
                    }}
                  </p>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <el-pagination
          :current-page="list.pageNum"
          :page-size="PAGE_SIZE"
          :total="list.total"
          layout="prev, pager, next"
          @current-change="loadList"
      /></DataState>
    </template>
    <DataState v-else :loading="loading && !experiment" :error="error" @retry="refresh">
      <template v-if="experiment">
        <div class="summary-grid">
          <article>
            <EvaluationStatusTag domain="experiment" :status="experiment.status" />
            <p v-if="experiment.failureCode">失败码：{{ experiment.failureCode }}</p>
            <p>开始 {{ experiment.startedAt || '未开始' }}</p>
            <p>
              结束
              {{
                activeExperiment(experiment.status) ||
                ['CREATED', 'PAUSED'].includes(experiment.status)
                  ? '尚未结束'
                  : experiment.finishedAt || '未提供'
              }}
            </p>
          </article>
          <article>
            <strong>冻结身份</strong>
            <p>DatasetVersion #{{ experiment.datasetVersionId || '未提供' }}</p>
            <p>Manifest schema {{ experiment.manifestSchemaVersion ?? '未提供' }}</p>
            <p>Manifest hash：{{ experiment.manifestHash || '未提供' }}</p>
          </article>
          <article>
            <strong>Token 预算与使用</strong>
            <p>授权 {{ fact(experiment.authorizedTokenBudget) }}</p>
            <p>
              实际 {{ fact(experiment.actualTokenUsage) }} · 超额
              {{ fact(experiment.budgetOverrun) }}
            </p>
            <small>授权值是启动门禁，不是在途调用的严格费用封顶。</small>
          </article>
        </div>
        <div class="actions">
          <el-button v-if="startable" :disabled="busy" @click="openStart">{{
            experiment.status === 'PAUSED' ? '预检并恢复实验' : '预检并启动实验'
          }}</el-button
          ><el-button v-if="canRun && cancellable" :disabled="busy" @click="cancel"
            >取消实验</el-button
          ><el-button :disabled="busy" @click="preflightOnly">重新预检</el-button>
        </div>
        <section v-if="preflight" aria-label="实验预检" class="preflight">
          <h3>预检 {{ preflight.eligible ? '通过' : '未通过' }}</h3>
          <p>
            {{ preflight.caseCount }} 用例 × {{ preflight.variantCount }} Variant =
            {{ preflight.plannedTaskCount }} 计划 Task；计划 Token 预算
            {{ preflight.plannedTokenBudget }}。
          </p>
          <p>预检是当前准入结果，启动时后端会再次核验。</p>
          <ul v-if="preflight.issues.length">
            <li v-for="(issue, index) in preflight.issues" :key="index">
              用例 #{{ issue.testCaseVersionId || '未提供' }} · {{ issue.code || '原因未提供' }} ·
              {{ issue.detailReasonCode || '无补充原因码' }}
            </li>
          </ul>
        </section>
        <section aria-label="冻结 Variant" class="variants">
          <h3>冻结 Variant 与关联运行</h3>
          <p>
            首版仅改变 Prompt。选中一项查看其 Run 进度；历史 Run 的恢复与重试通过 Run
            页面正式入口操作。
          </p>
          <div class="variant-list">
            <article v-for="item in experiment.variants" :key="String(item.id)">
              <strong>{{ item.variantKey }} · {{ item.role }}</strong>
              <p>
                Variant #{{ item.id }} · {{ item.type }} · 配置 #{{
                  item.candidateConfigRef || '冻结来源'
                }}
              </p>
              <details>
                <summary>快照与差异身份</summary>
                <p>
                  来源 schema {{ item.sourceSnapshotSchemaVersion ?? '未提供' }} / hash
                  {{ item.sourceSnapshotHash || '未提供' }}
                </p>
                <p>
                  候选 schema {{ item.candidateSnapshotSchemaVersion ?? '未提供' }} / hash
                  {{ item.candidateSnapshotHash || '未提供' }}
                </p>
                <p>非 Prompt 部分 hash：{{ item.snapshotWithoutPromptHash || '未提供' }}</p>
                <p>
                  差异字段：{{
                    item.promptDiffFieldPaths.length ? item.promptDiffFieldPaths.join('、') : '无'
                  }}
                </p>
              </details>
              <template v-if="item.evaluationRunId"
                ><el-button
                  :disabled="busy"
                  :aria-pressed="selectedVariantId === String(item.id)"
                  @click="selectVariant(item)"
                  >查看 {{ item.variantKey }} 进度</el-button
                ><RouterLink :to="`/spaces/${spaceId}/evaluation/runs/${item.evaluationRunId}`"
                  >进入 Run #{{ item.evaluationRunId }}</RouterLink
                ></template
              >
              <p v-else>尚未关联运行。</p>
            </article>
          </div>
          <p v-if="runError" role="alert">运行进度不可用：{{ runError }}</p>
          <article v-if="selectedRun">
            <h4>{{ selectedVariant?.variantKey }} · Run #{{ selectedRun.id }}</h4>
            <EvaluationStatusTag domain="run" :status="selectedRun.status" />
            <p>
              {{ selectedRun.cases.filter((item) => item.status === 'COMPLETED').length }} /
              {{ selectedRun.caseCount }} 用例完成 · 异常
              {{
                selectedRun.cases.filter((item) =>
                  ['EVALUATOR_FAILED', 'REPLAY_FAILED'].includes(item.status),
                ).length
              }}
              · 取消 {{ selectedRun.cases.filter((item) => item.status === 'CANCELED').length }}
            </p>
            <p v-if="selectedRun.pauseReason">暂停原因：{{ selectedRun.pauseReason }}</p>
          </article>
        </section>
        <section aria-label="报告版本与结论" class="reports">
          <header class="experiment-header">
            <h3>报告版本与人工结论</h3>
            <div class="actions">
              <el-button v-if="canManage && completed" :disabled="busy" @click="recalculate"
                >重算报告</el-button
              ><el-button
                v-if="canManage && completed && !experiment.decision && reportSupported"
                :disabled="busy"
                type="primary"
                @click="decisionDialog = true"
                >记录人工结论</el-button
              >
            </div>
          </header>
          <p>重算不会改写历史报告；计算输入不变时可能复用原 revision。</p>
          <p v-if="reportError" role="alert">{{ reportError }}</p>
          <label v-if="revisions.length"
            >报告版本<select
              :value="selectedRevision"
              aria-label="报告版本"
              :disabled="busy"
              @change="chooseRevision"
            >
              <option v-for="item in revisions" :key="item.revision" :value="item.revision">
                Revision {{ item.revision }} · {{ item.generatedAt || '未提供' }}
              </option>
            </select></label
          >
          <p v-else>尚无报告。终态报告若尚未生成，可刷新查看。</p>
          <article v-if="experiment.decision" class="decision">
            <strong
              >人工结论：{{ experiment.decision }} · Revision
              {{ experiment.decisionReportRevision }}</strong
            >
            <p>{{ experiment.decisionReason }}</p>
            <p>
              由 #{{ experiment.decidedBy }} 于 {{ experiment.decidedAt }} 记录。结论不修改生产
              Agent、不分配线上流量。
            </p>
          </article>
          <ExperimentReportPanel
            v-if="report"
            :report="report"
            :space-id="spaceId"
            :can-read-task="canReadTask"
          />
        </section>
      </template>
    </DataState>
    <ExperimentCreate
      v-if="creating && canCreate"
      :space-id="spaceId"
      :allowed="canCreate"
      @close="creating = false"
      @created="created"
    />
    <el-dialog
      v-model="startDialog"
      :title="experiment?.status === 'PAUSED' ? '恢复离线实验' : '启动离线实验'"
      width="640px"
      ><p v-if="preflight">
        {{ preflight.caseCount }} 用例 · {{ preflight.variantCount }} Variant ·
        {{ preflight.plannedTaskCount }} Task · 计划 Token {{ preflight.plannedTokenBudget }}
      </p>
      <p v-if="experiment?.authorizedTokenBudget != null">
        恢复必须使用原授权值 {{ experiment.authorizedTokenBudget }}，请重新输入并确认。
      </p>
      <label
        >授权 Token 预算<input
          v-model.number="authorizedBudget"
          type="number"
          min="1"
          step="1"
          aria-label="授权 Token 预算" /></label
      ><label
        >Worker 授权有效期（秒）<input
          v-model.number="ttl"
          type="number"
          :min="WORKER_TTL_MIN"
          :max="WORKER_TTL_MAX"
          step="1"
          aria-label="实验 Worker 授权有效期"
      /></label>
      <p>
        允许 300～86400 秒。隔离执行会调用模型与工具并消耗
        Token；授权值是启动门禁，不是在途调用的严格费用封顶。
      </p>
      <label class="consent"
        ><input
          v-model="budgetConfirmed"
          type="checkbox"
          aria-label="确认实验预算与隔离执行"
        />我确认上述授权预算与执行范围</label
      ><template #footer
        ><el-button @click="startDialog = false">取消</el-button
        ><el-button type="primary" :disabled="!validStart || busy" @click="start"
          >确认并{{ experiment?.status === 'PAUSED' ? '恢复' : '启动' }}</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="decisionDialog" title="记录指定报告的人工结论" width="600px"
      ><p>
        固定引用 Revision {{ selectedRevision }}。结论不可覆盖，不修改生产 Agent、不分配线上流量。
      </p>
      <label
        >结论<select v-model="decisionValue" aria-label="实验人工结论">
          <option value="INSUFFICIENT_EVIDENCE">证据不足</option>
          <option value="ACCEPTED">接受候选</option>
          <option value="REJECTED">拒绝候选</option>
        </select></label
      ><label
        >判断依据<textarea
          v-model="decisionReason"
          aria-label="实验结论依据"
          rows="4"
          maxlength="1000"
        /></label
      ><template #footer
        ><el-button @click="decisionDialog = false">取消</el-button
        ><el-button type="primary" :disabled="busy || !decisionReason.trim()" @click="decide"
          >记录结论</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElButton, ElDialog, ElMessage, ElMessageBox, ElPagination } from 'element-plus'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { normalizeApiError } from '@/api/errors'
import DataState from '@/shared/components/DataState.vue'
import EvaluationStatusTag from '@/features/evaluation/components/EvaluationStatusTag.vue'
import ExperimentCreate from '@/features/evaluation/components/ExperimentCreate.vue'
import ExperimentReportPanel from '@/features/evaluation/components/ExperimentReportPanel.vue'
import * as api from '@/features/evaluation/api/evaluation-api'
import {
  activeExperiment,
  completedExperiment,
  EXPERIMENT_STATUSES,
  validBudget,
} from '@/features/evaluation/experiment'
import {
  RUN_POLL_INTERVAL,
  RUN_POLL_MAX_DELAY,
  WORKER_TTL_DEFAULT,
  WORKER_TTL_MIN,
  WORKER_TTL_MAX,
  validWorkerTtl,
} from '@/features/evaluation/run'
import type {
  Experiment,
  ExperimentStatus,
  ExperimentSummary,
  ExperimentVariant,
  ExperimentPreflight,
  ExperimentReport,
  ExperimentReportRevision,
  ExperimentDecision,
  EvaluationPage,
  EvaluationRun,
} from '@/features/evaluation/types'
const props = defineProps<{ resourceId?: string }>()
const route = useRoute(),
  router = useRouter(),
  workspace = useWorkspaceStore()
const PAGE_SIZE = 10,
  spaceId = computed(() => String(route.params.spaceId)),
  basePath = computed(() => `/spaces/${spaceId.value}/evaluation/experiments`)
const scoped = computed(() => String(workspace.currentSpaceId) === spaceId.value)
const canRead = computed(
  () => scoped.value && workspace.hasPermission(SPACE_PERMISSIONS.EVALUATION_READ),
)
const canRun = computed(
  () => canRead.value && workspace.hasPermission(SPACE_PERMISSIONS.EVALUATION_RUN),
)
const canManage = computed(
  () => canRead.value && workspace.hasPermission(SPACE_PERMISSIONS.EVALUATION_MANAGE),
)
const canCreate = computed(
  () => canManage.value && workspace.hasPermission(SPACE_PERMISSIONS.AGENT_MANAGE),
)
const canReadTask = computed(
  () => canRead.value && workspace.hasPermission(SPACE_PERMISSIONS.TASK_READ),
)
const list = ref<EvaluationPage<ExperimentSummary>>({
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: PAGE_SIZE,
})
const experiment = ref<Experiment | null>(null),
  loading = ref(false),
  busy = ref(false),
  error = ref(''),
  pollError = ref(''),
  actionError = ref(''),
  creating = ref(false)
const statusFilter = ref<ExperimentStatus | ''>(''),
  datasetFilter = ref(''),
  createdFrom = ref(''),
  createdTo = ref('')
const preflight = ref<ExperimentPreflight>(),
  startDialog = ref(false),
  authorizedBudget = ref<number>(),
  ttl = ref(WORKER_TTL_DEFAULT),
  budgetConfirmed = ref(false)
const selectedVariantId = ref(''),
  selectedRun = ref<EvaluationRun>(),
  runError = ref('')
const revisions = ref<ExperimentReportRevision[]>([]),
  selectedRevision = ref<number>(),
  report = ref<ExperimentReport>(),
  reportError = ref('')
const decisionDialog = ref(false),
  decisionValue = ref<ExperimentDecision>('INSUFFICIENT_EVIDENCE'),
  decisionReason = ref('')
const selectedVariant = computed(() =>
  experiment.value?.variants.find((item) => String(item.id) === selectedVariantId.value),
)
const completed = computed(() => !!experiment.value && completedExperiment(experiment.value.status))
const startable = computed(
  () =>
    canRun.value && !!experiment.value && ['CREATED', 'PAUSED'].includes(experiment.value.status),
)
const cancellable = computed(
  () =>
    !!experiment.value &&
    ['CREATED', 'STARTING', 'RUNNING', 'PAUSED'].includes(experiment.value.status),
)
const reportSupported = computed(
  () =>
    !!report.value?.compatible &&
    report.value.compatibilityCode === 'SUPPORTED' &&
    report.value.schemaVersion === 1 &&
    !!report.value.report,
)
const validStart = computed(
  () =>
    startable.value &&
    !!preflight.value?.eligible &&
    budgetConfirmed.value &&
    validWorkerTtl(ttl.value) &&
    validBudget(authorizedBudget.value, preflight.value.plannedTokenBudget) &&
    (experiment.value?.authorizedTokenBudget == null ||
      authorizedBudget.value === experiment.value.authorizedTokenBudget),
)
const fact = (value: number | null | undefined) => (value == null ? '未提供' : String(value))
let readRequest: AbortController | undefined,
  mutationRequest: AbortController | undefined,
  timer: ReturnType<typeof setTimeout> | undefined,
  failures = 0,
  generation = 0,
  reportKey = window.crypto.randomUUID()
function stopTimer() {
  if (timer) clearTimeout(timer)
  timer = undefined
}
function schedule() {
  stopTimer()
  if (
    canRead.value &&
    experiment.value &&
    activeExperiment(experiment.value.status) &&
    !document.hidden &&
    !busy.value
  )
    timer = setTimeout(
      () => void loadDetail(true),
      Math.min(RUN_POLL_MAX_DELAY, RUN_POLL_INTERVAL * 2 ** failures),
    )
}
function ensureExperiment(value: Experiment) {
  if (
    String(value.id) !== props.resourceId ||
    String(value.spaceId) !== spaceId.value ||
    value.variants.some((item) => String(item.experimentId) !== String(value.id))
  )
    throw new Error('实验身份或空间归属不一致')
}
function ensurePreflight(value: ExperimentPreflight) {
  if (String(value.experimentId) !== props.resourceId) throw new Error('预检不属于当前实验')
}
function ensureReport(value: ExperimentReport) {
  if (String(value.experimentId) !== props.resourceId) throw new Error('报告不属于当前实验')
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
    if (datasetFilter.value && !/^[1-9]\d{0,18}$/.test(datasetFilter.value))
      throw new Error('请输入有效数据集版本 ID')
    if (createdFrom.value && createdTo.value && createdFrom.value > createdTo.value)
      throw new Error('创建起始不能晚于截止')
    const value = await api.searchExperiments(
      {
        spaceId: spaceId.value,
        status: statusFilter.value || undefined,
        datasetVersionId: datasetFilter.value || undefined,
        createdFrom: createdFrom.value || undefined,
        createdTo: createdTo.value || undefined,
        pageNum,
        pageSize: PAGE_SIZE,
      },
      pending.signal,
    )
    if (pending.signal.aborted) return
    if (value.records.some((item) => String(item.spaceId) !== spaceId.value))
      throw new Error('实验空间归属不一致')
    list.value = value
  } catch (e) {
    if (!pending.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!pending.signal.aborted) loading.value = false
  }
}
async function loadSelectedRun(pending: AbortController) {
  const variant = selectedVariant.value
  if (!variant?.evaluationRunId) {
    selectedRun.value = undefined
    return
  }
  try {
    const value = await api.getEvaluationRun(variant.evaluationRunId, pending.signal)
    if (pending.signal.aborted) return
    if (
      String(value.id) !== String(variant.evaluationRunId) ||
      String(value.spaceId) !== spaceId.value ||
      String(variant.experimentId) !== props.resourceId
    )
      throw new Error('关联运行身份或空间归属不一致')
    selectedRun.value = value
    runError.value = ''
  } catch (e) {
    if (!pending.signal.aborted) {
      selectedRun.value = undefined
      runError.value = normalizeApiError(e).message
    }
  }
}
async function loadReports(pending: AbortController) {
  try {
    const values = await api.getExperimentReportRevisions(props.resourceId!, pending.signal)
    if (pending.signal.aborted) return
    if (values.some((item) => String(item.experimentId) !== props.resourceId))
      throw new Error('报告列表身份不一致')
    revisions.value = values
    const requested = route.query.reportRevision
    if (
      requested !== undefined &&
      (typeof requested !== 'string' ||
        !/^[1-9]\d*$/.test(requested) ||
        !values.some((item) => item.revision === Number(requested)))
    )
      throw new Error('请求的报告版本不属于当前实验')
    const revision = requested
      ? Number(requested)
      : (selectedRevision.value ?? values.at(-1)?.revision)
    if (revision !== undefined && !values.some((item) => item.revision === revision))
      throw new Error('报告版本不存在')
    selectedRevision.value = revision
    if (revision === undefined) {
      report.value = undefined
      reportError.value = ''
      return
    }
    const value = await api.getExperimentReport(props.resourceId!, revision, pending.signal)
    if (pending.signal.aborted) return
    ensureReport(value)
    if (
      value.revision !== revision ||
      (value.manifestHash && value.manifestHash !== experiment.value?.manifestHash)
    )
      throw new Error('报告版本或 Manifest 身份不一致')
    // 未知 schema 的正文不进入可展示状态，即使异常响应携带正文。
    report.value =
      value.compatible && value.compatibilityCode === 'SUPPORTED' && value.schemaVersion === 1
        ? value
        : { ...value, report: null, selectedRecordIds: null }
    reportError.value = ''
  } catch (e) {
    if (!pending.signal.aborted) {
      report.value = undefined
      reportError.value = normalizeApiError(e).message
    }
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
    const previousStatus = experiment.value?.status,
      value = await api.getExperiment(props.resourceId, pending.signal)
    if (pending.signal.aborted) return
    ensureExperiment(value)
    experiment.value = value
    error.value = ''
    pollError.value = ''
    failures = 0
    if (!value.variants.some((item) => String(item.id) === selectedVariantId.value))
      selectedVariantId.value = String(
        value.variants.find((item) => item.evaluationRunId)?.id || '',
      )
    await loadSelectedRun(pending)
    if (pending.signal.aborted) return
    if (!quiet || previousStatus !== value.status) await loadReports(pending)
  } catch (e) {
    if (!pending.signal.aborted) {
      const fault = normalizeApiError(e)
      failures = Math.min(failures + 1, 3)
      if (quiet && experiment.value && fault.code !== 40300 && fault.code !== 40400)
        pollError.value = fault.message
      else {
        error.value = fault.message
        experiment.value = null
        selectedRun.value = undefined
        report.value = undefined
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
  if (!busy.value) void (props.resourceId ? loadDetail() : loadList(list.value.pageNum))
}
function selectVariant(item: ExperimentVariant) {
  if (busy.value) return
  selectedVariantId.value = String(item.id)
  selectedRun.value = undefined
  void loadDetail(true)
}
function chooseRevision(event: Event) {
  selectedRevision.value = Number((event.target as HTMLSelectElement).value)
  report.value = undefined
  decisionDialog.value = false
  void router.replace({ query: { ...route.query, reportRevision: String(selectedRevision.value) } })
}
async function created(value: Experiment) {
  creating.value = false
  ElMessage.success('离线实验已创建，请预检并确认预算')
  await router.push(`${basePath.value}/${value.id}`)
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
async function checkPreflight(open: boolean) {
  if (!canRead.value || busy.value || !props.resourceId) return
  const pending = beginMutation()
  try {
    const value = await api.getExperimentPreflight(props.resourceId, pending.signal)
    if (pending.signal.aborted) return
    ensurePreflight(value)
    preflight.value = value
    if (open && value.eligible && startable.value) {
      authorizedBudget.value = undefined
      budgetConfirmed.value = false
      ttl.value = WORKER_TTL_DEFAULT
      startDialog.value = true
    }
  } catch (e) {
    if (!pending.signal.aborted) actionError.value = normalizeApiError(e).message
  } finally {
    if (!pending.signal.aborted) {
      busy.value = false
      schedule()
    }
  }
}
function openStart() {
  void checkPreflight(true)
}
function preflightOnly() {
  void checkPreflight(false)
}
async function start() {
  if (!validStart.value || busy.value) return
  const budget = authorizedBudget.value!,
    currentTtl = ttl.value,
    currentGeneration = generation,
    pending = beginMutation()
  try {
    const value = await api.getExperimentPreflight(props.resourceId!, pending.signal)
    if (pending.signal.aborted) return
    ensurePreflight(value)
    preflight.value = value
    if (!value.eligible || !validBudget(budget, value.plannedTokenBudget))
      throw new Error('预检或计划预算已变化，请重新确认')
    try {
      await ElMessageBox.confirm(
        `计划 ${value.plannedTaskCount} 个隔离 Task，授权 ${budget} Token。授权是启动门禁，不是在途调用的严格封顶。`,
        '确认启动或恢复实验',
        { confirmButtonText: '确认执行', cancelButtonText: '返回', type: 'warning' },
      )
    } catch {
      return
    }
    if (
      pending.signal.aborted ||
      currentGeneration !== generation ||
      !canRun.value ||
      !startable.value ||
      budget !== authorizedBudget.value ||
      currentTtl !== ttl.value ||
      !budgetConfirmed.value
    )
      return
    const result = await api.startExperiment(
      props.resourceId!,
      { authorizedTokenBudget: budget, workerCapabilityTtlSeconds: currentTtl },
      pending.signal,
    )
    if (pending.signal.aborted) return
    ensureExperiment(result)
    experiment.value = result
    startDialog.value = false
    ElMessage.success('实验启动或恢复请求已处理')
  } catch (e) {
    if (!pending.signal.aborted)
      actionError.value = `${normalizeApiError(e).message}。请刷新状态后再操作。`
  } finally {
    if (!pending.signal.aborted) {
      busy.value = false
      void loadDetail()
    }
  }
}
async function cancel() {
  if (!canRun.value || !cancellable.value || busy.value) return
  const currentGeneration = generation
  try {
    await ElMessageBox.confirm(
      '请求取消关联运行；已完成的模型调用与产物不会撤销，取消可能等待对账。',
      '取消离线实验',
      { confirmButtonText: '请求取消', cancelButtonText: '继续运行', type: 'warning' },
    )
  } catch {
    return
  }
  if (currentGeneration !== generation || !canRun.value || !cancellable.value || busy.value) return
  const pending = beginMutation()
  try {
    const value = await api.cancelExperiment(props.resourceId!, pending.signal)
    if (pending.signal.aborted) return
    ensureExperiment(value)
    experiment.value = value
    ElMessage.success('取消请求已记录')
  } catch (e) {
    if (!pending.signal.aborted) actionError.value = normalizeApiError(e).message
  } finally {
    if (!pending.signal.aborted) {
      busy.value = false
      void loadDetail()
    }
  }
}
async function recalculate() {
  if (!canManage.value || !completed.value || busy.value) return
  const currentGeneration = generation
  try {
    await ElMessageBox.confirm(
      '按当前执行、指标与反馈显式重算；输入不变时复用原 revision，历史报告保持不变。',
      '重算实验报告',
      { confirmButtonText: '重算报告', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  if (currentGeneration !== generation || !canManage.value || !completed.value || busy.value) return
  const pending = beginMutation()
  try {
    const value = await api.recalculateExperimentReport(
      props.resourceId!,
      { clientRequestKey: reportKey },
      pending.signal,
    )
    if (pending.signal.aborted) return
    ensureReport(value)
    selectedRevision.value = value.revision
    reportKey = window.crypto.randomUUID()
    await router.replace({ query: { ...route.query, reportRevision: String(value.revision) } })
    ElMessage.success(`报告已处理：Revision ${value.revision}`)
  } catch (e) {
    if (!pending.signal.aborted)
      actionError.value = `${normalizeApiError(e).message}。再次重算复用本次幂等键。`
  } finally {
    if (!pending.signal.aborted) {
      busy.value = false
      void loadDetail()
    }
  }
}
async function decide() {
  if (
    !canManage.value ||
    !completed.value ||
    experiment.value?.decision ||
    !reportSupported.value ||
    !selectedRevision.value ||
    !decisionReason.value.trim() ||
    busy.value
  )
    return
  const revision = selectedRevision.value,
    reason = decisionReason.value.trim(),
    decision = decisionValue.value,
    currentGeneration = generation
  try {
    await ElMessageBox.confirm(
      `记录 ${decision}，固定引用 Revision ${revision}，不可覆盖。不修改生产 Agent、不分配线上流量。`,
      '记录人工结论',
      { confirmButtonText: '记录结论', cancelButtonText: '返回' },
    )
  } catch {
    return
  }
  if (
    currentGeneration !== generation ||
    !canManage.value ||
    experiment.value?.decision ||
    selectedRevision.value !== revision ||
    decisionReason.value.trim() !== reason ||
    decisionValue.value !== decision ||
    busy.value
  )
    return
  const pending = beginMutation()
  try {
    await api.submitExperimentDecision(
      props.resourceId!,
      { reportRevision: revision, decision, reason },
      pending.signal,
    )
    if (pending.signal.aborted) return
    decisionDialog.value = false
    decisionReason.value = ''
    ElMessage.success('人工结论已记录')
  } catch (e) {
    if (!pending.signal.aborted)
      actionError.value = `${normalizeApiError(e).message}。请刷新状态核对已有结论。`
  } finally {
    if (!pending.signal.aborted) {
      busy.value = false
      void loadDetail()
    }
  }
}
function visibilityChanged() {
  stopTimer()
  if (document.hidden) {
    readRequest?.abort()
    loading.value = false
  } else if (!experiment.value || activeExperiment(experiment.value.status))
    void loadDetail(!!experiment.value)
}
watch(
  () => [props.resourceId, spaceId.value, canRead.value],
  () => {
    generation++
    stopTimer()
    readRequest?.abort()
    mutationRequest?.abort()
    busy.value = false
    experiment.value = null
    report.value = undefined
    revisions.value = []
    selectedRevision.value = undefined
    selectedVariantId.value = ''
    selectedRun.value = undefined
    preflight.value = undefined
    startDialog.value = false
    decisionDialog.value = false
    creating.value = false
    authorizedBudget.value = undefined
    budgetConfirmed.value = false
    decisionReason.value = ''
    actionError.value = ''
    pollError.value = ''
    reportError.value = ''
    runError.value = ''
    failures = 0
    reportKey = window.crypto.randomUUID()
    statusFilter.value = ''
    datasetFilter.value = ''
    createdFrom.value = ''
    createdTo.value = ''
    void (props.resourceId ? loadDetail() : loadList(1))
  },
  { immediate: true },
)
watch(
  () => [canRun.value, canManage.value, canCreate.value],
  () => {
    generation++
    startDialog.value = false
    decisionDialog.value = false
    creating.value = false
    mutationRequest?.abort()
    busy.value = false
    schedule()
  },
)
watch(
  () => route.query.reportRevision,
  () => {
    if (!busy.value && props.resourceId) void loadDetail()
  },
)
onMounted(() => document.addEventListener('visibilitychange', visibilityChanged))
onBeforeUnmount(() => {
  generation++
  stopTimer()
  readRequest?.abort()
  mutationRequest?.abort()
  document.removeEventListener('visibilitychange', visibilityChanged)
})
</script>
<style scoped>
.experiment {
  display: grid;
  gap: var(--adw-space-4);
}
.experiment-header {
  display: flex;
  justify-content: space-between;
  gap: var(--adw-space-4);
  align-items: flex-start;
  flex-wrap: wrap;
}
.actions {
  display: flex;
  gap: var(--adw-space-2);
  flex-wrap: wrap;
  align-items: center;
}
.filters {
  display: flex;
  gap: var(--adw-space-3);
  flex-wrap: wrap;
  align-items: flex-end;
}
label {
  display: grid;
  gap: var(--adw-space-2);
  margin: var(--adw-space-2) 0;
}
input,
select,
textarea {
  padding: var(--adw-space-2);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-bg-surface);
  color: var(--adw-text-primary);
  max-width: 100%;
}
.consent {
  display: flex;
  align-items: center;
  gap: var(--adw-space-2);
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--adw-space-4);
}
article,
.variants,
.reports,
.preflight {
  padding: var(--adw-space-4);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  margin-bottom: var(--adw-space-4);
}
.variant-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--adw-space-3);
}
.variant-list a {
  display: block;
  margin-top: var(--adw-space-2);
}
p,
small,
a {
  overflow-wrap: anywhere;
}
small {
  color: var(--adw-text-secondary);
}
.table-wrap {
  overflow-x: auto;
}
table {
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
.decision {
  border-left: 3px solid var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
summary {
  cursor: pointer;
}
[role='alert'] {
  color: var(--adw-color-danger);
}
input:focus-visible,
select:focus-visible,
textarea:focus-visible,
a:focus-visible,
summary:focus-visible {
  outline: 2px solid var(--adw-color-primary);
}
</style>
