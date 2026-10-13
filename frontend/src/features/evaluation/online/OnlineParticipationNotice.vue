<template>
  <section v-if="creation || canRead" class="participation-notice" aria-label="线上实验参与提示">
    <el-alert
      v-if="creation"
      type="warning"
      :closable="false"
      show-icon
      title="符合线上实验范围的任务可能使用冻结 baseline 或候选 Prompt，分组由服务端决定。"
    >
      正式文档仍生成变更请求并经过人工审批，草稿可能发生真实修改；参与任务会占用实验、任务及空间预算。当前页面不能指定组或候选配置。
    </el-alert>
    <template v-if="canRead">
      <div class="participation-heading">
        <strong>空间实验占位</strong>
        <el-button :loading="loading" size="small" @click="refresh">核对当前状态</el-button>
      </div>
      <p v-if="error" role="alert">占位状态未知：{{ error }}</p>
      <p v-else-if="loading">正在核对运行中、暂停及停止中的实验。</p>
      <template v-else-if="occupying">
        <p>
          {{ occupying.name }} · {{ onlineStatus(occupying.status) }} · Agent
          {{ occupying.agentId }}
        </p>
        <p v-if="occupying.status === 'ACTIVE'">
          仅符合该 Agent 和冻结文档范围的新任务可能参与。最终归属以服务端任务绑定为准。
        </p>
        <p v-else>
          暂停或停止中仍保留空间占位，不能启动另一个实验。新准入不再分配实验组；已绑定任务的重试继续使用原身份。
          {{ ONLINE_FALLBACK_NOTICE }}
        </p>
        <p v-if="occupying.status === 'STOPPING'">
          未决执行和结算仍待核对，停止请求不等于远端调用已结束。
        </p>
        <RouterLink :to="`/spaces/${spaceId}/evaluation/online-experiments/${occupying.id}`"
          >查看实验与阻塞原因</RouterLink
        >
      </template>
      <p v-else>本次查询未发现占位实验；启动与任务创建时仍由服务端重新核验。</p>
    </template>
    <p v-else-if="creation">当前权限无法读取实验范围与占位状态，是否参与由服务端核验。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { RouterLink } from 'vue-router'
import { ElAlert, ElButton } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'
import { searchOnlineExperiments } from './api'
import { ONLINE_FALLBACK_NOTICE, onlineStatus, validOnlineId } from './presentation'
import type { OnlineSummary } from './types'

const props = withDefaults(defineProps<{ spaceId: string; creation?: boolean }>(), {
  creation: false,
})
const workspace = useWorkspaceStore()
const canRead = computed(
  () =>
    validOnlineId(props.spaceId) &&
    String(workspace.currentSpaceId) === props.spaceId &&
    workspace.hasPermission(SPACE_PERMISSIONS.SPACE_READ) &&
    workspace.hasPermission(SPACE_PERMISSIONS.EVALUATION_READ),
)
const loading = ref(false),
  error = ref(''),
  occupying = ref<OnlineSummary | null>(null)
let controller: AbortController | null = null

async function refresh() {
  controller?.abort()
  occupying.value = null
  error.value = ''
  if (!canRead.value) {
    loading.value = false
    return
  }
  const current = new AbortController()
  controller = current
  loading.value = true
  const spaceId = props.spaceId
  try {
    const statuses = ['ACTIVE', 'PAUSED', 'STOPPING']
    const pages = await Promise.all(
      statuses.map((status) =>
        searchOnlineExperiments({ spaceId, status, pageNum: 1, pageSize: 1 }, current.signal),
      ),
    )
    if (current.signal.aborted) return
    pages.forEach((page, index) => {
      if (
        !Number.isSafeInteger(page.total) ||
        page.total < 0 ||
        page.total > 1 ||
        page.records.length !== page.total ||
        page.records.some(
          (item) =>
            item.spaceId !== spaceId || item.status !== statuses[index] || !validOnlineId(item.id),
        )
      )
        throw new Error('占位查询身份或数量不一致，请重新核对')
    })
    const rows = pages.flatMap((page) => page.records)
    if (rows.length > 1) throw new Error('状态查询期间占位发生变化，请重新核对')
    occupying.value = rows[0] ?? null
  } catch (failure) {
    if (!current.signal.aborted) {
      error.value = normalizeApiError(failure).message
      current.abort()
    }
  } finally {
    if (controller === current) loading.value = false
  }
}
watch(() => [props.spaceId, canRead.value], refresh, { immediate: true })
onBeforeUnmount(() => controller?.abort())
</script>

<style scoped>
.participation-notice {
  display: grid;
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
}
.participation-notice p {
  margin: 0;
  overflow-wrap: anywhere;
  color: var(--adw-text-secondary);
}
.participation-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
</style>
