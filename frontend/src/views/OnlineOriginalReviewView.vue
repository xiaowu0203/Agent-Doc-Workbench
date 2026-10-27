<template>
  <section class="original-review" aria-label="隐藏分组原始评价">
    <header>
      <h2>原始产物评价</h2>
      <p>只查看冻结业务目标、输入与完整原始产物。正式审批独立进行，评分不会修改文档。</p>
    </header>
    <p>如已在其他页面或站外获知分组、Prompt 或成本，须声明非盲态；没有访问记录不能证明盲态。</p>
    <p v-if="!allowed" role="alert">需要当前空间的读取、评估执行、任务读取和文档读取权限。</p>
    <template v-else>
      <form class="selection" @submit.prevent="loadContext">
        <label
          >分配 ID<input
            :value="assignmentId"
            aria-label="评审分配ID"
            inputmode="numeric"
            :disabled="busy"
            @change="changeAssignment"
        /></label>
        <label
          >原始产物<select
            :value="target"
            aria-label="原始评审目标"
            :disabled="busy"
            @change="changeTarget"
          >
            <option value="TEXT">原始文本</option>
            <option value="CHANGE_SET">完整原始变更集合</option>
          </select></label
        >
        <el-button native-type="submit" :disabled="loading || busy || !validOnlineId(assignmentId)"
          >读取原始产物</el-button
        >
      </form>
      <DataState
        :loading="listing"
        :error="listError"
        :empty="!candidates.records.length"
        empty-text="暂无评审候选；未完成或不可求证的产物不会生成零分。"
        @retry="loadCandidates(candidates.pageNum)"
      >
        <div class="candidates">
          <el-button
            v-for="item in candidates.records"
            :key="item.assignmentId"
            :disabled="busy"
            :aria-pressed="assignmentId === item.assignmentId"
            @click="selectAssignment(item.assignmentId)"
            ><span class="candidate-label"
              ><strong>{{ item.documentTitle || `文档 #${item.documentId}` }}</strong
              ><small>分配 #{{ item.assignmentId }}</small
              ><span>{{
                item.ownReviewRecorded === true
                  ? '本人有历史评审记录'
                  : item.ownReviewRecorded === false
                    ? '本人尚无评审记录'
                    : '本人记录状态未知'
              }}</span
              ><small v-if="assignmentId === item.assignmentId">当前选择</small></span
            ></el-button
          >
        </div>
        <p>
          这里只显示本人是否曾提交评审，不透露他人评分；文本与变更集合的具体记录、当前证据和链尾仍需点开核对。
        </p>
        <el-pagination
          :current-page="candidates.pageNum"
          :page-size="10"
          :total="candidates.total"
          layout="prev, pager, next"
          @current-change="loadCandidates"
        />
      </DataState>
      <p v-if="error" role="alert">{{ error }}</p>
      <p v-if="loading" role="status">正在核验完整冻结上下文……</p>
      <template v-if="context">
        <section>
          <h3>冻结业务目标</h3>
          <pre>{{ context.goal.instruction }}</pre>
          <h3>冻结输入 · 版本 {{ context.originalContext.baseVersion }}</h3>
          <pre>{{ context.originalContext.inputContent }}</pre>
        </section>
        <section aria-label="完整原始产物">
          <h3>完整原始产物</h3>
          <template v-if="target === 'TEXT'">
            <el-button
              :aria-pressed="markdownPreview"
              @click="markdownPreview = !markdownPreview"
              >{{ markdownPreview ? '查看原始文本' : '预览 Markdown' }}</el-button
            >
            <!-- eslint-disable-next-line vue/no-v-html -- Markdown 禁用 HTML，输出再经 DOMPurify 移除外部媒体和链接。 -->
            <div v-if="markdownPreview" class="original-markdown" v-html="renderedOriginal"></div>
            <pre v-else>{{ context.originalContext.originalText }}</pre>
          </template>
          <article
            v-for="(item, index) in context.originalContext.originals || []"
            :key="item.evidenceId"
          >
            <h4>产物 {{ index + 1 }} · {{ item.evidenceType }}</h4>
            <p>
              {{ item.structureValid ? '原始结构有效' : '原始结构无效，仍纳入整体评价' }} · 基线
              {{ item.baseVersion
              }}<span v-if="item.outputVersion !== null"> / 收尾 {{ item.outputVersion }}</span>
            </p>
            <pre v-if="item.originalChanges !== null">{{ item.originalChanges }}</pre>
            <pre v-if="item.originalContent !== null">{{ item.originalContent }}</pre>
            <p v-else>没有可应用的完整正文；保留原始结构，不能选择其他成功项代替。</p>
          </article>
        </section>
        <OnlineQualityHistoryPanel
          :key="`${identity}/${saved?.id ?? ''}`"
          :space-id="spaceId"
          :experiment-id="experimentId"
          :context="context"
          :target="target"
          :busy="busy"
          @checking="historyChecking"
          @verified="historyVerified"
          @correct="selectCurrentTail"
        />
        <p v-if="unconfirmedRequest" role="alert">
          上次提交尚未确认；先核对反馈链，不能重复提交或自动追加更正。
        </p>
        <form
          @submit.prevent="prepare"
          @input.capture="ratingDirty = true"
          @change.capture="ratingDirty = true"
        >
          <fieldset :disabled="busy">
            <p>
              服务端知情状态：{{
                context.knowledgeStatus
              }}。最终状态由服务端复核，非盲态更正不能恢复盲态。
            </p>
            <label
              >声明知情状态<select v-model="blindness" aria-label="声明知情状态">
                <option value="UNKNOWN">无法确认</option>
                <option value="NON_BLINDED">已经知情</option>
                <option
                  v-if="context.blindedEligible && context.knowledgeStatus === 'BLINDED'"
                  value="BLINDED"
                >
                  未获知组别或配置
                </option>
              </select></label
            >
            <label
              >原始质量等级<select v-model.number="rubric" aria-label="原始质量等级">
                <option value="" disabled>请选择质量等级</option>
                <option
                  v-for="level in ORIGINAL_QUALITY_LEVELS"
                  :key="level.score"
                  :value="level.score"
                >
                  {{ level.score }} / 4 · {{ level.label }}
                </option>
              </select></label
            >
            <p>
              0 是有效评分；无法取得证据时不能评分。依据冻结目标评价完整产物，等级会映射为
              {{ rubric === '' ? '待选择' : rubric / 4 }}，与正式审批接受与否分开。
            </p>
            <details>
              <summary>通用交付等级说明 v1</summary>
              <ul>
                <li v-for="level in ORIGINAL_QUALITY_LEVELS" :key="level.score">
                  {{ level.score }} / 4：{{ level.label }}
                </li>
              </ul>
              <p>
                用于本次新评分。请按冻结目标和完整证据说明依据；历史评分不按此说明重新解释，质量标签与安全事件仍需分别判断。
              </p>
            </details>
            <label
              >质量标签<select v-model="label" aria-label="质量标签">
                <option value="" disabled>请选择质量标签</option>
                <option value="ACCEPTED">可接受</option>
                <option value="REJECTED">不可接受</option>
                <option value="NEEDS_CHANGES">需要修改</option>
                <option value="NOT_APPLICABLE">不适用</option>
              </select></label
            >
            <label
              >原因说明<textarea
                v-model="comment"
                aria-label="评价原因"
                maxlength="2000"
                rows="3"
              />
            </label>
            <label
              >原因代码（逗号分隔大写代码）<input v-model="codes" aria-label="评价原因代码"
            /></label>
            <label
              >明确更正的当前反馈 ID（首评留空）<input
                v-model="supersedes"
                aria-label="被更正反馈ID"
                inputmode="numeric"
            /></label>
            <label v-if="isOwner" class="checkbox"
              ><input v-model="serious" type="checkbox" />确认严重安全事件</label
            >
            <label v-if="serious && isOwner"
              >安全确认理由<textarea
                v-model="safetyReason"
                aria-label="安全确认理由"
                maxlength="2000"
              />
            </label>
            <el-button native-type="submit">核对评价</el-button>
          </fieldset>
        </form>
        <section v-if="prepared" aria-label="原始评价确认">
          <h3>确认追加评价</h3>
          <p>
            等级 {{ prepared.onlineQuality.rubricScore }} / 4，声明
            {{ prepared.onlineQuality.blindness }}；{{
              prepared.onlineQuality.supersedesFeedbackId ? '追加更正，旧反馈保留' : '追加首评'
            }}。
          </p>
          <el-button :disabled="busy" type="primary" @click="submit">{{
            busy ? '正在提交' : '确认评价'
          }}</el-button>
        </section>
        <p v-if="saved" role="status">
          已保存反馈 #{{ saved.id }}，有效知情状态
          {{ saved.onlineQuality.blindness }}。更正须引用当前链尾并填写理由。
        </p>
        <el-button :disabled="busy || !nextAssignment" @click="selectAssignment(nextAssignment)"
          >查看下一项（不提交评分）</el-button
        >
      </template>
    </template>
  </section>
</template>
<script setup lang="ts">
import { ORIGINAL_QUALITY_LEVELS } from '@/features/evaluation/online/rubric'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, onBeforeRouteLeave } from 'vue-router'
import { ElButton, ElPagination, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import { normalizeApiError } from '@/api/errors'
import DataState from '@/shared/components/DataState.vue'
import OnlineQualityHistoryPanel from '@/features/evaluation/online/OnlineQualityHistoryPanel.vue'
import { SPACE_PERMISSIONS as P } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'
import {
  createOnlineQuality,
  getOnlineReviewContext,
  searchOnlineReviewAssignments,
} from '@/features/evaluation/online/api'
import { validOnlineId } from '@/features/evaluation/online/presentation'
import type {
  OnlineBlindness,
  OnlinePage,
  OnlineQualityRequest,
  OnlineQualityResult,
  OnlineReviewAssignment,
  OnlineReviewContext,
  OnlineReviewTarget,
  OnlineQualityHistory,
} from '@/features/evaluation/online/types'

const props = defineProps<{ experimentId: string }>()
const route = useRoute(),
  workspace = useWorkspaceStore()
const spaceId = computed(() => String(route.params.spaceId || ''))
const allowed = computed(
  () =>
    String(workspace.currentSpaceId) === spaceId.value &&
    [P.SPACE_READ, P.EVALUATION_READ, P.EVALUATION_RUN, P.TASK_READ, P.DOCUMENT_READ].every(
      workspace.hasPermission,
    ),
)
const isOwner = computed(
  () => allowed.value && workspace.currentPermissions?.role?.roleKey === 'OWNER',
)
const assignmentId = ref(''),
  target = ref<OnlineReviewTarget>('TEXT'),
  context = ref<OnlineReviewContext | null>(null)
const candidates = ref<OnlinePage<OnlineReviewAssignment>>({
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
})
const listing = ref(false),
  loading = ref(false),
  busy = ref(false),
  listError = ref(''),
  error = ref('')
const blindness = ref<OnlineBlindness>('UNKNOWN'),
  rubric = ref<number | ''>(''),
  label = ref<OnlineQualityRequest['label'] | ''>(''),
  comment = ref(''),
  codes = ref(''),
  supersedes = ref(''),
  serious = ref(false),
  safetyReason = ref('')
const prepared = ref<OnlineQualityRequest | null>(null),
  saved = ref<OnlineQualityResult | null>(null)
watch(saved, (value) => {
  if (!value) return
  candidates.value.records = candidates.value.records.map((item) =>
    item.assignmentId === value.onlineQuality.assignmentId
      ? { ...item, ownReviewRecorded: true }
      : item,
  )
})
const ratingDirty = ref(false),
  markdownPreview = ref(false)
const markdown = new MarkdownIt({ html: false, linkify: false, breaks: true })
const renderedOriginal = computed(() =>
  DOMPurify.sanitize(markdown.render(context.value?.originalContext.originalText || ''), {
    FORBID_TAGS: ['img', 'a', 'video', 'audio', 'iframe', 'object', 'style'],
    FORBID_ATTR: ['style'],
  }),
)
const nextAssignment = computed(() => {
  const index = candidates.value.records.findIndex(
    (item) => item.assignmentId === assignmentId.value,
  )
  return index >= 0 ? candidates.value.records[index + 1]?.assignmentId || '' : ''
})
async function discardRating() {
  if (busy.value) return false
  if (!ratingDirty.value) return true
  try {
    await ElMessageBox.confirm(
      '当前评分尚未提交。离开将丢弃本页评分输入，已保存的历史反馈保持不变。',
      '保留评分输入？',
      { confirmButtonText: '丢弃输入并继续', cancelButtonText: '留在当前评审', type: 'warning' },
    )
    ratingDirty.value = false
    return true
  } catch {
    return false
  }
}
onBeforeRouteLeave(discardRating)
function beforeUnload(event: Event) {
  if (ratingDirty.value || busy.value) {
    event.preventDefault()
  }
}
onMounted(() => window.addEventListener('beforeunload', beforeUnload))
async function changeAssignment(event: Event) {
  const input = event.target as HTMLInputElement,
    value = input.value
  if (await discardRating()) assignmentId.value = value
  else input.value = assignmentId.value
}
async function changeTarget(event: Event) {
  const input = event.target as HTMLSelectElement,
    value = input.value as typeof target.value
  if (await discardRating()) target.value = value
  else input.value = target.value
}
const currentHistory = ref<OnlineQualityHistory | null>(null),
  unconfirmedRequest = ref<OnlineQualityRequest | null>(null)
let acknowledgedContent: string | null = null
function contentSignature(payload: OnlineQualityRequest) {
  return JSON.stringify([
    payload.onlineQuality.rubricScore,
    payload.label,
    payload.comment,
    payload.onlineQuality.reasonCodes,
    payload.onlineQuality.seriousSafetyEvent,
    payload.onlineQuality.safetyReason,
  ])
}
function historyChecking() {
  currentHistory.value = null
  prepared.value = null
}
function historyVerified(value: OnlineQualityHistory) {
  currentHistory.value = value
  prepared.value = null
  const attempted = unconfirmedRequest.value,
    own = value.lastOwnFeedback
  if (
    attempted &&
    own &&
    own.rubricScore === attempted.onlineQuality.rubricScore &&
    own.label === attempted.label &&
    own.comment === attempted.comment &&
    own.supersedesFeedbackId === attempted.onlineQuality.supersedesFeedbackId &&
    own.declaredBlindness === attempted.onlineQuality.blindness &&
    JSON.stringify(own.reasonCodes) === JSON.stringify(attempted.onlineQuality.reasonCodes) &&
    own.seriousSafetyEvent === attempted.onlineQuality.seriousSafetyEvent &&
    own.safetyReason === attempted.onlineQuality.safetyReason
  ) {
    saved.value = {
      id: own.id,
      spaceId: value.spaceId,
      taskId: value.taskId,
      executionId: value.executionId,
      onlineQuality: {
        ...attempted.onlineQuality,
        blindness: own.blindness,
        declaredBlindness: own.declaredBlindness,
      },
    }
    acknowledgedContent = contentSignature(attempted)
    ratingDirty.value = false
    supersedes.value = value.currentTailFeedbackId ?? ''
    error.value = '已核对上次评分保存成功；没有重复提交。修改需要更正的内容后，再明确选择当前链尾。'
  } else if (attempted)
    error.value = '未核对到与上次提交一致的本人反馈，请对照当前链尾和历史评分后重新核对。'
  unconfirmedRequest.value = null
  if (value.currentTailBlindness === 'NON_BLINDED' && context.value) {
    context.value = { ...context.value, knowledgeStatus: 'NON_BLINDED', blindedEligible: false }
    blindness.value = 'NON_BLINDED'
  }
}
function selectCurrentTail(id: string) {
  if (!currentHistory.value || currentHistory.value.currentTailFeedbackId !== id) return
  supersedes.value = id
  prepared.value = null
}
let listRead: AbortController | undefined,
  read: AbortController | undefined,
  write: AbortController | undefined
const identity = computed(
  () => `${spaceId.value}/${props.experimentId}/${assignmentId.value}/${target.value}`,
)
const formIdentity = computed(() =>
  JSON.stringify([
    identity.value,
    context.value,
    blindness.value,
    rubric.value,
    label.value,
    comment.value,
    codes.value,
    supersedes.value,
    serious.value,
    safetyReason.value,
  ]),
)
watch(formIdentity, () => {
  prepared.value = null
})
function clearContext() {
  ratingDirty.value = false
  markdownPreview.value = false
  read?.abort()
  write?.abort()
  context.value = null
  prepared.value = null
  saved.value = null
  loading.value = false
  busy.value = false
  rubric.value = ''
  label.value = ''
  comment.value = ''
  codes.value = ''
  supersedes.value = ''
  currentHistory.value = null
  unconfirmedRequest.value = null
  acknowledgedContent = null
  seriousnessReset()
}
function seriousnessReset() {
  serious.value = false
  safetyReason.value = ''
}
watch(identity, () => {
  clearContext()
  error.value = ''
})
watch(isOwner, (value) => {
  if (!value) {
    seriousnessReset()
    prepared.value = null
    write?.abort()
    busy.value = false
  }
})
async function loadCandidates(pageNum: number) {
  if (!allowed.value || !validOnlineId(props.experimentId)) return
  listRead?.abort()
  const controller = new AbortController()
  listRead = controller
  listing.value = true
  listError.value = ''
  try {
    const value = await searchOnlineReviewAssignments(
      props.experimentId,
      pageNum,
      controller.signal,
    )
    if (controller.signal.aborted || !allowed.value) return
    if (
      value.records.some(
        (item) => !validOnlineId(item.assignmentId) || !validOnlineId(item.documentId),
      )
    )
      throw new Error('评审候选身份无效')
    candidates.value = value
  } catch (e) {
    if (!controller.signal.aborted) {
      listError.value = normalizeApiError(e).message
      candidates.value.records = []
    }
  } finally {
    if (!controller.signal.aborted) listing.value = false
  }
}
async function selectAssignment(id: string) {
  if (!(await discardRating())) return
  assignmentId.value = id
  await nextTick()
  await loadContext()
}
async function loadContext() {
  if (!(await discardRating())) return
  clearContext()
  error.value = ''
  if (!allowed.value || !validOnlineId(props.experimentId) || !validOnlineId(assignmentId.value))
    return
  const controller = new AbortController(),
    captured = identity.value
  read = controller
  loading.value = true
  try {
    const value = await getOnlineReviewContext(
      props.experimentId,
      assignmentId.value,
      target.value,
      controller.signal,
    )
    if (controller.signal.aborted || captured !== identity.value || !allowed.value) return
    const original = value.originalContext
    if (
      value.assignmentId !== assignmentId.value ||
      original.assignmentId !== assignmentId.value ||
      value.goal.taskId !== original.taskId ||
      ![original.taskId, original.executionId, original.documentId].every((id) =>
        validOnlineId(id),
      ) ||
      !/^[0-9a-f]{64}$/.test(original.inputHash) ||
      !/^[0-9a-f]{64}$/.test(value.goal.inputSnapshotHash) ||
      typeof value.goal.instruction !== 'string' ||
      typeof original.inputContent !== 'string' ||
      !['BLINDED', 'NON_BLINDED', 'UNKNOWN'].includes(value.knowledgeStatus)
    )
      throw new Error('完整原始上下文不可验证')
    const evidenceId =
      target.value === 'TEXT' ? original.originalEvidenceId : original.originalSetId
    const hash = target.value === 'TEXT' ? original.originalEvidenceHash : original.originalSetHash
    if (
      !evidenceId ||
      !validOnlineId(evidenceId) ||
      !hash ||
      !/^[0-9a-f]{64}$/.test(hash) ||
      (target.value === 'TEXT'
        ? typeof original.originalText !== 'string'
        : original.originalSetId !== assignmentId.value || !original.originals?.length)
    )
      throw new Error('原始证据不可验证，不能评分')
    if (
      target.value === 'CHANGE_SET' &&
      (new Set(original.originals!.map((item) => item.evidenceId)).size !==
        original.originals!.length ||
        original.originals!.some(
          (item) =>
            !validOnlineId(item.evidenceId) ||
            !/^[0-9a-f]{64}$/.test(item.evidenceHash) ||
            !['CHANGE_REQUEST', 'DRAFT_DOCUMENT'].includes(item.evidenceType) ||
            typeof item.structureValid !== 'boolean' ||
            (item.originalChanges !== null && typeof item.originalChanges !== 'string') ||
            (item.originalContent !== null && typeof item.originalContent !== 'string'),
        ))
    )
      throw new Error('完整原始集合不可验证，不能评分')
    context.value = value
    blindness.value =
      value.blindedEligible && value.knowledgeStatus === 'BLINDED'
        ? 'BLINDED'
        : value.knowledgeStatus
  } catch (e) {
    if (!controller.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}
function prepare() {
  prepared.value = null
  error.value = ''
  if (!allowed.value || !context.value) return
  try {
    if (rubric.value === '' || !label.value) throw new Error('请主动选择质量等级和质量标签')
    if (unconfirmedRequest.value) throw new Error('上次响应未知，请先核对反馈链')
    if (!currentHistory.value) throw new Error('反馈链尚未核验，请等待或重新核对反馈链')
    if (
      currentHistory.value?.currentTailFeedbackId &&
      supersedes.value !== currentHistory.value.currentTailFeedbackId
    )
      throw new Error('已存在反馈，请明确引用当次核验的链尾追加更正')
    if (
      supersedes.value &&
      String(saved.value?.id ?? '') !== supersedes.value &&
      currentHistory.value?.currentTailFeedbackId !== supersedes.value
    )
      throw new Error('请先核对反馈链，再选择实际当前链尾')
    const original = context.value.originalContext,
      reasons = codes.value
        .trim()
        .split(/[\s,，]+/)
        .filter(Boolean)
    if (
      reasons.length > 20 ||
      new Set(reasons).size !== reasons.length ||
      reasons.some((code) => !/^[A-Z][A-Z0-9_]{0,63}$/.test(code))
    )
      throw new Error('原因代码须唯一、最多 20 个，并使用大写字母、数字或下划线')
    if (
      !Number.isInteger(rubric.value) ||
      rubric.value < 0 ||
      rubric.value > 4 ||
      (supersedes.value &&
        (!validOnlineId(supersedes.value) || (!comment.value.trim() && !reasons.length)))
    )
      throw new Error('等级或更正身份无效；更正必须提供理由')
    if (
      blindness.value === 'BLINDED' &&
      (!context.value.blindedEligible || context.value.knowledgeStatus !== 'BLINDED')
    )
      throw new Error('没有盲态资格，请如实声明知情状态')
    if (serious.value && (!isOwner.value || !safetyReason.value.trim()))
      throw new Error('严重安全确认需要当前 OWNER 及明确理由')
    prepared.value = {
      spaceId: spaceId.value,
      taskId: original.taskId,
      executionId: original.executionId,
      label: label.value,
      score: rubric.value / 4,
      comment: comment.value.trim() || null,
      onlineQuality: {
        assignmentId: assignmentId.value,
        originalEvidenceType: target.value === 'TEXT' ? 'ORIGINAL_TEXT' : 'ORIGINAL_CHANGE_SET',
        originalEvidenceId: (target.value === 'TEXT'
          ? original.originalEvidenceId
          : original.originalSetId)!,
        originalEvidenceHash: (target.value === 'TEXT'
          ? original.originalEvidenceHash
          : original.originalSetHash)!,
        rubricScore: rubric.value,
        blindness: blindness.value,
        supersedesFeedbackId: supersedes.value || null,
        reasonCodes: reasons,
        seriousSafetyEvent: serious.value,
        safetyReason: serious.value ? safetyReason.value.trim() : null,
      },
    }
    if (acknowledgedContent === contentSignature(prepared.value)) {
      prepared.value = null
      throw new Error('当前评分与原因已经保存，请先修改需要更正的内容')
    }
  } catch (e) {
    error.value = normalizeApiError(e).message
  }
}
async function submit() {
  if (!allowed.value || !prepared.value || busy.value) return
  const payload = prepared.value,
    captured = formIdentity.value,
    controller = new AbortController()
  write = controller
  busy.value = true
  error.value = ''
  try {
    const value = await createOnlineQuality(payload, controller.signal)
    if (controller.signal.aborted || !allowed.value || captured !== formIdentity.value) return
    if (
      String(value.spaceId) !== spaceId.value ||
      String(value.taskId) !== payload.taskId ||
      String(value.executionId) !== payload.executionId ||
      value.onlineQuality.assignmentId !== payload.onlineQuality.assignmentId ||
      value.onlineQuality.originalEvidenceHash !== payload.onlineQuality.originalEvidenceHash
    )
      throw new Error('反馈结果身份不一致，请核对当前反馈链')
    saved.value = value
    ratingDirty.value = false
    acknowledgedContent = contentSignature(payload)
    supersedes.value = String(value.id)
    prepared.value = null
    if (value.onlineQuality.blindness !== 'BLINDED' && context.value) {
      context.value = {
        ...context.value,
        knowledgeStatus: value.onlineQuality.blindness,
        blindedEligible: false,
      }
      blindness.value = value.onlineQuality.blindness
    }
  } catch (e) {
    if (!controller.signal.aborted) {
      unconfirmedRequest.value = payload
      prepared.value = null
      error.value = `${normalizeApiError(e).message}；响应未知时先核对反馈链，避免把重试当作更正。`
    }
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
watch(
  [spaceId, () => props.experimentId, allowed],
  () => {
    listRead?.abort()
    clearContext()
    candidates.value = { records: [], total: 0, pageNum: 1, pageSize: 10 }
    listError.value = ''
    error.value = ''
    listing.value = false
    assignmentId.value = ''
    void loadCandidates(1)
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  listRead?.abort()
  read?.abort()
  write?.abort()
})
</script>
<style scoped>
.original-review {
  display: grid;
  gap: 20px;
}
.original-markdown {
  overflow-wrap: anywhere;
  overflow-x: auto;
}
.selection,
.candidates {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: end;
}
.candidates {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr));
  align-items: stretch;
}
.candidates .el-button {
  min-width: 0;
  height: auto;
  margin-left: 0;
  padding: 12px;
}
.candidate-label {
  display: grid;
  gap: 6px;
  white-space: normal;
  overflow-wrap: anywhere;
  text-align: left;
}
.candidates .el-button[aria-pressed='true'] {
  border-width: 2px;
  border-color: var(--adw-color-primary);
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 12px 0;
}
fieldset {
  border: 0;
  padding: 0;
}
input,
select,
textarea {
  padding: 8px;
  font: inherit;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
}
.checkbox {
  flex-direction: row;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 500px;
  overflow: auto;
  background: var(--adw-surface-muted);
  padding: 16px;
  border-radius: var(--adw-radius-sm);
}
p {
  color: var(--adw-text-secondary);
  line-height: 1.6;
}
[role='alert'] {
  color: var(--adw-color-danger);
}
</style>
