<template>
  <div class="case-source" aria-label="来源任务与文档">
    <dl>
      <dt>来源任务</dt>
      <dd>
        <RouterLink
          v-if="canReadTask && version.sourceTaskId"
          :to="`/spaces/${spaceId}/tasks/${version.sourceTaskId}?tab=evidence`"
        >
          {{ taskName || `任务 #${version.sourceTaskId}` }}
        </RouterLink>
        <span v-else>{{
          version.sourceTaskId ? `任务 #${version.sourceTaskId}` : '未提供来源任务'
        }}</span>
        <small v-if="taskName">当前任务名称 · #{{ version.sourceTaskId }}</small>
        <small v-if="taskError" role="status">任务名称暂不可用，保留来源 ID。</small>
      </dd>
      <dt>来源文档</dt>
      <dd>
        <template v-if="source">
          <span>{{ source.documentTitle || `文档 #${source.documentId}` }}</span>
          <small>当前文档名称仅辅助定位 · #{{ source.documentId }}</small>
        </template>
        <span v-else>{{ sourceLoading ? '正在核对冻结来源…' : '文档名称与身份暂不可用' }}</span>
        <small v-if="!canReadTask || !canReadDocument"
          >查看来源名称需要对应任务、文档读取权限。</small
        >
        <small v-else-if="sourceError" role="status"
          >来源读取或冻结依据核验失败；以下冻结版本和审计信息仍可查看。</small
        >
      </dd>
      <dt>冻结文档版本</dt>
      <dd>
        {{
          version.documentVersionSnapshot == null ? '未提供' : `v${version.documentVersionSnapshot}`
        }}
      </dd>
    </dl>
    <p>名称显示当前取值；回放依据为此用例保存的冻结版本与哈希。</p>
    <button v-if="taskError || sourceError" type="button" @click="load">重试来源名称</button>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { getTaskNames } from '@/features/task/api/task-api'
import { getTestCaseSource } from '../api/evaluation-api'
import type { TestCaseSource, TestCaseVersion } from '../types'
const props = defineProps<{
  spaceId: string
  version: TestCaseVersion
  canReadTask: boolean
  canReadDocument: boolean
}>()
const taskName = ref(''),
  source = ref<TestCaseSource | null>(null)
const taskError = ref(false),
  sourceError = ref(false),
  sourceLoading = ref(false)
let pending: AbortController | undefined
function load() {
  pending?.abort()
  const request = new AbortController(),
    { spaceId, version, canReadTask, canReadDocument } = props
  pending = request
  taskName.value = ''
  source.value = null
  taskError.value = false
  sourceError.value = false
  sourceLoading.value = false
  if (String(version.spaceId) !== spaceId || !canReadTask || !version.sourceTaskId) return
  const taskId = String(version.sourceTaskId)
  void getTaskNames({ spaceId, taskIds: [taskId] }, request.signal)
    .then((rows) => {
      if (request.signal.aborted) return
      const row = rows.find(
        (item) => String(item.id) === taskId && String(item.spaceId) === spaceId,
      )
      taskName.value = row?.name || ''
      taskError.value = !row?.name
    })
    .catch(() => {
      if (!request.signal.aborted) taskError.value = true
    })
  if (!canReadDocument) return
  sourceLoading.value = true
  void getTestCaseSource(version.id, request.signal)
    .then((value) => {
      if (request.signal.aborted) return
      if (
        String(value.testCaseVersionId) !== String(version.id) ||
        String(value.spaceId) !== spaceId ||
        String(value.sourceTaskId) !== taskId ||
        !version.sourceInputHash ||
        value.sourceInputHash !== version.sourceInputHash ||
        !value.documentId
      )
        throw new Error('来源身份不一致')
      source.value = value
    })
    .catch(() => {
      if (!request.signal.aborted) sourceError.value = true
    })
    .finally(() => {
      if (!request.signal.aborted) sourceLoading.value = false
    })
}
watch(() => [props.spaceId, props.version, props.canReadTask, props.canReadDocument], load, {
  immediate: true,
})
onBeforeUnmount(() => pending?.abort())
</script>

<style scoped>
dl {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: var(--adw-space-3) var(--adw-space-4);
  margin: 0;
}
dt {
  color: var(--adw-text-secondary);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
small {
  display: block;
  color: var(--adw-text-secondary);
  margin-top: var(--adw-space-1);
}
p {
  color: var(--adw-text-secondary);
  line-height: 1.6;
}
a {
  color: var(--adw-color-primary);
}
button {
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-surface);
  color: var(--adw-text-primary);
  padding: var(--adw-space-2) var(--adw-space-3);
  cursor: pointer;
}
a:focus-visible,
button:focus-visible {
  outline: 2px solid var(--adw-color-primary);
  outline-offset: 2px;
}
@media (max-width: 600px) {
  dl {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
