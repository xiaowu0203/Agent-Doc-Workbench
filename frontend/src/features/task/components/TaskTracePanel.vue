<template>
  <section class="trace-panel" aria-label="调用审计与 Trace">
    <div class="trace-heading">
      <div>
        <h2>调用审计与 Trace</h2>
        <p>技术链路来自 OTel；业务终态、Token 和冻结快照以业务记录为准。</p>
      </div>
      <div class="trace-actions">
        <el-button :loading="loading" @click="loadTrace">刷新 Trace</el-button>
        <a v-if="jaegerUrl" :href="jaegerUrl" target="_blank" rel="noopener noreferrer"
          >在 Jaeger 中打开</a
        >
      </div>
    </div>

    <div class="trace-layout">
      <div class="trace-main">
        <DataState :loading="loading && !trace" :error="error" @retry="loadTrace">
          <template v-if="trace">
            <article class="surface-card trace-summary">
              <div>
                <span>Trace 可用性</span
                ><strong>{{ trace.availabilityCode === 'AVAILABLE' ? '已关联' : '不可用' }}</strong
                ><code>{{ trace.traceId || '未关联 Trace' }}</code>
              </div>
              <div>
                <span>端到端遥测耗时</span
                ><strong>{{ formatTraceDuration(trace.durationMicros) }}</strong>
              </div>
              <div>
                <span>可见 Span 总数</span><strong>{{ trace.spanCount ?? '不可用' }}</strong>
              </div>
              <div>
                <span>错误 / 取消 / 重试</span
                ><strong
                  >{{ trace.errorCount ?? '不可用' }} / {{ trace.canceledCount ?? '不可用' }} /
                  {{ trace.retryCount ?? '不可用' }}</strong
                >
              </div>
            </article>
            <el-alert
              v-if="trace.availabilityCode !== 'AVAILABLE'"
              type="info"
              :closable="false"
              :title="availabilityMessage"
              :description="trace.availabilityCode"
            />
            <el-alert
              v-if="trace.partial || trace.truncated"
              type="warning"
              :closable="false"
              :title="
                trace.truncated
                  ? 'Trace 已截断，当前视图仅包含部分节点'
                  : 'Trace 投影不完整，不能断言上下文完整'
              "
            />
            <el-alert
              v-if="traceIdentityMismatch"
              type="warning"
              :closable="false"
              title="Task 与 Execution 的 Trace 身份不同，请结合业务身份排查上下文断链"
            />

            <article
              v-if="trace.availabilityCode === 'AVAILABLE'"
              class="surface-card waterfall-card"
            >
              <div class="trace-heading">
                <div>
                  <h3>跨服务调用瀑布</h3>
                  <p>默认折叠技术明细，错误、取消、重试与关键路径保持展开。</p>
                </div>
                <el-button @click="showTechnical = !showTechnical">{{
                  showTechnical ? '折叠技术 Span' : `展开技术 Span (${foldedCount})`
                }}</el-button>
              </div>
              <div class="trace-filters" aria-label="Trace 节点筛选">
                <button
                  v-for="option in filters"
                  :key="option.key"
                  type="button"
                  :aria-pressed="filter === option.key"
                  @click="filter = option.key"
                >
                  {{ option.label }}
                </button>
              </div>
              <div class="waterfall-scroll">
                <div class="waterfall-axis">
                  <span>调用树 / 服务</span>
                  <div>
                    <span v-for="tick in [0, 1, 2, 3, 4]" :key="tick">{{
                      formatTraceDuration(Math.round((range * tick) / 4))
                    }}</span>
                  </div>
                  <span>耗时</span>
                </div>
                <button
                  v-for="row in visibleRows"
                  :key="row.span.spanId"
                  type="button"
                  class="span-row"
                  :class="{
                    'span-row--selected': selectedSpan?.spanId === row.span.spanId,
                    'span-row--error': row.span.status === 'ERROR',
                    'span-row--critical': critical.has(row.span.spanId),
                  }"
                  :aria-pressed="selectedSpan?.spanId === row.span.spanId"
                  :aria-label="`${row.span.service} · ${row.span.name} · ${row.span.status}`"
                  @click="selectedId = row.span.spanId"
                >
                  <span
                    class="span-name"
                    :style="{ paddingLeft: `${Math.min(row.depth, 6) * 12}px` }"
                    ><strong :title="row.span.name">{{ row.span.name }}</strong
                    ><small
                      >{{ row.span.service
                      }}<template v-if="row.missingParent"> · 父关联缺失</template
                      ><template v-if="row.span.status !== 'OK'"> · {{ row.span.status }}</template
                      ><template v-if="spanHasRetry(row.span)"> · 重试</template></small
                    ></span
                  >
                  <span class="span-track"
                    ><span class="span-bar" :style="barStyle(row.span)"></span
                  ></span>
                  <span class="span-duration">{{
                    formatTraceDuration(row.span.durationMicros)
                  }}</span>
                </button>
              </div>
              <p v-if="!visibleRows.length" class="trace-empty">当前筛选没有可展示的节点。</p>
              <p class="trace-note">
                关键路径按当前投影的最长调用链估计，扣除嵌套区间重叠；部分或截断数据不代表完整链路。点击节点查看受控属性。
              </p>
              <div class="service-summary">
                <h3>服务分布</h3>
                <p>累计 Span 耗时可能重叠，不等于服务独占耗时。</p>
                <ul>
                  <li v-for="service in trace.services" :key="service.service">
                    <strong>{{ service.service }}</strong
                    ><span
                      >{{ service.spanCount }} Span · {{ service.errorCount }} 错误 ·
                      {{ formatTraceDuration(service.totalDurationMicros) }}</span
                    >
                  </li>
                </ul>
              </div>
            </article>
          </template>
        </DataState>
      </div>
      <aside class="trace-side">
        <article class="surface-card span-detail">
          <h3>选中 Span</h3>
          <template v-if="selectedSpan">
            <el-tag
              :type="
                selectedSpan.status === 'ERROR'
                  ? 'danger'
                  : selectedSpan.status === 'OK'
                    ? 'success'
                    : 'info'
              "
              >{{ selectedSpan.status }}</el-tag
            >
            <strong>{{ selectedSpan.name }}</strong>
            <dl>
              <dt>Span ID</dt>
              <dd>{{ selectedSpan.spanId }}</dd>
              <dt>父 Span</dt>
              <dd>{{ selectedSpan.parentSpanId || '根节点 / 父关联不可用' }}</dd>
              <dt>服务</dt>
              <dd>{{ selectedSpan.service }}</dd>
              <dt>类型</dt>
              <dd>{{ selectedSpan.kind }}</dd>
              <dt>耗时</dt>
              <dd>{{ formatTraceDuration(selectedSpan.durationMicros) }}</dd>
              <template v-for="attribute in selectedAttributes" :key="attribute.key"
                ><dt>{{ attribute.key }}</dt>
                <dd>{{ attribute.value }}</dd></template
              >
            </dl>
          </template>
          <p v-else>暂无可选节点，业务事实仍可查看。</p>
        </article>
        <article class="surface-card trace-facts">
          <h3>业务事实对照</h3>
          <dl>
            <dt>Task / Run ID</dt>
            <dd>{{ detail.task.id }}</dd>
            <dt>Task 终态</dt>
            <dd>{{ detail.task.status }}</dd>
            <dt>Execution ID</dt>
            <dd>{{ detail.execution?.id ?? '尚未创建' }}</dd>
            <dt>Execution 状态</dt>
            <dd>{{ detail.execution?.status ?? '不可用' }}</dd>
            <dt>Token 权威记录</dt>
            <dd>
              {{ detail.task.tokensUsed ?? '不可用'
              }}<span v-if="detail.tokensEstimated">（含估算）</span>
            </dd>
            <dt>模型 / 工具审计</dt>
            <dd>
              {{ detail.execution?.modelCalls.length ?? '不可用' }} /
              {{ detail.execution?.toolCalls.length ?? '不可用' }}
            </dd>
            <dt>执行快照 schema</dt>
            <dd>{{ detail.execution?.executionSnapshotSchemaVersion ?? '不可用' }}</dd>
            <dt>执行快照 hash</dt>
            <dd>{{ detail.execution?.executionSnapshotHash ?? '不可用' }}</dd>
            <dt>Task Trace</dt>
            <dd>{{ detail.task.traceId || '未关联' }}</dd>
            <dt>Execution Trace / Span</dt>
            <dd>
              {{ detail.execution?.traceId || '未关联' }} /
              {{ detail.execution?.spanId || '未关联' }}
            </dd>
          </dl>
          <p class="trace-note">
            遥测故障、保留期或采样不会改变这些业务记录。此处不展示原始 SQL、URL、参数、正文或凭证。
          </p>
        </article>
      </aside>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ElAlert, ElButton, ElTag } from 'element-plus'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { normalizeApiError } from '@/api/errors'
import { getTaskTrace } from '@/features/task/api/task-api'
import type { TaskTraceView, TraceSpan } from '@/features/task/engineering-types'
import type { TaskExecutionDetail } from '@/features/task/types'
import {
  criticalTracePath,
  formatTraceDuration,
  jaegerTraceUrl,
  micros,
  spanHasRetry,
  TRACE_ATTRIBUTE_KEYS,
  traceRows,
} from '@/features/task/trace-view'
import DataState from '@/shared/components/DataState.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const props = defineProps<{ detail: TaskExecutionDetail }>()
const workspace = useWorkspaceStore()
const trace = ref<TaskTraceView | null>(null),
  loading = ref(false),
  error = ref(''),
  selectedId = ref(''),
  showTechnical = ref(false)
const filter = ref('ALL')
let controller: AbortController | null = null
const filters = [
  { key: 'ALL', label: '全部' },
  { key: 'ERROR', label: '错误与取消' },
  { key: 'RETRY', label: '重试' },
  { key: 'CRITICAL', label: '关键路径' },
]
const rows = computed(() => traceRows(trace.value?.spans ?? [])),
  critical = computed(() => criticalTracePath(rows.value))
const technicalCategories = new Set(['DATABASE', 'REDIS', 'HTTP', 'TECHNICAL'])
const important = (span: TraceSpan) =>
  !technicalCategories.has(span.category) ||
  span.status === 'ERROR' ||
  span.status === 'CANCELED' ||
  spanHasRetry(span) ||
  critical.value.has(span.spanId)
const foldedCount = computed(() => rows.value.filter((row) => !important(row.span)).length)
const visibleRows = computed(() =>
  rows.value.filter(({ span }) => {
    if (filter.value === 'ERROR') return span.status === 'ERROR' || span.status === 'CANCELED'
    if (filter.value === 'RETRY') return spanHasRetry(span)
    if (filter.value === 'CRITICAL') return critical.value.has(span.spanId)
    return showTechnical.value || important(span)
  }),
)
const selectedSpan = computed(
  () => trace.value?.spans.find((span) => span.spanId === selectedId.value) ?? null,
)
const selectedAttributes = computed(
  () => selectedSpan.value?.attributes.filter((item) => TRACE_ATTRIBUTE_KEYS.has(item.key)) ?? [],
)
const origin = computed(
  () =>
    micros(trace.value?.startTimeMicros ?? null) ??
    (rows.value.length
      ? Math.min(...rows.value.map(({ span }) => Number(span.startTimeMicros)))
      : 0),
)
const range = computed(() =>
  Math.max(
    1,
    micros(trace.value?.durationMicros ?? null) ??
      Math.max(
        ...rows.value.map(
          ({ span }) => Number(span.startTimeMicros) + Number(span.durationMicros) - origin.value,
        ),
        0,
      ),
  ),
)
const traceIdentityMismatch = computed(
  () =>
    props.detail.task.traceId &&
    props.detail.execution?.traceId &&
    props.detail.task.traceId !== props.detail.execution.traceId,
)
const jaegerUrl = computed(() =>
  jaegerTraceUrl(
    import.meta.env.VITE_JAEGER_UI_URL,
    import.meta.env.VITE_JAEGER_UI_ACCESS_CONTROL_CONFIRMED === 'true',
    String(workspace.currentSpaceId) === String(props.detail.task.spaceId) &&
      workspace.hasPermission(SPACE_PERMISSIONS.TASK_READ),
    trace.value?.traceId ?? null,
  ),
)
const availabilityMessages = {
  NO_TRACE: '任务没有关联 Trace，可继续查看业务审计。',
  INVALID_TRACE_ID: '关联 Trace ID 无法安全查询。',
  NOT_CONFIGURED: '遥测查询后端尚未配置。',
  NOT_FOUND_OR_NOT_SAMPLED: '未查询到 Trace，可能与采样或保留期有关。',
  RETENTION_WINDOW_ELAPSED: '关联已超过 14 天保留窗口，业务记录仍可查看。',
  BACKEND_UNAVAILABLE: '遥测后端暂不可用，可刷新重试。',
  PAYLOAD_INVALID: '遥测结构无法安全解释。',
  PAYLOAD_TOO_LARGE: '遥测返回超出受控读取上限。',
  UNRELATED_TRACE: '无法安全确认 Trace 与此任务的关联。',
  AVAILABLE: 'Trace 可用。',
}
const availabilityMessage = computed(() =>
  trace.value
    ? (availabilityMessages[trace.value.availabilityCode] ?? trace.value.availabilityCode)
    : '',
)
function barStyle(span: TraceSpan) {
  const left = Math.min(
    100,
    Math.max(0, ((Number(span.startTimeMicros) - origin.value) / range.value) * 100),
  )
  return {
    left: `${left}%`,
    width: `${Math.min(100 - left, Math.max(0.6, (Number(span.durationMicros) / range.value) * 100))}%`,
  }
}
async function loadTrace() {
  controller?.abort()
  const request = new AbortController()
  controller = request
  loading.value = true
  error.value = ''
  const task = props.detail.task
  try {
    const response = await getTaskTrace(task.id, request.signal)
    if (request.signal.aborted) return
    if (
      String(response.taskId) !== String(task.id) ||
      String(response.spaceId) !== String(task.spaceId)
    )
      throw new Error('Trace 关联身份不一致')
    trace.value = response
    if (!response.spans.some((span) => span.spanId === selectedId.value))
      selectedId.value =
        response.spans.find((span) => span.category === 'AGENT')?.spanId ??
        response.spans[0]?.spanId ??
        ''
  } catch (e) {
    if (!request.signal.aborted) {
      trace.value = null
      selectedId.value = ''
      error.value = normalizeApiError(e).message
    }
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}
watch(
  [() => props.detail.task.id, () => props.detail.task.spaceId, () => props.detail.task.status],
  () => {
    trace.value = null
    selectedId.value = ''
    showTechnical.value = false
    filter.value = 'ALL'
    void loadTrace()
  },
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>

<style scoped>
.trace-panel,
.trace-main,
.trace-side {
  display: grid;
  gap: var(--adw-space-4);
  min-width: 0;
}
.trace-heading {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: var(--adw-space-4);
}
.trace-heading h2,
h3 {
  margin: 0;
  font-size: var(--adw-font-size-subtitle);
}
.trace-heading p,
.trace-note,
.service-summary p {
  margin: 6px 0 0;
  color: var(--adw-text-secondary);
  font-size: var(--adw-font-size-caption);
  line-height: 1.7;
}
.trace-actions {
  display: flex;
  align-items: center;
  gap: var(--adw-space-3);
  flex-shrink: 0;
}
.trace-actions a {
  color: var(--adw-color-primary);
}
.trace-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 310px;
  gap: var(--adw-space-4);
  align-items: start;
}
.trace-summary {
  display: grid;
  grid-template-columns: 1.5fr 1fr 1fr 1.2fr;
  padding: var(--adw-space-5);
  gap: var(--adw-space-4);
  border-left: 3px solid var(--adw-color-primary);
}
.trace-summary span {
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.trace-summary strong {
  display: block;
  margin-top: 8px;
  font-size: 20px;
}
.trace-summary code {
  display: block;
  margin-top: 6px;
  overflow-wrap: anywhere;
  font-size: 11px;
}
.waterfall-card,
.span-detail,
.trace-facts {
  padding: var(--adw-space-5);
}
.trace-filters {
  display: flex;
  gap: var(--adw-space-2);
  flex-wrap: wrap;
  margin: var(--adw-space-4) 0;
}
.trace-filters button {
  border: 0;
  border-radius: var(--adw-radius-sm);
  padding: 6px 10px;
  background: var(--adw-surface-muted);
  color: var(--adw-text-secondary);
  cursor: pointer;
  font: inherit;
}
.trace-filters [aria-pressed='true'] {
  background: var(--adw-color-primary-soft);
  color: var(--adw-color-primary);
}
.waterfall-scroll {
  overflow-x: auto;
}
.waterfall-axis,
.span-row {
  display: grid;
  grid-template-columns: 220px minmax(180px, 1fr) 80px;
  gap: 12px;
  min-width: 520px;
  align-items: center;
}
.waterfall-axis {
  padding: 12px 0;
  font-size: 11px;
  color: var(--adw-text-tertiary);
}
.waterfall-axis div {
  display: flex;
  justify-content: space-between;
}
.span-row {
  width: 100%;
  padding: 9px 0;
  border: 0;
  border-top: 1px solid var(--adw-border-color-light);
  background: transparent;
  color: var(--adw-text-primary);
  text-align: left;
  cursor: pointer;
}
.span-row--selected {
  background: var(--adw-color-primary-soft);
}
.span-name {
  min-width: 0;
}
.span-name strong {
  display: block;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
}
.span-name small {
  display: block;
  margin-top: 4px;
  color: var(--adw-text-secondary);
  font-size: 10px;
  overflow-wrap: anywhere;
}
.span-track {
  position: relative;
  height: 16px;
  background: repeating-linear-gradient(
    to right,
    var(--adw-border-color-light) 0 1px,
    transparent 1px 25%
  );
}
.span-bar {
  position: absolute;
  height: 10px;
  top: 3px;
  border-radius: 3px;
  background: var(--adw-color-primary);
}
.span-row--error .span-bar {
  background: var(--adw-color-danger);
}
.span-row--critical .span-bar {
  outline: 1px solid var(--adw-text-primary);
  outline-offset: 2px;
}
.span-duration {
  font-size: 11px;
  text-align: right;
}
.span-detail > strong {
  display: block;
  margin: 12px 0;
  overflow-wrap: anywhere;
}
.span-detail .el-tag {
  margin-top: 12px;
}
dl {
  display: grid;
  grid-template-columns: minmax(90px, 1fr) minmax(0, 1.5fr);
  gap: 10px;
  margin-bottom: 0;
  font-size: 12px;
}
dt {
  color: var(--adw-text-secondary);
  overflow-wrap: anywhere;
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.service-summary {
  margin-top: var(--adw-space-5);
  padding-top: var(--adw-space-4);
  border-top: 1px solid var(--adw-border-color-light);
}
.service-summary h3 {
  font-size: 14px;
}
.service-summary ul {
  padding: 0;
  list-style: none;
}
.service-summary li {
  display: flex;
  justify-content: space-between;
  gap: 10px;
  padding: 6px 0;
  font-size: 12px;
}
.service-summary li span {
  color: var(--adw-text-secondary);
}
.trace-empty {
  color: var(--adw-text-secondary);
  text-align: center;
}
button:focus-visible {
  outline: 2px solid var(--adw-color-primary);
  outline-offset: -2px;
}
@media (max-width: 1150px) {
  .trace-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .trace-side {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 720px) {
  .trace-summary,
  .trace-side {
    grid-template-columns: minmax(0, 1fr);
  }
  .trace-heading {
    flex-direction: column;
  }
}
</style>
