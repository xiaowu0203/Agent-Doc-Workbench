<template>
  <section class="case-panel" aria-label="执行尝试与结果">
    <header>
      <h3>CaseRun #{{ caseRun.id }}</h3>
      <p>
        来源用例版本 #{{ caseRun.testCaseVersionId || '不可用' }}；默认查看当前尝试，历史尝试只读。
      </p>
    </header>
    <DataState
      :loading="loading && !selected"
      :error="error"
      :empty="!page.records.length"
      empty-text="尚未产生执行尝试。"
      @retry="load(page.pageNum)"
    >
      <div class="case-panel__columns">
        <div class="attempt-column">
          <section class="attempts" aria-label="Attempt 历史">
            <button
              v-for="attempt in page.records"
              :key="String(attempt.id)"
              class="attempt"
              :aria-pressed="String(attempt.id) === String(selected?.id)"
              @click="selectAttempt(attempt)"
            >
              <strong
                >Attempt {{ attempt.attemptNo }}
                {{ attempt.currentCaseAttempt ? '（当前）' : '（历史，只读）' }}</strong
              ><EvaluationStatusTag domain="attempt" :status="attempt.status" /><small
                >#{{ attempt.id }}</small
              ><small>{{ attempt.failureCode || '未提供失败码' }}</small>
            </button>
            <el-pagination
              v-if="page.total > PAGE_SIZE"
              size="small"
              layout="prev, pager, next"
              :total="page.total"
              :page-size="PAGE_SIZE"
              :current-page="page.pageNum"
              @current-change="load"
            />
          </section>
          <div v-if="selected" class="attempt-detail">
            <div class="attempt-facts">
              <h4>
                Attempt {{ selected.attemptNo }}
                {{ selected.currentCaseAttempt ? '（当前）' : '（历史，只读）' }}
              </h4>
              <p>
                开始 {{ selected.startedAt || '尚未开始' }} · 结束
                {{ selected.finishedAt || '尚未结束' }}
              </p>
              <p v-if="selected.failureCode">
                失败阶段 {{ selected.failureStage || '不可用' }} · {{ selected.failureCode }}
              </p>
              <RouterLink
                v-if="canReadTask && selected.executionTaskId"
                :to="taskPath(selected.executionTaskId)"
                >查看执行 Task #{{ selected.executionTaskId }}、Execution、Trace
                与隔离产物</RouterLink
              >
              <p v-else>
                执行 Task #{{ selected.executionTaskId || '尚未生成'
                }}{{ selected.executionTaskId && !canReadTask ? '（需要任务读取权限）' : '' }}
              </p>
              <div class="attempt-actions">
                <el-button v-if="canReplay" :disabled="busy" @click="$emit('retryReplay')"
                  >重放模型与工具</el-button
                >
                <el-button
                  v-if="canEvaluate"
                  :disabled="busy"
                  @click="$emit('retryEvaluation', String(selected.id))"
                  >仅重试评估器</el-button
                >
              </div>
              <p v-if="canReplay || canEvaluate" class="attempt-warning">
                重放创建新 Task 并消耗 Token；仅重试评估器复用执行证据，不重跑模型与工具。
              </p>
            </div>
            <section class="result-history" aria-label="Result 历史">
              <h4>评估结果与评价尝试</h4>
              <p v-if="!selected.results.length">
                尚未产生 Result。{{
                  selected.status === 'REPLAY_FAILED'
                    ? '回放失败，缺少评估结果与指标。'
                    : selected.status === 'EVALUATOR_FAILED'
                      ? '评估器执行或准入失败，没有可用结果、指标与证据。请检查冻结评估器版本与运行准入。'
                      : selected.status === 'CANCELED'
                        ? '本次执行已取消，未产生评估结果。'
                        : '请检查执行状态或等待评估完成。'
                }}
              </p>
              <button
                v-for="result in visibleResults"
                :key="String(result.id)"
                class="result-row"
                :aria-pressed="String(result.id) === String(selectedResult?.id)"
                @click="chooseResult(result)"
              >
                <span
                  ><strong
                    >{{ result.evaluatorName || '评估器名称不可用' }} · v{{
                      result.evaluatorVersionNo ?? '不可用'
                    }}</strong
                  ><small
                    >Result #{{ result.id }} · 评价尝试 {{ result.evaluationAttemptNo }}
                    {{
                      result.currentEvaluationResultAttempt ? '（当前评价）' : '（历史，只读）'
                    }}</small
                  ></span
                ><EvaluationStatusTag domain="result" :status="result.status" />
              </button>
              <el-pagination
                v-if="selected.results.length > PAGE_SIZE"
                size="small"
                layout="prev, pager, next"
                :total="selected.results.length"
                :page-size="PAGE_SIZE"
                :current-page="resultPage"
                @current-change="resultPage = $event"
              />
            </section>
          </div>
        </div>
        <aside v-if="selected" class="result-column">
          <p v-if="loading" class="refresh-status" role="status">正在更新尝试与结果</p>
          <EvaluationResultPanel
            v-if="selectedResult"
            :key="String(selectedResult.id)"
            :space-id="spaceId"
            :run-id="runId"
            :case-run-id="String(caseRun.id)"
            :attempt-id="String(selected.id)"
            :summary="selectedResult"
            :task-id="selected.executionTaskId ? String(selected.executionTaskId) : null"
            :can-read-task="canReadTask"
            :revision="revision"
          />
          <EvaluationFeedbackPanel
            :space-id="spaceId"
            :run-id="runId"
            :case-run-id="String(caseRun.id)"
            :task-id="selected.executionTaskId ? String(selected.executionTaskId) : null"
            :records="caseRun.feedback"
            :can-write="
              canRun &&
              selected.currentCaseAttempt &&
              (!selectedResult || selectedResult.currentEvaluationResultAttempt) &&
              !busy
            "
            @saved="$emit('feedbackSaved')"
          />
        </aside>
      </div>
    </DataState>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElButton, ElPagination } from 'element-plus'
import DataState from '@/shared/components/DataState.vue'
import { normalizeApiError } from '@/api/errors'
import { searchCaseAttempts } from '../api/evaluation-api'
import type {
  EvaluationCaseAttemptHistory,
  EvaluationCaseRun,
  EvaluationPage,
  EvaluationResultSummary,
} from '../types'
import { activeAttempt } from '../run'
import EvaluationStatusTag from './EvaluationStatusTag.vue'
import EvaluationResultPanel from './EvaluationResultPanel.vue'
import EvaluationFeedbackPanel from './EvaluationFeedbackPanel.vue'
const props = defineProps<{
  spaceId: string
  runId: string
  caseRun: EvaluationCaseRun
  revision: number
  canRun: boolean
  canReadTask: boolean
  cancelRequested: boolean
  busy: boolean
  initialAttemptId?: string
  initialResultId?: string
}>()
defineEmits<{ retryReplay: []; retryEvaluation: [attemptId: string]; feedbackSaved: [] }>()
const PAGE_SIZE = 10
const page = ref<EvaluationPage<EvaluationCaseAttemptHistory>>({
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: PAGE_SIZE,
})
const selected = ref<EvaluationCaseAttemptHistory | null>(null),
  selectedResult = ref<EvaluationResultSummary | null>(null),
  resultPage = ref(1)
const loading = ref(false),
  error = ref('')
let request: AbortController | undefined,
  followCurrent = !props.initialAttemptId
const canReplay = computed(
  () =>
    props.canRun &&
    selected.value?.currentCaseAttempt &&
    (!selectedResult.value || selectedResult.value.currentEvaluationResultAttempt) &&
    !props.cancelRequested &&
    !activeAttempt(props.caseRun.status),
)
const canEvaluate = computed(
  () =>
    props.canRun &&
    selected.value?.currentCaseAttempt &&
    (!selectedResult.value || selectedResult.value.currentEvaluationResultAttempt) &&
    !!selected.value.executionTaskId &&
    ['COMPLETED', 'EVALUATOR_FAILED'].includes(props.caseRun.status),
)
const visibleResults = computed(
  () =>
    selected.value?.results.slice(
      (resultPage.value - 1) * PAGE_SIZE,
      resultPage.value * PAGE_SIZE,
    ) || [],
)
function taskPath(id: string | number) {
  return `/spaces/${props.spaceId}/tasks/${id}?tab=evidence`
}
function chooseResult(value: EvaluationResultSummary) {
  selectedResult.value = value
}
function selectAttempt(value: EvaluationCaseAttemptHistory) {
  followCurrent = value.currentCaseAttempt
  const previous = selectedResult.value
  selected.value = value
  selectedResult.value =
    value.results.find(
      (item) =>
        previous?.currentEvaluationResultAttempt &&
        item.currentEvaluationResultAttempt &&
        String(item.evaluatorVersionId) === String(previous.evaluatorVersionId),
    ) ||
    value.results.find(
      (item) =>
        !previous?.currentEvaluationResultAttempt && String(item.id) === String(previous?.id),
    ) ||
    value.results.find((item) => item.currentEvaluationResultAttempt) ||
    value.results[0] ||
    null
  resultPage.value = Math.max(
    1,
    Math.ceil(
      (value.results.findIndex((item) => String(item.id) === String(selectedResult.value?.id)) +
        1) /
        PAGE_SIZE,
    ),
  )
}
async function load(pageNum: number, locateDeepLink = false) {
  request?.abort()
  const pending = new AbortController()
  request = pending
  loading.value = true
  error.value = ''
  try {
    let value = await searchCaseAttempts(
      props.caseRun.id,
      { pageNum, pageSize: PAGE_SIZE },
      pending.signal,
    )
    if (pending.signal.aborted) return
    if (locateDeepLink && props.initialAttemptId) {
      while (
        !value.records.some((item) => String(item.id) === props.initialAttemptId) &&
        value.pageNum * PAGE_SIZE < value.total
      ) {
        value = await searchCaseAttempts(
          props.caseRun.id,
          { pageNum: value.pageNum + 1, pageSize: PAGE_SIZE },
          pending.signal,
        )
        if (pending.signal.aborted) return
      }
      if (!value.records.some((item) => String(item.id) === props.initialAttemptId))
        throw new Error('执行尝试不属于当前用例')
    }
    if (
      value.records.some(
        (item) =>
          item.currentCaseAttempt !== (String(item.id) === String(props.caseRun.currentAttemptId)),
      )
    )
      throw new Error('当前执行尝试身份不一致，请刷新运行')
    page.value = value
    const choice =
      value.records.find((item) => locateDeepLink && String(item.id) === props.initialAttemptId) ||
      value.records.find(
        (item) => !followCurrent && String(item.id) === String(selected.value?.id),
      ) ||
      value.records.find((item) => item.currentCaseAttempt) ||
      value.records[0]
    if (choice) {
      selectAttempt(choice)
      if (locateDeepLink && props.initialResultId) {
        const result = choice.results.find((item) => String(item.id) === props.initialResultId)
        if (!result) throw new Error('评价结果不属于当前执行尝试')
        chooseResult(result)
        resultPage.value = Math.ceil((choice.results.indexOf(result) + 1) / PAGE_SIZE)
      }
    } else {
      selected.value = null
      selectedResult.value = null
    }
  } catch (e) {
    if (!pending.signal.aborted) {
      error.value = normalizeApiError(e).message
      selected.value = null
      selectedResult.value = null
    }
  } finally {
    if (!pending.signal.aborted) loading.value = false
  }
}
watch(
  () => [
    props.spaceId,
    props.runId,
    props.caseRun.id,
    props.initialAttemptId,
    props.initialResultId,
  ],
  () => {
    selected.value = null
    selectedResult.value = null
    followCurrent = !props.initialAttemptId
    void load(1, true)
  },
  { immediate: true },
)
watch(
  () => props.revision,
  () => {
    void load(followCurrent ? 1 : page.value.pageNum)
  },
)
onBeforeUnmount(() => request?.abort())
</script>
<style scoped>
.case-panel {
  min-width: 0;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  overflow: hidden;
}
header {
  padding: var(--adw-space-4);
  border-bottom: 1px solid var(--adw-border-color);
}
h3,
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
.case-panel__columns {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  align-items: start;
}
.attempt-column {
  min-width: 0;
}
.result-column {
  min-width: 0;
  border-left: 1px solid var(--adw-border-color);
}
.attempts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}
.attempt,
.result-row {
  width: 100%;
  text-align: left;
  display: grid;
  gap: 8px;
  padding: var(--adw-space-3);
  border: 0;
  border-bottom: 1px solid var(--adw-border-color);
  background: transparent;
  color: var(--adw-text-primary);
  cursor: pointer;
  overflow-wrap: anywhere;
}
button[aria-pressed='true'] {
  background: var(--adw-color-primary-soft);
  border-left: 3px solid var(--adw-color-primary);
}
.attempt-detail {
  min-width: 0;
}
.attempt-facts,
.result-history {
  padding: var(--adw-space-4);
  border-bottom: 1px solid var(--adw-border-color);
}
.result-row {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  margin-top: var(--adw-space-2);
}
.attempt-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--adw-space-2);
  margin-top: var(--adw-space-3);
}
.attempt-actions .el-button {
  margin-left: 0;
}
.attempt-warning {
  border-left: 3px solid var(--adw-color-warning);
  padding: var(--adw-space-3);
  background: var(--adw-color-warning-soft);
  font-size: var(--adw-font-size-caption);
}
button:focus-visible {
  outline: 2px solid var(--adw-color-primary);
  outline-offset: -2px;
}
@media (max-width: 700px) {
  .case-panel__columns {
    grid-template-columns: minmax(0, 1fr);
  }
  .attempts {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
    border-right: 0;
  }
  .result-column {
    border-left: 0;
    border-top: 1px solid var(--adw-border-color);
  }
}
</style>
