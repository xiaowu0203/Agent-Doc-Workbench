<template>
  <el-tag :type="presentation.type" :title="status || undefined" :aria-label="accessibleLabel">
    {{ presentation.label }}
  </el-tag>
</template>

<script setup lang="ts">
import { ElTag } from 'element-plus'
import { computed } from 'vue'

const props = defineProps<{
  domain: 'version' | 'run' | 'attempt' | 'result' | 'experiment' | 'decision' | 'feedback'
  status: string | null
}>()

type TagType = 'primary' | 'success' | 'warning' | 'danger' | 'info'
interface StatusPresentation {
  label: string
  type: TagType
}

const states: Record<string, StatusPresentation> = {
  CREATED: { label: '已创建', type: 'info' },
  DISPATCHING: { label: '派发中', type: 'primary' },
  STARTING: { label: '启动中', type: 'primary' },
  RUNNING: { label: '运行中', type: 'primary' },
  REPLAY_CREATED: { label: '回放已创建', type: 'info' },
  REPLAY_RUNNING: { label: '回放中', type: 'primary' },
  EVALUATING: { label: '评估中', type: 'primary' },
  PAUSED: { label: '已暂停', type: 'warning' },
  CANCEL_PENDING: { label: '取消中', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  COMPLETED_WITH_ERRORS: { label: '完成但有错误', type: 'warning' },
  CANCELED: { label: '已取消', type: 'info' },
  FAILED: { label: '失败', type: 'danger' },
  REPLAY_FAILED: { label: '回放失败', type: 'danger' },
  EVALUATOR_FAILED: { label: '评估器失败', type: 'danger' },
  DRAFT: { label: '草稿', type: 'primary' },
  PUBLISHED: { label: '已发布', type: 'success' },
  ARCHIVED: { label: '已归档', type: 'info' },
  PASSED: { label: '通过', type: 'success' },
  ERROR: { label: '评估异常', type: 'danger' },
  SKIPPED: { label: '已跳过', type: 'info' },
  ACCEPTED: { label: '接受', type: 'success' },
  REJECTED: { label: '拒绝', type: 'danger' },
  INSUFFICIENT_EVIDENCE: { label: '证据不足', type: 'warning' },
  NEEDS_CHANGES: { label: '需要修改', type: 'warning' },
  NOT_APPLICABLE: { label: '不适用', type: 'info' },
}

const domainStatuses: Record<typeof props.domain, string[]> = {
  version: ['DRAFT', 'PUBLISHED', 'ARCHIVED'],
  run: [
    'CREATED',
    'DISPATCHING',
    'RUNNING',
    'PAUSED',
    'CANCEL_PENDING',
    'COMPLETED',
    'COMPLETED_WITH_ERRORS',
    'CANCELED',
    'FAILED',
  ],
  experiment: [
    'CREATED',
    'STARTING',
    'RUNNING',
    'PAUSED',
    'CANCEL_PENDING',
    'COMPLETED',
    'COMPLETED_WITH_ERRORS',
    'CANCELED',
    'FAILED',
  ],
  attempt: [
    'CREATED',
    'REPLAY_CREATED',
    'REPLAY_RUNNING',
    'EVALUATING',
    'COMPLETED',
    'REPLAY_FAILED',
    'EVALUATOR_FAILED',
    'CANCEL_PENDING',
    'CANCELED',
  ],
  result: ['PASSED', 'FAILED', 'ERROR', 'SKIPPED'],
  decision: ['ACCEPTED', 'REJECTED', 'INSUFFICIENT_EVIDENCE'],
  feedback: ['ACCEPTED', 'REJECTED', 'NEEDS_CHANGES', 'NOT_APPLICABLE'],
}

const presentation = computed<StatusPresentation>(() => {
  if (!props.status) return { label: '未提供状态', type: 'info' }
  if (!domainStatuses[props.domain].includes(props.status)) {
    return { label: props.status, type: 'info' }
  }
  if (props.domain === 'result' && props.status === 'FAILED') {
    return { label: '未通过', type: 'danger' }
  }
  return states[props.status]!
})
const accessibleLabel = computed(() =>
  props.status ? `${presentation.value.label} (${props.status})` : presentation.value.label,
)
</script>
