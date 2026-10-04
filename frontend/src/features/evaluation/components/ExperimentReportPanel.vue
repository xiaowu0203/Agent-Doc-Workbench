<template>
  <section aria-label="不可变实验报告" class="report-panel">
    <header>
      <h3>Prompt 候选对比报告 · Revision {{ report.revision }}</h3>
      <p>
        生成 {{ report.generatedAt || '未提供' }} · 生成者 #{{
          report.generatedBy || '未提供'
        }}。历史报告只读，分母以冻结数据集为准。
      </p>
    </header>
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
    <p v-if="!supported" role="alert">
      报告不可解释：{{ report.compatibilityCode }}。保留 revision 和持久化
      hash；不显示未知正文，也不自动重算。
    </p>
    <template v-else-if="content">
      <div class="report-overview">
        <article>
          <span>冻结用例 / Variant</span
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
          <section aria-label="Variant 指标汇总">
            <h4>Variant 指标汇总</h4>
            <p>各指标独立展示有效分母；成本按币种分开，未提供值不按 0 处理。</p>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Variant / 指标</th>
                    <th>有效 / 期望 / 缺失</th>
                    <th>Boolean 真 / 假</th>
                    <th>数值总值 / 均值（按单位）</th>
                    <th>缺失原因</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, index) in content.summaries" :key="index">
                    <td>
                      {{ row.variantKey }}<small>{{ row.metricKey }}</small>
                    </td>
                    <td>{{ row.validCount }} / {{ row.expectedCount }} / {{ row.missingCount }}</td>
                    <td>{{ row.trueCount }} / {{ row.falseCount }}</td>
                    <td>
                      <p v-for="unit in summaryUnits(row)" :key="unit">
                        {{ unit || '未指定单位' }}：{{ fact(row.numericTotalsByUnit[unit]) }} /
                        {{ fact(row.numericMeansByUnit[unit]) }}
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
            <p v-if="!content.summaries.length">尚无指标汇总。</p>
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
          </section>
          <section aria-label="Candidate 配对比较">
            <h4>Candidate 配对比较</h4>
            <div class="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Candidate / 指标</th>
                    <th>配对分母</th>
                    <th>改善 / 下降 / 不变</th>
                    <th>币种不可比</th>
                    <th>Candidate − Baseline</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, index) in content.comparisons" :key="index">
                    <td>
                      {{ row.candidateVariantKey || '未提供' }}<small>{{ row.metricKey }}</small>
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
                      <template v-if="Object.keys(row.meanCandidateMinusBaselineByCurrency).length"
                        ><p
                          v-for="(value, currency) in row.meanCandidateMinusBaselineByCurrency"
                          :key="currency"
                        >
                          {{ currency }}：{{ value }}
                        </p></template
                      ><template v-else>{{
                        row.pairedCount
                          ? fact(row.meanCandidateMinusBaseline)
                          : '无有效配对，不计算差值'
                      }}</template>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p v-if="!content.comparisons.length">没有可用比较；不推断候选更优。</p>
          </section>
          <section aria-label="逐用例原值矩阵">
            <h4>逐用例原值矩阵</h4>
            <div class="matrix-filters">
              <label
                >Variant<select v-model="variantFilter" aria-label="矩阵 Variant">
                  <option value="">全部</option>
                  <option v-for="key in variants" :key="key">{{ key }}</option>
                </select></label
              ><label
                >指标<select v-model="metricFilter" aria-label="矩阵指标">
                  <option value="">全部</option>
                  <option v-for="key in metrics" :key="key">{{ key }}</option>
                </select></label
              >
            </div>
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
                      #{{ cell.value.testCaseVersionId || '未提供'
                      }}<small>{{ cell.value.variantKey }} · {{ cell.value.metricKey }}</small>
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
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElPagination } from 'element-plus'
import { metricText } from '../experiment'
import type { EntityId } from '@/features/workspace/types'
import type {
  ExperimentReport,
  ExperimentReportCell,
  ExperimentReportCaseSelection,
  ExperimentReportVariantSummary,
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
const variantFilter = ref(''),
  metricFilter = ref(''),
  page = ref(1)
const variants = computed(() => [
  ...new Set(content.value?.cells.map((cell) => cell.value.variantKey)),
])
const metrics = computed(() => [
  ...new Set(content.value?.cells.map((cell) => cell.value.metricKey)),
])
const filteredCells = computed(
  () =>
    content.value?.cells.filter(
      (cell) =>
        (!variantFilter.value || cell.value.variantKey === variantFilter.value) &&
        (!metricFilter.value || cell.value.metricKey === metricFilter.value),
    ) || [],
)
const visibleCells = computed(() =>
  filteredCells.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE),
)
const evidenceCovered = computed(
  () =>
    new Set(
      content.value?.cells
        .filter((cell) => cell.value.metricId !== null && cell.evidenceIds.length)
        .map((cell) => String(cell.value.metricId)),
    ).size,
)
const fact = (value: number | null | undefined) => (value == null ? '未提供' : String(value))
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
  for (const row of content.value?.summaries || []) {
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
watch(
  () => [props.report.revision, variantFilter.value, metricFilter.value],
  () => {
    page.value = 1
  },
)
</script>
<style scoped>
.report-panel {
  display: grid;
  gap: var(--adw-space-4);
}
.report-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: var(--adw-space-3);
}
.report-overview article {
  border-left: 3px solid var(--adw-color-primary);
  padding: var(--adw-space-3);
  background: var(--adw-color-primary-soft);
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
