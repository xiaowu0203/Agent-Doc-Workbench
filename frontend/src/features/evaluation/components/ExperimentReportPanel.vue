<template>
  <section aria-label="不可变实验报告" class="report-panel">
    <header>
      <h3>Prompt 候选对比报告 · Revision {{ report.revision }}</h3>
      <p>
        生成 <BusinessTime :value="report.generatedAt" empty-text="未提供" /> · 生成者 #{{
          report.generatedBy || '未提供'
        }}。历史报告只读，分母以冻结数据集为准。
      </p>
    </header>
    <p v-if="!supported" role="alert">
      报告不可解释：{{ report.compatibilityCode }}。保留 revision 和持久化
      hash；不显示未知正文，也不自动重算。
    </p>
    <template v-else-if="content">
      <section aria-label="冻结来源摘要" class="source-summary">
        <p v-if="sourceSummary">
          冻结 {{ sourceSummary.caseCount }} 个用例，已知来源按任务 ID 去重为
          {{ sourceSummary.distinctSourceTaskCount }} 个；{{ sourceSummary.missingSourceCaseCount }}
          个用例缺少来源任务身份。
        </p>
        <p v-else-if="sourceError" role="status">
          来源去重摘要暂不可用：{{ sourceError }}。冻结用例
          {{ content.expectedCaseCount }} 个，不能推算独立来源数。<button
            @click="loadSourceSummary"
          >
            重试来源摘要
          </button>
        </p>
        <p v-else>正在核对冻结来源摘要；冻结用例 {{ content.expectedCaseCount }} 个。</p>
        <p>
          来源数量来自校验过的冻结清单；来源去重不代表统计独立性，不因候选组数或重复执行增加样本。
        </p>
      </section>
      <nav class="metric-groups" aria-label="报告指标分组">
        <button
          v-for="group in metricGroups"
          :key="group.key"
          :aria-pressed="groupFilter === group.key"
          @click="groupFilter = group.key"
        >
          {{ group.label }}
        </button>
      </nav>
      <p class="report-hint">分组仅切换展示；有效、期望、缺失及配对分母始终使用冻结报告。</p>
      <section v-if="overviewMetrics.length" aria-label="重点指标对照" class="report-comparison">
        <h4>重点指标对照</h4>
        <p>
          质量规则与执行成功分别判断；每项保留有效分母，费用按币种分开。用例数量不代表独立来源数量。
        </p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>指标</th>
                <th v-for="variant in summaryVariants" :key="variant">
                  {{ variant === 'baseline' ? '基线' : variant }}
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="key in overviewMetrics" :key="key">
                <th :title="key">{{ metricLabel(key) }}</th>
                <td v-for="variant in summaryVariants" :key="variant">
                  <template v-if="summaryFor(key, variant)">
                    <p
                      v-for="unit in summaryUnits(summaryFor(key, variant)!)"
                      :key="unit"
                      :title="String(summaryFor(key, variant)!.numericMeansByUnit[unit])"
                    >
                      {{
                        formatMetricNumber(summaryFor(key, variant)!.numericMeansByUnit[unit], unit)
                      }}
                    </p>
                    <span v-if="!summaryFor(key, variant)!.validCount">无有效结果</span>
                    <span v-else-if="!summaryUnits(summaryFor(key, variant)!).length"
                      >真 {{ summaryFor(key, variant)!.trueCount }} / 假
                      {{ summaryFor(key, variant)!.falseCount }}</span
                    >
                    <small
                      >有效 {{ summaryFor(key, variant)!.validCount }} /
                      {{ summaryFor(key, variant)!.expectedCount }} · 缺失
                      {{ summaryFor(key, variant)!.missingCount }}</small
                    >
                    <template v-if="variant !== 'baseline' && comparisonFor(key, variant)">
                      <p
                        v-for="delta in pairDifferences(comparisonFor(key, variant)!)"
                        :key="delta.unit || 'unknown'"
                        :title="String(delta.value)"
                      >
                        配对差（候选 − 基线）：{{ differenceText(delta.value, delta.unit) }}
                      </p>
                      <small
                        >配对 {{ comparisonFor(key, variant)!.pairedCount }} /
                        {{ pairExpected(variant, key) }}；按共同用例的冻结配对结果展示。</small
                      >
                      <small v-if="!comparisonFor(key, variant)!.pairedCount"
                        >无有效配对，不计算差值。</small
                      >
                    </template>
                  </template>
                  <span v-else>未提供</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <div class="report-overview">
        <article>
          <span>冻结用例 / 配置组</span
          ><strong>{{ content.expectedCaseCount }} / {{ content.variantCount }}</strong>
        </article>
        <article>
          <span>授权 Token</span><strong>{{ fact(content.authorizedTokenBudget) }}</strong>
        </article>
        <article>
          <span>实际 Token</span><strong>{{ fact(content.actualTokenUsage) }}</strong>
        </article>
        <article>
          <span>超额 Token</span><strong>{{ fact(content.budgetOverrun) }}</strong>
        </article>
      </div>
      <div class="report-columns">
        <div class="report-main">
          <details aria-label="Variant 指标汇总">
            <summary>全部指标汇总与原值</summary>
            <p>各指标独立展示有效分母；成本按币种分开，未提供值不按 0 处理。</p>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>配置组 / 指标</th>
                    <th>有效 / 期望 / 缺失</th>
                    <th>Boolean 真 / 假</th>
                    <th>数值总值 / 均值（按单位）</th>
                    <th>缺失原因</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, index) in groupedSummaries" :key="index">
                    <td>
                      {{ row.variantKey
                      }}<small :title="row.metricKey">{{ metricLabel(row.metricKey) }}</small>
                    </td>
                    <td>{{ row.validCount }} / {{ row.expectedCount }} / {{ row.missingCount }}</td>
                    <td>
                      {{
                        row.validCount <= 0
                          ? '无有效结果'
                          : row.trueCount + row.falseCount > 0
                            ? `${row.trueCount} / ${row.falseCount}`
                            : '不适用'
                      }}
                    </td>
                    <td>
                      <p
                        v-for="unit in summaryUnits(row)"
                        :key="unit"
                        :title="`原值：${row.numericTotalsByUnit[unit]} / ${row.numericMeansByUnit[unit]} ${unit}`"
                      >
                        {{ formatMetricNumber(row.numericTotalsByUnit[unit], unit) }} /
                        {{ formatMetricNumber(row.numericMeansByUnit[unit], unit) }}
                      </p>
                      <span v-if="!summaryUnits(row).length">无有效数值汇总</span>
                    </td>
                    <td>
                      <p v-for="(count, reason) in row.missingReasons" :key="reason">
                        {{ reason }}：{{ count }}
                      </p>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!groupedSummaries.length">当前分组无指标汇总。</p>
            <details v-if="charts.length">
              <summary>同单位数值均值比较</summary>
              <p>柱长按本组最大绝对值归一化；保留正负原值。没有有效分母的指标不绘图。</p>
              <figure v-for="chart in charts" :key="chart.key">
                <figcaption>{{ chart.key }}</figcaption>
                <div v-for="row in chart.rows" :key="row.variant" class="chart-row">
                  <span>{{ row.variant }} · n={{ row.count }}</span>
                  <div class="bar-track">
                    <div
                      class="bar"
                      :style="{
                        width: `${chart.maximum ? (Math.abs(row.value) / chart.maximum) * 100 : 0}%`,
                      }"
                    />
                  </div>
                  <strong>{{ row.value }}</strong>
                </div>
              </figure>
            </details>
          </details>
          <section aria-label="Candidate 配对比较">
            <h4>候选配对比较</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>候选 / 指标</th>
                    <th>配对分母</th>
                    <th>改善 / 下降 / 不变</th>
                    <th>币种不可比</th>
                    <th>候选 − 基线</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, index) in groupedComparisons" :key="index">
                    <td>
                      {{ row.candidateVariantKey || '未提供'
                      }}<small :title="row.metricKey">{{ metricLabel(row.metricKey) }}</small>
                    </td>
                    <td>
                      {{ row.pairedCount }} /
                      {{ pairExpected(row.candidateVariantKey, row.metricKey) }}
                    </td>
                    <td>
                      {{ row.improvedCount }} / {{ row.worsenedCount }} / {{ row.unchangedCount }}
                    </td>
                    <td>{{ row.incomparableCurrencyCount }}</td>
                    <td>
                      <p
                        v-for="delta in pairDifferences(row)"
                        :key="delta.unit || 'unknown'"
                        :title="String(delta.value)"
                      >
                        {{ differenceText(delta.value, delta.unit) }}
                      </p>
                      <span v-if="!pairDifferences(row).length">{{
                        row.pairedCount
                          ? '未提供数值差值，请结合改善/下降/不变计数'
                          : '无有效配对，不计算差值'
                      }}</span>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!groupedComparisons.length">当前分组没有可用比较；不推断候选更优。</p>
          </section>
          <section aria-label="逐用例原值矩阵">
            <h4>逐用例原值矩阵</h4>
            <div class="matrix-filters">
              <label
                >配置组<select v-model="variantFilter" aria-label="矩阵 Variant">
                  <option value="">全部</option>
                  <option v-for="key in variants" :key="key">{{ key }}</option>
                </select></label
              ><label
                >指标<select v-model="metricFilter" aria-label="矩阵指标">
                  <option value="">全部</option>
                  <option v-for="key in metrics" :key="key">{{ key }}</option>
                </select></label
              >
              <label
                >结果范围<select v-model="cellState" aria-label="矩阵结果范围">
                  <option value="">全部结果</option>
                  <option value="missing">缺失结果</option>
                  <option value="failed">执行异常或布尔未通过</option>
                </select></label
              >
            </div>
            <p>当前匹配 {{ filteredCells.length }} 项；筛选不改变冻结汇总或配对分母。</p>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>用例 / Variant / 指标</th>
                    <th>原值 / 单位</th>
                    <th>Metric / Evaluator 身份</th>
                    <th>Evidence 引用 / 执行下钻</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(cell, index) in visibleCells" :key="index">
                    <td>
                      {{ caseLabel(cell.value.testCaseVersionId)
                      }}<small>#{{ cell.value.testCaseVersionId || '未提供' }}</small
                      ><small>{{ cell.value.variantKey }} · {{ cell.value.metricKey }}</small>
                    </td>
                    <td>
                      {{ metricText(cell.value)
                      }}<small>{{ cell.value.unit ?? '未指定单位' }}</small>
                    </td>
                    <td>
                      Metric #{{ cell.value.metricId || '未提供'
                      }}<small>Evaluator #{{ cell.value.evaluatorVersionId || '执行指标' }}</small>
                    </td>
                    <td>
                      <p>
                        {{
                          cell.evidenceIds.length
                            ? cell.evidenceIds.map((id) => `#${id}`).join('、')
                            : '证据引用缺失'
                        }}
                      </p>
                      <RouterLink v-if="selection(cell)?.runId" :to="runLink(selection(cell)!)"
                        >查看 Run / Attempt</RouterLink
                      ><RouterLink
                        v-if="canReadTask && selection(cell)?.taskId"
                        :to="taskLink(selection(cell)!.taskId!)"
                        >查看 Task 证据</RouterLink
                      >
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!filteredCells.length">当前条件无指标原值。</p>
            <el-pagination
              v-if="filteredCells.length > PAGE_SIZE"
              v-model:current-page="page"
              :page-size="PAGE_SIZE"
              :total="filteredCells.length"
              layout="prev, pager, next"
              size="small"
            />
          </section>
          <details>
            <summary>全部执行选择与缺失状态（{{ content.cases.length }}）</summary>
            <article v-for="(item, index) in content.cases" :key="index">
              <strong
                >{{ item.variantKey }} · 用例 #{{ item.testCaseVersionId || '未提供' }}</strong
              >
              <p>
                Attempt #{{ item.attemptId || '未提供' }} ·
                {{ item.attemptStatus || '执行未提供' }} · {{ item.failureCode || '未提供失败码' }}
              </p>
              <RouterLink v-if="item.runId" :to="runLink(item)">查看 Run / Attempt</RouterLink
              ><RouterLink v-if="canReadTask && item.taskId" :to="taskLink(item.taskId)"
                >查看 Task 证据</RouterLink
              >
            </article>
          </details>
        </div>
        <aside>
          <section aria-label="反馈与证据覆盖">
            <h4>反馈与证据覆盖</h4>
            <p>
              证据引用覆盖 {{ evidenceCovered }} /
              {{ content.cells.filter((cell) => cell.value.metricId !== null).length }} 个已选
              Metric；不作为质量分。
            </p>
            <p v-for="(coverage, index) in content.feedbackCoverage" :key="index">
              {{ coverage.variantKey }} · {{ coverage.sourceType || '来源未提供' }}：{{
                coverage.coveredCaseCount
              }}
              / {{ content.expectedCaseCount }} 用例
            </p>
            <p v-if="!content.feedbackCoverage.length">未提供反馈覆盖。</p>
          </section>
          <details>
            <summary>Evidence 身份（{{ content.metricEvidence.length }}）</summary>
            <article v-for="(item, index) in content.metricEvidence" :key="index">
              <strong>{{ item.evidenceType }} · #{{ item.evidenceId || '未提供' }}</strong>
              <p>Metric #{{ item.metricId || '未提供' }}</p>
              <p>业务 ID：{{ item.businessId || '未提供' }}</p>
              <p>内容 hash：{{ item.contentHash || '未提供' }}</p>
            </article>
          </details>
          <details>
            <summary>反馈明细（{{ content.feedback.length }}）</summary>
            <p>重复记录并列保留，MANUAL 与 CHANGE_REQUEST 独立展示，不合成为质量分。</p>
            <article v-for="item in content.feedback" :key="String(item.id)">
              <strong>{{ item.variantKey }} · {{ item.label || '未提供' }}</strong>
              <p>
                {{ item.sourceType || '来源未提供' }} · #{{ item.id }} · 用例 #{{
                  item.testCaseVersionId || '未提供'
                }}
              </p>
              <p>来源 ID：{{ item.sourceBusinessId || '未提供' }} · 分数 {{ fact(item.score) }}</p>
            </article>
          </details>
        </aside>
      </div>
    </template>
    <details>
      <summary>报告身份与选中记录</summary>
      <dl>
        <dt>schema</dt>
        <dd>{{ report.schemaVersion ?? '未提供' }}</dd>
        <dt>Manifest hash</dt>
        <dd>{{ report.manifestHash || '未提供' }}</dd>
        <dt>内容 hash</dt>
        <dd>{{ report.contentHash || '未提供' }}</dd>
        <dt>计算输入 hash</dt>
        <dd>{{ report.calculationInputHash || '未提供' }}</dd>
      </dl>
      <template v-if="supported && report.selectedRecordIds"
        ><p v-for="(ids, key) in report.selectedRecordIds" :key="key">
          {{ key }}：{{ ids.length ? ids.join('、') : '无' }}
        </p></template
      >
    </details>
  </section>
</template>
<script setup lang="ts">
import BusinessTime from '@/shared/components/BusinessTime.vue'
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { RouterLink } from 'vue-router'
import { ElPagination } from 'element-plus'
import { metricText } from '../experiment'
import { queryCaseVersionNames, getExperimentSourceSummary } from '../api/evaluation-api'
import { normalizeApiError } from '@/api/errors'
import { formatMetricNumber, metricLabel } from '../display'
import type { EntityId } from '@/features/workspace/types'
import type {
  ExperimentReport,
  ExperimentReportCell,
  ExperimentReportCaseSelection,
  ExperimentReportVariantSummary,
  CaseVersionName,
  ExperimentReportPairComparison,
  ExperimentSourceSummary,
} from '../types'
const props = defineProps<{ report: ExperimentReport; spaceId: string; canReadTask: boolean }>()
const PAGE_SIZE = 10
const supported = computed(
  () =>
    props.report.compatible &&
    props.report.compatibilityCode === 'SUPPORTED' &&
    props.report.schemaVersion === 1 &&
    !!props.report.report,
)
const content = computed(() => (supported.value ? props.report.report : null))
const metricGroups = [
  { key: '', label: '全部指标' },
  { key: 'quality', label: '质量规则' },
  { key: 'execution', label: '执行状态' },
  { key: 'resource', label: '成本与用量' },
  { key: 'latency', label: '耗时' },
  { key: 'other', label: '其他指标' },
]
const groupFilter = ref('')
function metricGroup(key: string) {
  if (key.startsWith('evaluation.')) return 'quality'
  if (key === 'execution.success') return 'execution'
  if (key === 'execution.latency') return 'latency'
  if (
    [
      'execution.cost',
      'execution.total-tokens',
      'execution.input-tokens',
      'execution.output-tokens',
      'execution.cached-input-tokens',
      'execution.retry-count',
    ].includes(key)
  )
    return 'resource'
  return 'other'
}
const inGroup = (key: string) => !groupFilter.value || metricGroup(key) === groupFilter.value
const groupedSummaries = computed(
  () => content.value?.summaries.filter((row) => inGroup(row.metricKey)) || [],
)
const groupedComparisons = computed(
  () => content.value?.comparisons.filter((row) => inGroup(row.metricKey)) || [],
)
const sourceSummary = ref<ExperimentSourceSummary | null>(null),
  sourceError = ref('')
let sourcePending: AbortController | undefined
async function loadSourceSummary() {
  sourcePending?.abort()
  sourceSummary.value = null
  sourceError.value = ''
  if (!supported.value) return
  const controller = new AbortController(),
    space = props.spaceId,
    report = props.report
  sourcePending = controller
  try {
    const value = await getExperimentSourceSummary(report.experimentId, controller.signal)
    if (controller.signal.aborted) return
    if (
      String(value.experimentId) !== String(report.experimentId) ||
      String(value.spaceId) !== space ||
      value.manifestHash !== report.manifestHash ||
      value.caseCount !== report.report?.expectedCaseCount ||
      ![value.caseCount, value.distinctSourceTaskCount, value.missingSourceCaseCount].every(
        (count) => Number.isSafeInteger(count) && count >= 0,
      ) ||
      value.distinctSourceTaskCount > value.caseCount - value.missingSourceCaseCount
    )
      throw new Error('来源摘要身份或计数不一致')
    sourceSummary.value = value
  } catch (error) {
    if (!controller.signal.aborted) sourceError.value = normalizeApiError(error).message
  }
}
watch(
  [
    supported,
    () => props.spaceId,
    () => props.report.experimentId,
    () => props.report.manifestHash,
    () => content.value?.expectedCaseCount,
  ],
  loadSourceSummary,
  { immediate: true },
)
onBeforeUnmount(() => sourcePending?.abort())
const summaryVariants = computed(() => [
  ...new Set(content.value?.summaries.map((row) => row.variantKey)),
])
const overviewMetrics = computed(() =>
  [
    'evaluation.document-change.valid',
    'evaluation.document-change.accuracy',
    'evaluation.artifact-contract.valid',
    'execution.success',
    'execution.cost',
    'execution.latency',
    'execution.total-tokens',
  ].filter((key) => inGroup(key) && content.value?.summaries.some((row) => row.metricKey === key)),
)
function summaryFor(key: string, variant: string) {
  return content.value?.summaries.find((row) => row.metricKey === key && row.variantKey === variant)
}
function comparisonFor(key: string, variant: string) {
  return content.value?.comparisons.find(
    (row) => row.metricKey === key && row.candidateVariantKey === variant,
  )
}
function pairDifferences(
  row: ExperimentReportPairComparison,
): { unit: string | null; value: number }[] {
  if (!row.pairedCount) return []
  const currencies = Object.entries(row.meanCandidateMinusBaselineByCurrency)
  if (currencies.length) return currencies.map(([unit, value]) => ({ unit, value }))
  if (row.meanCandidateMinusBaseline == null) return []
  const baseline = summaryFor(row.metricKey, 'baseline'),
    candidate = summaryFor(row.metricKey, row.candidateVariantKey || '')
  const units = baseline ? summaryUnits(baseline) : [],
    otherUnits = candidate ? summaryUnits(candidate) : []
  const unit =
    units.length === 1 && otherUnits.length === 1 && units[0] === otherUnits[0] ? units[0]! : null
  return [{ unit, value: row.meanCandidateMinusBaseline }]
}
function differenceText(value: number, unit: string | null) {
  return `${formatMetricNumber(value, unit === 'ratio' ? 'percentage-point' : unit)}${unit === null ? '（单位未确认）' : ''}`
}
const variantFilter = ref(''),
  metricFilter = ref(''),
  cellState = ref(''),
  page = ref(1)
const variants = computed(() => [
  ...new Set(content.value?.cells.map((cell) => cell.value.variantKey)),
])
const metrics = computed(() => [
  ...new Set(
    content.value?.cells
      .filter((cell) => inGroup(cell.value.metricKey))
      .map((cell) => cell.value.metricKey),
  ),
])
const filteredCells = computed(
  () =>
    content.value?.cells.filter(
      (cell) =>
        inGroup(cell.value.metricKey) &&
        (!variantFilter.value || cell.value.variantKey === variantFilter.value) &&
        (!metricFilter.value || cell.value.metricKey === metricFilter.value) &&
        (!cellState.value ||
          (cellState.value === 'missing'
            ? !!cell.value.missingReason
            : cell.value.booleanValue === false ||
              ['REPLAY_FAILED', 'EVALUATOR_FAILED'].includes(
                selection(cell)?.attemptStatus || '',
              ))),
    ) || [],
)
const visibleCells = computed(() =>
  filteredCells.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
)
const caseNames = ref<Record<string, CaseVersionName>>({})
const visibleCaseIds = computed(() => [
  ...new Set(
    visibleCells.value
      .map((cell) => cell.value.testCaseVersionId)
      .filter((id): id is EntityId => id !== null),
  ),
])
let namesPending: AbortController | undefined
watch(
  [() => props.spaceId, () => props.report.contentHash, visibleCaseIds],
  async () => {
    namesPending?.abort()
    caseNames.value = {}
    if (!supported.value || !visibleCaseIds.value.length) return
    const controller = new AbortController(),
      ids = [...visibleCaseIds.value],
      space = props.spaceId
    namesPending = controller
    try {
      const rows = await queryCaseVersionNames(space, ids, controller.signal)
      if (controller.signal.aborted) return
      if (
        rows.some(
          (row) =>
            String(row.spaceId) !== space ||
            !ids.some((id) => String(id) === String(row.versionId)),
        )
      )
        return
      caseNames.value = Object.fromEntries(rows.map((row) => [String(row.versionId), row]))
    } catch {
      // 可选名称不可用时保留报告中的冻结身份，不影响已核验正文。
      if (!controller.signal.aborted) caseNames.value = {}
    }
  },
  { immediate: true },
)
function caseLabel(id: EntityId | null) {
  const value = caseNames.value[String(id)]
  return value ? `${value.name} · v${value.versionNo}（当前目录名称）` : '用例名称不可用'
}
onBeforeUnmount(() => namesPending?.abort())
const evidenceCovered = computed(
  () =>
    new Set(
      content.value?.cells
        .filter((cell) => cell.value.metricId !== null && cell.evidenceIds.length)
        .map((cell) => String(cell.value.metricId)),
    ).size,
)
const fact = (value: number | null | undefined) =>
  value == null ? '未提供' : formatMetricNumber(value)
const summaryUnits = (row: ExperimentReportVariantSummary) => [
  ...new Set([...Object.keys(row.numericTotalsByUnit), ...Object.keys(row.numericMeansByUnit)]),
]
function pairExpected(candidate: string | null, metric: string) {
  const baseline = content.value?.summaries.find(
    (row) => row.variantKey === 'baseline' && row.metricKey === metric,
  )
  const alternative = content.value?.summaries.find(
    (row) => row.variantKey === candidate && row.metricKey === metric,
  )
  return baseline && alternative && baseline.expectedCount === alternative.expectedCount
    ? baseline.expectedCount
    : '指标期望未提供'
}
function selection(cell: ExperimentReportCell) {
  return content.value?.cases.find(
    (item) =>
      item.variantKey === cell.value.variantKey &&
      String(item.testCaseVersionId) === String(cell.value.testCaseVersionId) &&
      String(item.runId) === String(cell.value.runId),
  )
}
function runLink(item: ExperimentReportCaseSelection) {
  return {
    path: `/spaces/${props.spaceId}/evaluation/runs/${item.runId}`,
    query: {
      caseRunId: item.caseRunId ? String(item.caseRunId) : undefined,
      attemptId: item.attemptId ? String(item.attemptId) : undefined,
    },
  }
}
function taskLink(id: EntityId) {
  return `/spaces/${props.spaceId}/tasks/${id}?tab=evidence`
}
const charts = computed(() => {
  const groups = new Map<string, { variant: string; count: number; value: number }[]>()
  for (const row of groupedSummaries.value) {
    // v1 只给出指标级分母；多单位汇总不具备每单位分母，保留表格展示。
    if (row.validCount <= 0 || Object.keys(row.numericMeansByUnit).length !== 1) continue
    for (const [unit, value] of Object.entries(row.numericMeansByUnit)) {
      if (!Number.isFinite(value)) continue
      const key = `${row.metricKey} (${unit || '未指定单位'})`,
        rows = groups.get(key) || []
      rows.push({ variant: row.variantKey, count: row.validCount, value })
      groups.set(key, rows)
    }
  }
  return [...groups.entries()]
    .filter(([, rows]) => rows.length > 1)
    .map(([key, rows]) => ({
      key,
      rows,
      maximum: Math.max(...rows.map((row) => Math.abs(row.value))),
    }))
})
watch(groupFilter, () => {
  metricFilter.value = ''
  page.value = 1
})
watch(
  () => [props.report.revision, variantFilter.value, metricFilter.value, cellState.value],
  () => {
    page.value = 1
  },
)
</script>
<style scoped>
.report-panel {
  min-width: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--adw-space-4);
}
.report-panel > * {
  min-width: 0;
}
.report-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--adw-space-3);
}
.report-panel > header h3,
.report-panel > .report-hint,
.source-summary p {
  margin: 0;
}
.report-panel .source-summary {
  border: 0;
  padding: 0;
  display: grid;
  gap: var(--adw-space-2);
}
.metric-groups {
  display: flex;
  flex-wrap: wrap;
  gap: var(--adw-space-2);
}
.metric-groups button {
  padding: var(--adw-space-2) var(--adw-space-3);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  background: var(--adw-surface);
  color: var(--adw-text-primary);
  cursor: pointer;
}
.metric-groups button[aria-pressed='true'] {
  border-color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
  font-weight: 600;
}
.metric-groups button:focus-visible {
  outline: 2px solid var(--adw-color-primary);
  outline-offset: 2px;
}
.report-overview article {
  border: 1px solid #dfe7f1;
  border-radius: 8px;
  padding: 14px;
  background: #f5f8fd;
}
.report-overview strong {
  display: block;
  font-size: var(--adw-font-size-title);
  margin-top: var(--adw-space-2);
}
.report-columns {
  display: grid;
  grid-template-columns: minmax(0, 3fr) minmax(230px, 1fr);
  gap: var(--adw-space-4);
  align-items: start;
}
.report-main,
aside {
  min-width: 0;
  display: grid;
  gap: var(--adw-space-4);
}
section section,
details {
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  padding: var(--adw-space-4);
}
.table-wrap {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
}
table {
  border-collapse: collapse;
  width: 100%;
}
th,
td {
  text-align: left;
  border-bottom: 1px solid var(--adw-border-color);
  padding: var(--adw-space-2);
  vertical-align: top;
  overflow-wrap: anywhere;
  min-width: 70px;
}
thead th { background: #f3f6fa; color: #52647b; font-size: 13px; }
.report-comparison table {
  min-width: 420px;
}
small {
  display: block;
  color: var(--adw-text-secondary);
  margin-top: var(--adw-space-1);
}
article + article {
  border-top: 1px solid var(--adw-border-color);
  margin-top: var(--adw-space-3);
  padding-top: var(--adw-space-3);
}
p,
dd,
a {
  overflow-wrap: anywhere;
}
dd {
  margin-left: 0;
}
dt {
  color: var(--adw-text-secondary);
}
a {
  display: block;
}
.matrix-filters {
  display: flex;
  gap: var(--adw-space-3);
  flex-wrap: wrap;
  margin-bottom: var(--adw-space-3);
}
label {
  display: grid;
  gap: var(--adw-space-2);
}
select {
  max-width: 260px;
  padding: var(--adw-space-2);
}
summary {
  cursor: pointer;
  font-weight: 600;
}
[role='alert'] {
  color: var(--adw-color-danger);
}
.chart-row {
  display: grid;
  grid-template-columns: 140px minmax(0, 1fr) 80px;
  gap: var(--adw-space-2);
  align-items: center;
  margin: var(--adw-space-2) 0;
}
figure {
  margin: var(--adw-space-4) 0;
}
.bar-track {
  height: 12px;
  background: var(--adw-border-color);
}
.bar {
  height: 100%;
  background: var(--adw-color-primary);
}
@media (max-width: 1100px) {
  .report-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}
@media (max-width: 600px) {
  .chart-row {
    grid-template-columns: 100px minmax(0, 1fr) 50px;
  }
}
select:focus-visible,
summary:focus-visible,
a:focus-visible {
  outline: 2px solid var(--adw-color-primary);
}
</style>
