<template>
  <section aria-label="线上实验操作">
    <p v-if="error" role="alert">{{ error }}</p>
    <div class="actions">
      <el-button
        v-for="action in visibleActions"
        :key="action"
        :type="action === 'emergency-stop' ? 'danger' : 'default'"
        :disabled="busy || !available(action)"
        @click="open(action)"
        >{{ onlineActionLabel(action) }}</el-button
      >
    </div>
    <p v-if="!visibleActions.length">
      当前状态没有可执行的运行操作；历史任务、报告和结算记录仍可查看。
    </p>
    <details>
      <summary>查看全部操作说明</summary>
      <p>
        启动须完成预检及业务授权；暂停关闭新分配；恢复沿用原截止时间；结束等待在途任务收敛；紧急熔断还会请求取消在途任务。已发生的费用和文档变更不会自动撤回。
      </p>
    </details>
    <p v-if="experiment.status === 'STOPPING'">
      停止中仍保留空间占位。取消、执行槽及预算须分别等待权威确认。
    </p>
    <p v-if="!preflight?.startable && ['CREATED', 'PAUSED'].includes(experiment.status)">
      启动与恢复需要有效预检及部署就绪，不可用草案检查代替。
    </p>
    <el-dialog
      :model-value="!!selected"
      :title="selected ? onlineActionLabel(selected) : ''"
      width="min(620px, 94vw)"
      @close="close"
    >
      <template v-if="selected">
        <p>实验：{{ experiment.name }}。范围、比例及配置保持冻结。</p>
        <p v-if="selected === 'start'">
          这是实际业务执行，会产生草稿或变更提案，正式文档仍须人工审批。
        </p>
        <p v-if="selected === 'stop'">关闭新分配，已接受任务继续收敛；未决不会立即释放占位。</p>
        <p v-if="selected === 'pause'">暂停新分配，已接受任务继续收敛。</p>
        <p v-if="selected === 'resume'">
          恢复原实验，不延长原截止时间。服务端将重新检查依赖和授权。
        </p>
        <p v-if="['start', 'emergency-stop'].includes(selected)" class="risk">
          {{ ONLINE_EMERGENCY_NOTICE }}
        </p>
        <p v-if="['stop', 'emergency-stop'].includes(selected)">{{ ONLINE_FALLBACK_NOTICE }}</p>
        <template v-if="selected === 'start'">
          <label><input v-model="acks.live" type="checkbox" />确认实际业务副作用与人工审批</label>
          <label
            ><input
              v-model="acks.retention"
              type="checkbox"
            />确认原始证据的保留、容量和删除处理责任</label
          >
          <label
            ><input v-model="acks.shared" type="checkbox" />确认两组共享资源，延迟存在干扰</label
          >
          <label
            ><input v-model="acks.cancel" type="checkbox" />确认自动紧急取消范围及未确认风险</label
          >
        </template>
        <label v-else
          >操作原因<el-input type="textarea" v-model="reason" aria-label="操作原因" maxlength="2000" :rows="3" />
        </label>
        <p v-if="!confirmationValid" role="alert">确认已失效：请重新预检或刷新当前状态。</p>
      </template>
      <template #footer
        ><el-button :disabled="busy" @click="close">返回</el-button
        ><el-button :disabled="busy || !ready" type="primary" @click="submit">{{
          busy ? '正在提交' : '确认操作'
        }}</el-button></template
      >
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElDialog, ElInput } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import { changeOnlineState } from './api'
import {
  ONLINE_EMERGENCY_NOTICE,
  ONLINE_FALLBACK_NOTICE,
  onlineActionAvailable,
  onlineActionLabel,
  onlineIdentity,
  onlineStateVersion,
} from './presentation'
import type {
  OnlineAction,
  OnlineActionResult,
  OnlineExperiment,
  OnlinePreflight,
  OnlineStartRequest,
  OnlineStateRequest,
} from './types'

const props = defineProps<{
  experiment: OnlineExperiment
  preflight: OnlinePreflight | null
  allowed: boolean
  canTerminate: boolean
}>()
const emit = defineEmits<{ changed: [value: OnlineActionResult] }>()
const visibleActions = computed<OnlineAction[]>(() => {
  switch (props.experiment.status) {
    case 'CREATED':
      return ['start', 'stop']
    case 'ACTIVE':
      return ['pause', 'stop', 'emergency-stop']
    case 'PAUSED':
      return ['resume', 'stop', 'emergency-stop']
    case 'STOPPING':
      return ['emergency-stop']
    default:
      return []
  }
})
const selected = ref<OnlineAction | null>(null),
  reason = ref(''),
  busy = ref(false),
  error = ref(''),
  captured = ref(''),
  now = ref(Date.now())
const acks = ref({ live: false, retention: false, shared: false, cancel: false })
let pending: AbortController | undefined, expiryTimer: ReturnType<typeof setTimeout> | undefined
let retry: { fingerprint: string; key: string } | undefined
const identity = computed(() => onlineIdentity(props.experiment))
function available(action: OnlineAction) {
  return (
    props.allowed &&
    (action !== 'emergency-stop' || props.canTerminate) &&
    onlineActionAvailable(props.experiment, action, props.preflight, now.value)
  )
}
const confirmationValid = computed(
  () => !!selected.value && captured.value === identity.value && available(selected.value),
)
const ready = computed(
  () =>
    confirmationValid.value &&
    (selected.value === 'start' ? Object.values(acks.value).every(Boolean) : !!reason.value.trim()),
)
function close() {
  if (busy.value) return
  selected.value = null
}
function open(action: OnlineAction) {
  now.value = Date.now()
  if (!available(action)) return
  selected.value = action
  captured.value = identity.value
  reason.value = ''
  acks.value = { live: false, retention: false, shared: false, cancel: false }
  error.value = ''
}
watch(
  () => props.preflight,
  (proof) => {
    if (selected.value === 'start' || selected.value === 'resume') {
      selected.value = null
      pending?.abort()
      busy.value = false
      retry = undefined
    }
    clearTimeout(expiryTimer)
    now.value = Date.now()
    if (proof)
      expiryTimer = setTimeout(
        () => {
          now.value = Date.now()
        },
        Math.max(0, Date.parse(proof.expiresAt) - Date.now()) + 1,
      )
  },
  { immediate: true },
)
watch([identity, () => props.allowed, () => props.canTerminate], () => {
  selected.value = null
  pending?.abort()
  busy.value = false
  error.value = ''
  retry = undefined
})
async function submit() {
  now.value = Date.now()
  if (!ready.value || !selected.value || busy.value) return
  const action = selected.value,
    snapshot = identity.value
  const common = {
    manifestHash: props.experiment.manifestHash!,
    expectedStateVersion: onlineStateVersion(props.experiment.stateVersion),
  }
  const payload:
    Omit<OnlineStateRequest, 'clientRequestKey'> | Omit<OnlineStartRequest, 'clientRequestKey'> =
    action === 'start'
      ? {
          ...common,
          preflightProofHash: props.preflight!.proofHash,
          liveSideEffectsAcknowledged: acks.value.live,
          retentionAcknowledged: acks.value.retention,
          sharedResourcesAcknowledged: acks.value.shared,
          emergencyCancellationAcknowledged: acks.value.cancel,
        }
      : { ...common, reason: reason.value.trim() }
  const fingerprint = JSON.stringify({ id: props.experiment.id, action, payload })
  if (retry?.fingerprint !== fingerprint) retry = { fingerprint, key: window.crypto.randomUUID() }
  const controller = new AbortController()
  pending = controller
  busy.value = true
  error.value = ''
  try {
    const result = await changeOnlineState(
      props.experiment.id,
      action,
      { ...payload, clientRequestKey: retry.key },
      controller.signal,
    )
    if (controller.signal.aborted || snapshot !== identity.value || !props.allowed) return
    if (
      result.experimentId !== props.experiment.id ||
      result.manifestHash !== props.experiment.manifestHash
    )
      throw new Error('操作结果身份不一致，请刷新确认')
    retry = undefined
    selected.value = null
    emit('changed', result)
  } catch (e) {
    if (!controller.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
onBeforeUnmount(() => {
  pending?.abort()
  clearTimeout(expiryTimer)
})
</script>
<style scoped>
.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
label {
  display: block;
  margin: 12px 0;
}
textarea {
  width: 100%;
  box-sizing: border-box;
}
.risk,
[role='alert'] {
  color: var(--adw-color-danger);
}
</style>
