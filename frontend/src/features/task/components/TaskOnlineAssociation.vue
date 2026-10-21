<template>
  <section
    v-if="associated && canReadTask"
    class="online-association surface-card"
    aria-label="任务线上实验关联"
  >
    <h2>线上实验关联</h2>
    <p>当前为普通任务详情，组别和执行配置可能已经展示；评审时不能据此声明 BLINDED。</p>
    <details>
      <summary>实验绑定与协议身份</summary>
      <dl>
        <div>
          <dt>实验 / 分配</dt>
          <dd>
            {{ onlineFact(task.onlineExperimentId) }} / {{ onlineFact(task.onlineAssignmentId) }}
          </dd>
        </div>
        <div>
          <dt>绑定协议 / 摘要</dt>
          <dd>
            {{ onlineFact(task.onlineBindingSchemaVersion) }} /
            {{ onlineFact(task.onlineBindingHash) }}
          </dd>
        </div>
      </dl>
    </details>
    <p v-if="!canRead">当前权限无法读取实验组和冻结模板，不推断历史配置。</p>
    <template v-else>
      <el-button size="small" :loading="loading" @click="refresh">核对冻结关联</el-button>
      <p v-if="error" role="alert">实验关联未知：{{ error }}</p>
      <template v-if="facts">
        <dl>
          <dt>实际分组</dt>
          <dd>
            {{ facts.variant === 'BASELINE' ? 'Baseline（冻结基线）' : 'Candidate（冻结候选）' }}
          </dd>
        </dl>
        <details>
          <summary>冻结模板与依赖摘要</summary>
          <dl>
            <div>
              <dt>冻结模板 ID / 摘要</dt>
              <dd>{{ facts.template.id }} / {{ facts.template.hash }}</dd>
            </div>
            <div>
              <dt>非 Prompt 配置摘要</dt>
              <dd>{{ facts.template.nonPromptHash }}</dd>
            </div>
            <div>
              <dt>清单摘要 / 状态</dt>
              <dd>{{ facts.manifestHash }} / {{ onlineStatus(facts.status) }}</dd>
            </div>
          </dl>
        </details>
      </template>
      <RouterLink
        v-if="facts"
        :to="`/spaces/${task.spaceId}/evaluation/online-experiments/${task.onlineExperimentId}`"
        >返回线上实验</RouterLink
      >
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElButton } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import { getOnlineExperiment, searchOnlineAssignments } from '@/features/evaluation/online/api'
import { onlineFact, onlineStatus, validOnlineId } from '@/features/evaluation/online/presentation'
import type { OnlineExperiment } from '@/features/evaluation/online/types'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'
import type { TaskDetail } from '../types'

const props = defineProps<{ task: TaskDetail }>()
const workspace = useWorkspaceStore()
const associated = computed(
  () => props.task.onlineExperimentId != null || props.task.onlineAssignmentId != null,
)
const canReadTask = computed(() => String(workspace.currentSpaceId) === String(props.task.spaceId) &&
  workspace.hasPermission(SPACE_PERMISSIONS.SPACE_READ) && workspace.hasPermission(SPACE_PERMISSIONS.TASK_READ))
const canRead = computed(
  () =>
    String(workspace.currentSpaceId) === String(props.task.spaceId) &&
    [
      SPACE_PERMISSIONS.SPACE_READ,
      SPACE_PERMISSIONS.TASK_READ,
      SPACE_PERMISSIONS.DOCUMENT_READ,
      SPACE_PERMISSIONS.EVALUATION_READ,
    ].every((permission) => workspace.hasPermission(permission)),
)
const loading = ref(false),
  error = ref('')
const facts = ref<{
  variant: string
  template: NonNullable<OnlineExperiment['baseline']>
  manifestHash: string
  status: string
} | null>(null)
let controller: AbortController | null = null

async function refresh() {
  controller?.abort()
  facts.value = null
  error.value = ''
  loading.value = false
  if (!associated.value || !canRead.value) return
  const task = props.task,
    experimentId = String(task.onlineExperimentId),
    assignmentId = String(task.onlineAssignmentId)
  if (
    !validOnlineId(task.onlineExperimentId ?? '') ||
    !validOnlineId(task.onlineAssignmentId ?? '') ||
    task.onlineBindingSchemaVersion !== 2 ||
    !/^[0-9a-f]{64}$/.test(task.onlineBindingHash ?? '')
  ) {
    error.value = '任务绑定缺失或协议尚不支持'
    return
  }
  const current = new AbortController()
  controller = current
  loading.value = true
  try {
    const [experiment, page] = await Promise.all([
      getOnlineExperiment(experimentId, current.signal),
      searchOnlineAssignments(
        experimentId,
        { taskId: String(task.id), pageNum: 1, pageSize: 1 },
        current.signal,
      ),
    ])
    if (current.signal.aborted) return
    const row = page.records[0]
    if (
      experiment.id !== experimentId ||
      experiment.spaceId !== String(task.spaceId) ||
      experiment.agentId !== String(task.agentId) ||
      experiment.manifestSchemaVersion !== 2 ||
      !/^[0-9a-f]{64}$/.test(experiment.manifestHash ?? '') ||
      page.total !== 1 ||
      page.records.length !== 1 ||
      !row ||
      row.id !== assignmentId ||
      row.experimentId !== experimentId ||
      row.spaceId !== String(task.spaceId) ||
      row.taskId !== String(task.id) ||
      row.documentId !== String(task.documentId) ||
      !['BASELINE', 'CANDIDATE'].includes(row.variant)
    )
      throw new Error('任务、实验或分配身份不一致')
    const template = row.variant === 'BASELINE' ? experiment.baseline : experiment.candidate
    if (
      !template ||
      !validOnlineId(template.id) ||
      template.schemaVersion !== 2 ||
      !/^[0-9a-f]{64}$/.test(template.hash) ||
      !/^[0-9a-f]{64}$/.test(template.nonPromptHash)
    )
      throw new Error('冻结模板身份无法核对')
    facts.value = {
      variant: row.variant,
      template,
      manifestHash: experiment.manifestHash!,
      status: experiment.status,
    }
  } catch (failure) {
    if (!current.signal.aborted) {
      error.value = normalizeApiError(failure).message
      current.abort()
    }
  } finally {
    if (controller === current) loading.value = false
  }
}
watch(
  () => JSON.stringify([
    props.task.id,
    props.task.spaceId,
    props.task.agentId,
    props.task.documentId,
    props.task.onlineExperimentId,
    props.task.onlineAssignmentId,
    props.task.onlineBindingSchemaVersion,
    props.task.onlineBindingHash,
    canRead.value,
  ]),
  refresh,
  { immediate: true },
)
onBeforeUnmount(() => controller?.abort())
</script>

<style scoped>
.online-association {
  padding: 20px;
}
summary {
  cursor: pointer;
  margin-block: 8px;
  color: var(--adw-text-secondary);
}
.online-association h2 {
  margin-top: 0;
  font-size: 18px;
}
.online-association p {
  color: var(--adw-text-secondary);
}
.online-association dl {
  display: grid;
  gap: 12px;
}
.online-association dt {
  color: var(--adw-text-tertiary);
}
.online-association dd {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
}
</style>
