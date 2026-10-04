<template>
  <section class="feedback" aria-label="用例人工反馈">
    <h4>用例人工反馈</h4>
    <p>反馈关联用例，跨评价尝试共享；每次提交新增记录，不覆盖已有反馈。</p>
    <article v-for="item in records" :key="String(item.id)">
      <EvaluationStatusTag domain="feedback" :status="item.label" />
      <p>
        {{ item.sourceType || '来源不可用' }} · #{{ item.id }} ·
        {{ item.createdAt || '时间不可用' }}
      </p>
      <p>人工分数 {{ item.score ?? '未提供' }}</p>
      <p v-if="item.comment">{{ item.comment }}</p>
      <small
        >来源 hash：{{ item.sourceHash || '不可用' }} · Task #{{ item.taskId || '未关联' }}</small
      >
    </article>
    <p v-if="!records.length">尚无人工反馈；没有反馈不等于分数为 0。</p>
    <form v-if="canWrite" @submit.prevent="submit">
      <label
        >反馈标签<select v-model="label" aria-label="反馈标签">
          <option value="ACCEPTED">接受</option>
          <option value="REJECTED">拒绝</option>
          <option value="NEEDS_CHANGES">需要修改</option>
          <option value="NOT_APPLICABLE">不适用</option>
        </select></label
      >
      <label
        >人工分数（可选，0～1）<input
          v-model="score"
          aria-label="人工分数"
          type="number"
          min="0"
          max="1"
          step="any"
      /></label>
      <label
        >反馈说明<textarea
          v-model="comment"
          aria-label="反馈说明"
          rows="3"
          maxlength="2000"
          placeholder="请填写判断依据，避免正文或敏感信息。"
        />
      </label>
      <p v-if="error" role="alert">{{ error }}</p>
      <el-button native-type="submit" :loading="busy" :disabled="busy">新增人工反馈</el-button>
    </form>
  </section>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElMessage, ElMessageBox } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import { createEvaluationFeedback } from '../api/evaluation-api'
import EvaluationStatusTag from './EvaluationStatusTag.vue'
import type { EvaluationFeedback, EvaluationFeedbackLabel } from '../types'
const props = defineProps<{
  spaceId: string
  runId: string
  caseRunId: string
  taskId: string | null
  records: EvaluationFeedback[]
  canWrite: boolean
}>()
const emit = defineEmits<{ saved: [] }>()
const label = ref<EvaluationFeedbackLabel>('ACCEPTED'),
  score = ref(''),
  comment = ref(''),
  busy = ref(false),
  error = ref('')
let request: AbortController | undefined,
  generation = 0
async function submit() {
  if (!props.canWrite || busy.value) return
  const numeric = score.value === '' ? null : Number(score.value)
  if (numeric !== null && (!Number.isFinite(numeric) || numeric < 0 || numeric > 1)) {
    error.value = '人工分数必须在 0～1 之间'
    return
  }
  const token = generation
  const payload = {
    spaceId: props.spaceId,
    caseRunId: props.caseRunId,
    taskId: props.taskId,
    label: label.value,
    score: numeric,
    comment: comment.value.trim() || null,
  }
  try {
    await ElMessageBox.confirm(
      '新增不可变人工反馈。重复提交会保留为多条记录，不会覆盖历史反馈。',
      '确认人工反馈',
      { confirmButtonText: '新增反馈', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  if (!props.canWrite || token !== generation || busy.value) return
  request?.abort()
  const pending = new AbortController()
  request = pending
  busy.value = true
  error.value = ''
  try {
    const value = await createEvaluationFeedback(payload, pending.signal)
    if (pending.signal.aborted) return
    if (
      String(value.spaceId) !== props.spaceId ||
      String(value.runId) !== props.runId ||
      String(value.caseRunId) !== props.caseRunId
    )
      throw new Error('反馈目标归属不一致')
    score.value = ''
    comment.value = ''
    ElMessage.success('人工反馈已新增')
    emit('saved')
  } catch (e) {
    if (!pending.signal.aborted)
      error.value = `${normalizeApiError(e).message}。如请求已发送，请先刷新反馈记录再决定重试。`
  } finally {
    if (!pending.signal.aborted) busy.value = false
  }
}
watch(
  () => [props.spaceId, props.runId, props.caseRunId, props.taskId, props.canWrite],
  () => {
    generation++
    request?.abort()
    busy.value = false
    error.value = ''
  },
)
onBeforeUnmount(() => request?.abort())
</script>
<style scoped>
.feedback {
  padding: var(--adw-space-4);
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
article {
  padding: var(--adw-space-3) 0;
  border-bottom: 1px solid var(--adw-border-color);
}
form {
  display: grid;
  gap: var(--adw-space-3);
  margin-top: var(--adw-space-4);
}
label {
  display: grid;
  gap: 6px;
}
input,
select,
textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 8px;
  color: var(--adw-text-primary);
  background: var(--adw-surface);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
}
[role='alert'] {
  color: var(--adw-color-danger);
}
</style>
