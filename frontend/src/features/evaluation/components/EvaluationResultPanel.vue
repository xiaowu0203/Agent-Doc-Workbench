<template>
  <section class="result-detail" aria-label="评估结果详情">
    <header>
      <h4>{{ summary.evaluatorName || '评估器名称不可用' }} · 评价结果</h4>
      <p>
        评价结果 #{{ summary.id }} <CopyButton :value="String(summary.id)" label="评价结果 ID" />
      </p>
      <EvaluationStatusTag domain="result" :status="summary.status" />
      <p>
        评价尝试 {{ summary.evaluationAttemptNo }}
        {{ summary.currentEvaluationResultAttempt ? '（本执行尝试的当前评价）' : '（历史，只读）' }}
      </p>
    </header>
    <DataState :loading="loading" :error="error" @retry="load">
      <template v-if="detail">
        <dl>
          <dt>评估器版本</dt>
          <dd>
            #{{ detail.evaluatorVersionId || '不可用' }} ·
            {{ summary.evaluatorName || '名称不可用' }}
          </dd>
          <dt>实现版本</dt>
          <dd>{{ detail.implementationVersion || '不可用' }}</dd>
          <dt>摘要码</dt>
          <dd>{{ detail.summaryCode || '未提供' }}</dd>
          <dt>便捷得分</dt>
          <dd>{{ detail.score ?? '未提供' }}（不替代标准 Metric）</dd>
          <dt>评估时间</dt>
          <dd>{{ detail.startedAt || '不可用' }} ～ {{ detail.finishedAt || '不可用' }}</dd>
        </dl>
        <h4>标准指标</h4>
        <p v-if="!detail.metrics.length">
          缺失：此结果没有标准指标。异常、跳过和证据缺失不计为 0。
        </p>
        <div v-else class="metric-table-wrap">
          <table>
            <thead>
              <tr>
                <th>指标 / 身份</th>
                <th>数值（悬停查看原值）</th>
                <th>来源 / 方向</th>
                <th>证据引用</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="metric in detail.metrics" :key="String(metric.id)">
                <td>
                  {{ metricLabel(metric.metricKey) }}
                  <details>
                    <summary>指标身份与原值</summary>
                    <small
                      >{{ metric.metricKey }}
                      <CopyButton :value="metric.metricKey" label="指标 key"
                    /></small>
                    <small
                      >指标 #{{ metric.id }} <CopyButton :value="String(metric.id)" label="指标 ID"
                    /></small>
                    <small>契约 {{ metric.contractVersion ?? '不可用' }}</small>
                    <small
                      >原值：{{
                        metric.numericValue ?? metric.booleanValue ?? metric.stringValue ?? '未提供'
                      }}
                      {{ metric.unit || '' }}</small
                    >
                  </details>
                </td>
                <td>
                  <EvaluationMetricValue
                    :numeric-value="metric.numericValue"
                    :boolean-value="metric.booleanValue"
                    :string-value="metric.stringValue"
                    :unit="metric.unit"
                    :missing-reason="
                      metric.numericValue === null &&
                      metric.booleanValue === null &&
                      metric.stringValue === null
                        ? 'VALUE_MISSING'
                        : null
                    "
                  />
                </td>
                <td>
                  <span :title="metric.source || ''">{{
                    metricSources[metric.source || ''] || metric.source || '不可用'
                  }}</span>
                  <small :title="metric.direction || ''">{{
                    metricDirections[metric.direction || ''] || metric.direction || '未提供方向'
                  }}</small>
                </td>
                <td>
                  <span v-if="!metric.evidence.length">缺失：没有证据引用</span
                  ><span
                    v-for="reference in metric.evidence"
                    :key="String(reference.id)"
                    class="metric-reference"
                    >#{{ reference.id }}</span
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <h4>证据（{{ detail.evidence.length }} 条引用）</h4>
        <p v-if="!detail.evidence.length">证据缺失，无法据此认定质量通过。</p>
        <article
          v-for="reference in detail.evidence"
          :key="String(reference.id)"
          class="evidence-reference"
        >
          <strong>{{ reference.evidenceType }} #{{ reference.id }}</strong>
          <p>业务 ID：{{ reference.businessId || '未提供' }}</p>
          <p>内容 hash：{{ reference.contentHash || '未提供' }}</p>
          <p v-if="reference.summary">{{ reference.summary }}</p>
          <RouterLink
            v-if="
              canReadTask &&
              taskId &&
              ['TASK', 'AGENT_EXECUTION', 'EXECUTION_ARTIFACT', 'TRACE', 'TOKEN_LEDGER'].includes(
                reference.evidenceType,
              )
            "
            :to="taskPath(reference.evidenceType)"
            >查看关联 Task 的{{
              reference.evidenceType === 'TRACE'
                ? '调用审计与 Trace'
                : reference.evidenceType === 'EXECUTION_ARTIFACT'
                  ? '隔离产物'
                  : '执行证据'
            }}</RouterLink
          >
        </article>
        <section v-if="detail.feedback.length" class="result-feedback" aria-label="结果关联反馈">
          <h4>结果关联反馈（跨评价尝试共享）</h4>
          <article v-for="item in detail.feedback" :key="String(item.id)">
            <EvaluationStatusTag domain="feedback" :status="item.label" />
            <p>
              {{ item.sourceType || '来源不可用' }} · #{{ item.id }} · 人工分数
              {{ item.score ?? '未提供' }}
            </p>
            <p v-if="item.comment">{{ item.comment }}</p>
          </article>
        </section>
      </template>
    </DataState>
  </section>
</template>
<script setup lang="ts">
import { metricLabel } from '../display'
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import DataState from '@/shared/components/DataState.vue'
import CopyButton from '@/shared/components/CopyButton.vue'
import { normalizeApiError } from '@/api/errors'
import { getEvaluationResult } from '../api/evaluation-api'
import type { EvaluationResultDetail, EvaluationResultSummary } from '../types'
import EvaluationStatusTag from './EvaluationStatusTag.vue'
import EvaluationMetricValue from './EvaluationMetricValue.vue'
const metricSources: Record<string, string> = { EVALUATOR: '评估器', EXECUTION: '执行事实' }
const metricDirections: Record<string, string> = {
  HIGHER_IS_BETTER: '越高越好',
  LOWER_IS_BETTER: '越低越好',
  NEUTRAL: '中性指标',
}
const props = defineProps<{
  spaceId: string
  runId: string
  caseRunId: string
  attemptId: string
  summary: EvaluationResultSummary
  taskId: string | null
  canReadTask: boolean
  revision: number
  returnPath?: string
}>()
const detail = ref<EvaluationResultDetail | null>(null),
  loading = ref(false),
  error = ref('')
let request: AbortController | undefined
function taskPath(type: string) {
  return {
    path: `/spaces/${props.spaceId}/tasks/${props.taskId}`,
    query: { tab: type === 'TRACE' ? 'trace' : 'evidence', evaluationReturn: props.returnPath },
  }
}
async function load() {
  request?.abort()
  const pending = new AbortController()
  request = pending
  loading.value = true
  error.value = ''
  detail.value = null
  try {
    const value = await getEvaluationResult(props.summary.id, pending.signal)
    if (pending.signal.aborted) return
    if (
      String(value.id) !== String(props.summary.id) ||
      String(value.spaceId) !== props.spaceId ||
      String(value.runId) !== props.runId ||
      String(value.caseAttemptId) !== props.attemptId ||
      String(value.evaluatorVersionId) !== String(props.summary.evaluatorVersionId) ||
      value.evaluationAttemptNo !== props.summary.evaluationAttemptNo
    )
      throw new Error('结果身份或空间归属不一致')
    if (
      value.metrics.some(
        (item) =>
          String(item.spaceId) !== props.spaceId ||
          String(item.runId) !== props.runId ||
          String(item.caseRunId) !== props.caseRunId ||
          String(item.caseAttemptId) !== props.attemptId ||
          String(item.evaluationResultId) !== String(value.id),
      ) ||
      value.feedback.some((item) => String(item.spaceId) !== props.spaceId)
    )
      throw new Error('指标或反馈归属不一致')
    // 诊断正文与 locator 不进入页面状态，不提供任意 URL 跳转。
    detail.value = {
      ...value,
      detailsJson: null,
      evidence: value.evidence.map((item) => ({ ...item, locatorJson: null })),
      metrics: value.metrics.map((item) => ({
        ...item,
        evidence: item.evidence.map((reference) => ({ ...reference, locatorJson: null })),
      })),
      feedback: value.feedback.map((item) => ({ ...item, factsJson: null })),
    }
  } catch (e) {
    if (!pending.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!pending.signal.aborted) loading.value = false
  }
}
watch(
  () => [props.summary.id, props.spaceId, props.runId, props.attemptId],
  () => void load(),
  { immediate: true },
)
watch(
  () => props.revision,
  () => void load(),
)
onBeforeUnmount(() => request?.abort())
</script>
<style scoped>
.result-detail {
  padding: var(--adw-space-4);
  border-bottom: 1px solid var(--adw-border-color);
  min-width: 0;
}
header {
  display: flex;
  align-items: center;
  gap: var(--adw-space-2);
  flex-wrap: wrap;
}
header p {
  flex-basis: 100%;
}
h4 {
  margin: 0;
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
dl {
  display: grid;
  grid-template-columns: 100px minmax(0, 1fr);
  gap: var(--adw-space-2);
  font-size: var(--adw-font-size-caption);
}
dt {
  color: var(--adw-text-secondary);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.metric-table-wrap {
  overflow-x: auto;
  margin-bottom: var(--adw-space-4);
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: var(--adw-font-size-caption);
}
th,
td {
  text-align: left;
  padding: 8px;
  border-bottom: 1px solid var(--adw-border-color);
  vertical-align: top;
  overflow-wrap: anywhere;
}
.metric-reference {
  display: block;
}
.evidence-reference,
.result-feedback article {
  padding: var(--adw-space-3) 0;
  border-bottom: 1px solid var(--adw-border-color);
}
.result-feedback {
  margin-top: var(--adw-space-4);
}
</style>
