<template>
  <section class="source-picker" aria-label="来源任务选择">
    <p v-if="!canReadSource" role="status">选择与核验来源需要任务读取、文档读取权限。</p>
    <template v-else>
      <form class="source-picker__search" @submit.prevent="loadTasks(1)">
        <el-input
          v-model="keyword"
          aria-label="搜索来源任务"
          placeholder="搜索已完成的 LIVE 任务"
          clearable
        />
        <el-button native-type="submit">查询来源</el-button>
      </form>
      <DataState :loading="loading" :error="error" @retry="loadTasks(page.pageNum)">
        <div class="source-picker__list">
          <button
            v-for="task in page.records"
            :key="String(task.id)"
            type="button"
            :aria-pressed="String(selected?.id) === String(task.id)"
            @click="selectTask(task.id)"
          >
            <strong>{{ task.name }}</strong
            ><span>{{ task.taskNo }} · {{ task.agentName || `Agent #${task.agentId}` }}</span>
          </button>
          <p v-if="!page.records.length">没有符合条件的任务。请先完成一次 LIVE 执行。</p>
        </div>
        <el-pagination
          v-if="page.total > 10"
          small
          layout="prev, pager, next"
          :total="page.total"
          :page-size="10"
          :current-page="page.pageNum"
          @current-change="loadTasks"
        />
      </DataState>
      <p v-if="checking" role="status">正在核验选中来源的冻结输入…</p>
      <p v-if="qualificationError" role="alert">{{ qualificationError }}</p>
      <div v-if="selected && eligibility" class="source-picker__facts">
        <strong>{{ selected.name }}</strong>
        <p>
          {{
            eligibility.replayable
              ? '来源可回放；创建版本前会再次核验。'
              : replayReason(eligibility.reasonCode)
          }}
        </p>
        <dl>
          <dt>Task / Execution</dt>
          <dd>{{ selected.id }} / {{ eligibility.sourceExecutionId || '未提供' }}</dd>
          <dt>输入 snapshot hash</dt>
          <dd>{{ eligibility.inputSnapshotHash || '未提供' }}</dd>
          <dt>执行 snapshot hash</dt>
          <dd>{{ eligibility.executionSnapshotHash || '未提供' }}</dd>
        </dl>
      </div>
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElInput, ElButton, ElPagination } from 'element-plus'
import DataState from '@/shared/components/DataState.vue'
import { normalizeApiError } from '@/api/errors'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { searchTasks, getTask, getReplayEligibility } from '@/features/task/api/task-api'
import type { TaskDetail, TaskPage } from '@/features/task/types'
import type { ReplayEligibility } from '@/features/task/engineering-types'
import { replayReason } from '../catalog'
const props = defineProps<{ spaceId: string; initialTaskId?: string }>()
const emit = defineEmits<{ select: [taskId: string | null] }>()
const workspace = useWorkspaceStore()
const canReadSource = computed(
  () =>
    String(workspace.currentSpaceId) === props.spaceId &&
    workspace.hasPermission(SPACE_PERMISSIONS.TASK_READ) &&
    workspace.hasPermission(SPACE_PERMISSIONS.DOCUMENT_READ),
)
const keyword = ref(''),
  loading = ref(false),
  error = ref(''),
  checking = ref(false),
  qualificationError = ref('')
const page = ref<TaskPage>({ records: [], total: 0, pageNum: 1, pageSize: 10 })
const selected = ref<TaskDetail | null>(null),
  eligibility = ref<ReplayEligibility | null>(null)
let listRequest: AbortController | undefined, selectedRequest: AbortController | undefined
async function loadTasks(pageNum: number) {
  if (!canReadSource.value) return
  listRequest?.abort()
  const request = new AbortController()
  listRequest = request
  loading.value = true
  error.value = ''
  page.value.records = []
  try {
    const result = await searchTasks(
      {
        spaceId: props.spaceId,
        status: 'COMPLETED',
        executionMode: 'LIVE',
        keyword: keyword.value.trim() || undefined,
        pageNum,
        pageSize: 10,
      },
      request.signal,
    )
    if (request.signal.aborted) return
    if (
      result.records.some(
        (task) =>
          String(task.spaceId) !== props.spaceId ||
          task.executionMode !== 'LIVE' ||
          task.status !== 'COMPLETED',
      )
    )
      throw new Error('来源任务归属或执行状态不一致')
    page.value = result
  } catch (e) {
    if (!request.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}
async function selectTask(id: string | number) {
  emit('select', null)
  selectedRequest?.abort()
  selected.value = null
  eligibility.value = null
  qualificationError.value = ''
  if (!canReadSource.value) return
  const request = new AbortController()
  selectedRequest = request
  checking.value = true
  try {
    const task = await getTask(id, request.signal)
    if (request.signal.aborted) return
    if (
      String(task.id) !== String(id) ||
      String(task.spaceId) !== props.spaceId ||
      task.status !== 'COMPLETED' ||
      task.executionMode !== 'LIVE' ||
      !['ORIGINAL', 'RERUN', 'REVIEW_REWORK'].includes(task.lineageType)
    )
      throw new Error('请选择当前空间已完成的原始、重跑或审批返工 LIVE 任务。')
    const result = await getReplayEligibility(id, request.signal)
    if (request.signal.aborted) return
    if (String(result.sourceTaskId) !== String(id)) throw new Error('回放资格来源身份不一致')
    selected.value = task
    eligibility.value = result
    if (result.replayable) emit('select', String(id))
  } catch (e) {
    if (!request.signal.aborted) qualificationError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) checking.value = false
  }
}
watch(
  () => [props.spaceId, props.initialTaskId, canReadSource.value],
  () => {
    listRequest?.abort()
    selectedRequest?.abort()
    selected.value = null
    eligibility.value = null
    checking.value = false
    loading.value = false
    qualificationError.value = ''
    error.value = ''
    emit('select', null)
    if (canReadSource.value) {
      void loadTasks(1)
      if (props.initialTaskId) void selectTask(props.initialTaskId)
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  listRequest?.abort()
  selectedRequest?.abort()
})
</script>
<style scoped>
.source-picker__search {
  display: flex;
  gap: var(--adw-space-3);
}
.source-picker__list {
  display: grid;
  max-height: 230px;
  overflow: auto;
  margin: var(--adw-space-3) 0;
}
.source-picker__list button {
  display: grid;
  gap: 4px;
  padding: var(--adw-space-3);
  border: 0;
  border-bottom: 1px solid var(--adw-border-color-light);
  background: transparent;
  color: var(--adw-text-primary);
  text-align: left;
  cursor: pointer;
}
.source-picker__list button[aria-pressed='true'] {
  background: var(--adw-color-primary-soft);
}
.source-picker__list span,
p {
  color: var(--adw-text-secondary);
}
.source-picker__facts {
  background: var(--adw-surface-muted);
  padding: var(--adw-space-4);
  border-radius: var(--adw-radius-sm);
}
dl {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr);
  gap: var(--adw-space-2);
  font-size: var(--adw-font-size-caption);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
p[role='alert'] {
  color: var(--adw-color-danger);
}
button:focus-visible {
  outline: 2px solid var(--adw-color-primary);
}
</style>
