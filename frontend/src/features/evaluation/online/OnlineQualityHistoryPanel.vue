<template>
  <section aria-label="原始评价更正链" class="history-panel">
    <h3>反馈历史与当前链尾</h3>
    <p>
      评分按更正顺序保留；只显示自己的自由文本。历史记录不是新的盲态资格，也不代替当前原始证据核验。
    </p>
    <el-button :disabled="busy" :loading="loading" @click="load(1)">核对反馈链</el-button>
    <p v-if="loading" role="status">正在读取当前反馈链，核对完成后可评价。</p>
    <p v-if="error" role="alert">反馈链未知：{{ error }}</p>
    <template v-if="value">
      <p v-if="!value.history.total">首评模式：已核验当前原始目标尚无人工反馈。</p>
      <template v-else>
        <p>
          更正模式：当次链尾 #{{ value.currentTailFeedbackId }}，有效知情状态
          {{ value.currentTailBlindness }}；提交时服务端仍会重新核对。
        </p>
        <el-button
          :disabled="busy || loading"
          @click="$emit('correct', value.currentTailFeedbackId!)"
          >以当前链尾追加更正</el-button
        >
        <p v-if="value.lastOwnFeedback">
          本人最后反馈 #{{ value.lastOwnFeedback.id }} · {{ value.lastOwnFeedback.rubricScore }} / 4
          · {{ value.lastOwnFeedback.label }} · {{ value.lastOwnFeedback.comment || '无自由文本' }}
        </p>
        <ol :start="(value.history.pageNum - 1) * value.history.pageSize + 1">
          <li v-for="item in value.history.records" :key="item.id">
            #{{ item.id }} · {{ item.rubricScore }} / 4 · {{ item.label }} · {{ item.blindness }}
            <span>{{
              item.supersedesFeedbackId ? `更正 #${item.supersedesFeedbackId}` : '首评'
            }}</span>
            <p v-if="item.ownFeedback">
              {{ item.comment || '无自由文本' }} {{ item.reasonCodes.join('、') }}
            </p>
            <p v-else>其他评审的自由文本和原因代码保持隐藏。</p>
          </li>
        </ol>
        <el-pagination
          :current-page="value.history.pageNum"
          :total="value.history.total"
          :page-size="10"
          layout="prev, pager, next"
          :disabled="busy || loading"
          @current-change="load"
        />
      </template>
    </template>
  </section>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElPagination } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import { searchOnlineQualityHistory } from './api'
import { validOnlineId } from './presentation'
import type {
  OnlineQualityHistory,
  OnlineQualityHistoryItem,
  OnlineReviewContext,
  OnlineReviewTarget,
} from './types'
const props = defineProps<{
  spaceId: string
  experimentId: string
  target: OnlineReviewTarget
  context: OnlineReviewContext
  busy: boolean
}>()
const emit = defineEmits<{
  checking: []
  verified: [value: OnlineQualityHistory]
  correct: [id: string]
}>()
const value = ref<OnlineQualityHistory | null>(null),
  loading = ref(false),
  error = ref('')
let controller: AbortController | undefined
function validItem(item: OnlineQualityHistoryItem) {
  return (
    validOnlineId(item.id) &&
    /^[0-9a-f]{64}$/.test(item.sourceHash) &&
    (item.supersedesFeedbackId === null || validOnlineId(item.supersedesFeedbackId)) &&
    Number.isInteger(item.rubricScore) &&
    item.rubricScore >= 0 &&
    item.rubricScore <= 4 &&
    ['ACCEPTED', 'REJECTED', 'NEEDS_CHANGES', 'NOT_APPLICABLE'].includes(item.label) &&
    ['BLINDED', 'NON_BLINDED', 'UNKNOWN'].includes(item.blindness) &&
    ['BLINDED', 'NON_BLINDED', 'UNKNOWN'].includes(item.declaredBlindness) &&
    typeof item.ownFeedback === 'boolean' &&
    typeof item.seriousSafetyEvent === 'boolean' &&
    Array.isArray(item.reasonCodes) &&
    (item.ownFeedback
      ? (item.comment === null || typeof item.comment === 'string') &&
        (item.safetyReason === null || typeof item.safetyReason === 'string') &&
        item.reasonCodes.every((code) => typeof code === 'string')
      : item.comment === null && item.safetyReason === null && item.reasonCodes.length === 0)
  )
}
async function load(pageNum: number) {
  if (props.busy || loading.value) return
  emit('checking')
  value.value = null
  error.value = ''
  loading.value = true
  const current = new AbortController()
  controller = current
  try {
    const result = await searchOnlineQualityHistory(
      props.experimentId,
      props.context.assignmentId,
      props.target,
      pageNum,
      current.signal,
    )
    if (current.signal.aborted) return
    const original = props.context.originalContext,
      text = props.target === 'TEXT',
      page = result.history
    if (
      result.schemaVersion !== 2 ||
      result.spaceId !== props.spaceId ||
      result.experimentId !== props.experimentId ||
      result.assignmentId !== props.context.assignmentId ||
      result.taskId !== original.taskId ||
      result.executionId !== original.executionId ||
      result.originalEvidenceType !== (text ? 'ORIGINAL_TEXT' : 'ORIGINAL_CHANGE_SET') ||
      result.originalEvidenceId !== (text ? original.originalEvidenceId : original.originalSetId) ||
      result.originalEvidenceHash !==
        (text ? original.originalEvidenceHash : original.originalSetHash) ||
      !Number.isSafeInteger(page.total) ||
      page.total < 0 ||
      page.pageNum !== pageNum ||
      page.pageSize !== 10 ||
      page.records.length !== Math.min(10, Math.max(0, page.total - (pageNum - 1) * 10)) ||
      new Set(page.records.map((item) => item.id)).size !== page.records.length ||
      page.records.some((item) => !validItem(item)) ||
      (result.lastOwnFeedback !== null &&
        (!result.lastOwnFeedback.ownFeedback || !validItem(result.lastOwnFeedback))) ||
      (page.total === 0
        ? result.currentTailFeedbackId !== null ||
          result.currentTailBlindness !== null ||
          result.lastOwnFeedback !== null
        : result.currentTailFeedbackId === null ||
          !validOnlineId(result.currentTailFeedbackId) ||
          !['BLINDED', 'NON_BLINDED', 'UNKNOWN'].includes(result.currentTailBlindness ?? ''))
    )
      throw new Error('反馈分页或原始身份不可核验')
    if (
      page.records.some((item, index) =>
        index === 0
          ? pageNum === 1 && item.supersedesFeedbackId !== null
          : item.supersedesFeedbackId !== page.records[index - 1]!.id,
      ) ||
      (page.records.length &&
        (pageNum - 1) * 10 + page.records.length === page.total &&
        page.records.at(-1)!.id !== result.currentTailFeedbackId)
    )
      throw new Error('反馈更正顺序或链尾不一致')
    value.value = result
    emit('verified', result)
  } catch (failure) {
    if (!current.signal.aborted) error.value = normalizeApiError(failure).message
  } finally {
    if (controller === current) loading.value = false
  }
}
watch(
  () =>
    JSON.stringify([
      props.spaceId,
      props.experimentId,
      props.target,
      props.context.assignmentId,
      props.context.originalContext.taskId,
      props.context.originalContext.originalEvidenceHash,
      props.context.originalContext.originalSetHash,
    ]),
  () => {
    controller?.abort()
    value.value = null
    error.value = ''
    loading.value = false
    void load(1)
  },
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>
<style scoped>
.history-panel {
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  padding: 16px;
}
.history-panel p,
li {
  overflow-wrap: anywhere;
}
li {
  margin: 12px 0;
}
li span {
  display: block;
  color: var(--adw-text-tertiary);
}
</style>
