<template>
  <el-dialog
    :model-value="open"
    title="创建线上实验草案"
    width="min(780px, 94vw)"
    top="5vh"
    body-class="online-experiment-create-body"
    :close-on-click-modal="false"
    @close="$emit('close')"
  >
    <p>只冻结候选和范围，不启动任务。两组仅改变 Agent 系统提示词，后续真实任务会产生业务副作用。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <ol ref="stepNavigation" class="create-steps" aria-label="创建步骤">
      <li
        v-for="(title, index) in ['范围与候选', '规则与预算', '核对冻结内容']"
        :key="title"
        :aria-current="step === index ? 'step' : undefined"
      >
        {{ index + 1 }}. {{ title }}
      </li>
    </ol>
    <form class="create-form" novalidate @submit.prevent="prepare">
      <fieldset :disabled="busy || !allowed">
        <section v-show="step === 0">
          <h3>范围与候选</h3>
          <label
            >实验名称<input v-model="name" aria-label="实验名称" maxlength="100" required
          /></label>
          <label
            >Agent<select v-model="agentId" aria-label="实验Agent" required>
              <option value="">选择已启用 Agent</option>
              <option v-for="agent in agents" :key="String(agent.id)" :value="String(agent.id)">
                {{ agent.name }}
              </option>
            </select></label
          >
          <el-button :loading="documentsLoading" @click="loadDocuments">从空间文档中选择</el-button>
          <p v-if="documentsError" role="alert">{{ documentsError }}</p>
          <section
            v-if="documentOptions !== null"
            class="document-picker"
            aria-label="实验文档选择"
          >
            <label>搜索文档名称<input v-model="documentKeyword" aria-label="搜索实验文档" /></label>
            <div class="document-picker__list">
              <label v-for="item in visibleDocuments" :key="String(item.id)" class="check">
                <input
                  type="checkbox"
                  :checked="documentIds.includes(String(item.id))"
                  @change="toggleDocument(String(item.id))"
                />
                <span
                  >{{ item.title }} · {{ item.docType === 'FORMAL' ? '正式文档' : '草稿' }}</span
                >
              </label>
              <p v-if="!visibleDocuments.length">没有匹配的可选文档。</p>
            </div>
          </section>
          <p>已选择 {{ uniqueDocumentIds.length }} 份文档。创建与启动时仍会重新核验范围和权限。</p>
          <details>
            <summary>按文档 ID 核对或补充</summary>
            <label
              >文档范围 ID<textarea
                v-model="documents"
                aria-label="文档范围ID"
                rows="3"
                placeholder="每行一个文档 ID，或用逗号分隔"
              />
            </label>
          </details>
          <p>范围和比例在创建后固定，同一文档始终在同一组。文档版本由每次真实任务分别冻结。</p>
          <label
            >候选系统提示词<textarea
              v-model="prompt"
              aria-label="候选系统提示词"
              rows="5"
              required
            />
          </label>
          <p v-if="baselineLoading">正在读取当前 Agent 的 Prompt 参考。</p>
          <label v-if="baseline"
            >当前 Agent 系统提示词参考（配置版本 {{ baseline.configVersion }}）
            <textarea
              :value="baseline.systemPrompt"
              aria-label="当前基线提示词参考"
              rows="5"
              readonly
            />
          </label>
          <p>
            参考仅用于创建前对照。两份最终系统提示词由服务端同时冻结；保存草案后，可在详情核对完整
            Prompt 行差异及非 Prompt 摘要。不会覆盖生产 Agent。
          </p>
        </section>
        <section v-show="step === 1">
          <h3>预算与分析要求</h3>
          <div class="row">
            <label
              >候选文档概率（%）<input
                v-model="weightPercent"
                aria-label="候选文档概率百分比"
                type="number"
                min="0.01"
                max="99.99"
                step="0.01"
                required
            /></label>
            <label
              >任务数上限<input
                v-model.number="tasks"
                aria-label="任务数上限"
                type="number"
                min="1"
                max="10000"
                required
            /></label>
          </div>
          <p>
            目标文档概率为 {{ weightPercent }}%，对应
            {{ Number.isInteger(weight) ? weight : '无效' }} 万分比；实际任务比例可能不同。
          </p>
          <div class="row">
            <label
              >总授权 Token<input
                v-model="total"
                aria-label="总授权Token"
                inputmode="numeric"
                required /></label
            ><label
              >每任务 Token 上限<input
                v-model="perTask"
                aria-label="每任务Token上限"
                inputmode="numeric"
                required
            /></label>
          </div>
          <div class="row">
            <DurationInput
              v-model="assignmentSeconds"
              label="分配窗口"
              :disabled="busy || !allowed"
            />
            <DurationInput
              v-model="observationSeconds"
              label="观察窗口"
              :disabled="busy || !allowed"
            />
          </div>
          <p>
            分配窗口 {{ readableWindow(assignmentSeconds) }}；停止后观察
            {{ readableWindow(observationSeconds) }}。精确秒数随草案冻结。
          </p>
          <label
            >分析模式<select v-model="mode" aria-label="分析模式">
              <option value="ENGINEERING">小样本工程验证（不能作比较结论）</option>
              <option value="DESCRIPTIVE">描述性比较（受样本与覆盖门禁约束）</option>
            </select></label
          >
          <label
            >最小有价值绝对差（比例）<input v-model="mde" aria-label="最小有价值差异" required
          /></label>
          <p>绝对比例差 0.1 表示 10 个百分点，并非相对提升 10%。</p>
          <label
            >差异的业务理由<textarea
              v-model="mdeReason"
              aria-label="差异业务理由"
              rows="2"
              maxlength="2000"
              required
            />
          </label>
          <label class="check"
            ><input v-model="humanRequired" type="checkbox" />结论必须有每组至少 80%
            的有效盲态原始质量评价</label
          >
          <section aria-label="冻结原始质量规则">
            <h3>原始质量主规则</h3>
            <el-button @click="pickRule(null)">选择已发布原始质量评估器</el-button>
            <p>
              {{
                version
                  ? `${versionName} / 版本 ${version.versionNo}`
                  : '需要已发布的线上原始文本或变更评估器'
              }}
            </p>
            <p v-if="!version">
              若没有可选版本，请先在<a
                :href="`/spaces/${spaceId}/evaluation/evaluators`"
                target="_blank"
                rel="noopener"
                >评估器管理</a
              >发布线上原始文本或变更评估器。可在新页面配置后回来继续，当前输入会保留。
            </p>
            <template v-if="version?.evaluatorKey === 'online-original-text-assertion'">
              <p>以下断言应用于全部范围文档；必须全部通过。未取得原始文本仍算缺失。</p>
              <div v-for="(assertion, index) in assertions" :key="index" class="assertion">
                <select v-model="assertion.operator" :aria-label="`断言${index + 1}运算`">
                  <option value="CONTAINS">包含</option>
                  <option value="NOT_CONTAINS">不包含</option>
                  <option value="EXACT">完全一致</option>
                  <option value="SHA256">正文摘要一致</option>
                </select>
                <input
                  v-model="assertion.value"
                  :aria-label="`断言${index + 1}内容`"
                  maxlength="2000"
                />
                <el-button :disabled="assertions.length === 1" @click="assertions.splice(index, 1)"
                  >移除</el-button
                >
              </div>
              <el-button
                :disabled="assertions.length >= 50"
                @click="assertions.push({ operator: 'CONTAINS', value: '' })"
                >增加断言</el-button
              >
            </template>
            <template v-else-if="version">
              <label
                >必需原始产物<select v-model="outputType" aria-label="必需原始产物">
                  <option value="CHANGE_REQUEST">变更提案</option>
                  <option value="DRAFT_DOCUMENT">草稿文档</option>
                </select></label
              >
              <p>结构和原始目标必须有效。目标正文摘要可按文档填写，留空仅检查结构。</p>
              <label v-for="id in uniqueDocumentIds" :key="id"
                >文档 #{{ id }} 目标正文 SHA-256<input
                  v-model="targetHashes[id]"
                  :aria-label="`文档${id}目标摘要`"
                  maxlength="64"
              /></label>
            </template>
            <details v-if="version && documentIds.length">
              <summary>逐文档覆盖主规则期望</summary>
              <p>未覆盖的文档使用上方共同要求。可按评估器契约填写完整 JSON；两组使用同一映射。</p>
              <label
                >覆盖文档<select v-model="overrideDocument" aria-label="主规则覆盖文档">
                  <option value="">选择文档</option>
                  <option v-for="id in uniqueDocumentIds" :key="id" :value="id">{{ id }}</option>
                </select></label
              >
              <label v-if="overrideDocument"
                >该文档完整期望（留空恢复共同要求）<textarea
                  v-model="primaryOverrides[overrideDocument]"
                  aria-label="主规则文档期望"
                  rows="4"
                />
              </label>
            </details>
          </section>
          <section aria-label="其他冻结规则">
            <h3>其他主规则、辅助与保护规则</h3>
            <p>
              全部主规则必须通过才计为文档主指标有效；辅助和保护规则单独展示，不代替原始质量。缺失不能改成通过，任务成功也不能代替产物质量。
            </p>
            <details v-for="(rule, index) in additionalRules" :key="index" open>
              <summary>
                规则 {{ index + 2 }} · {{ rule.versionName || '待配置' }} ·
                {{ { PRIMARY: '主规则', AUXILIARY: '辅助规则', GUARDRAIL: '保护规则' }[rule.role] }}
                · {{ rule.comparison }} {{ rule.threshold }} · {{ uniqueDocumentIds.length }} 份文档
              </summary>
              <label
                >稳定规则标识<input
                  v-model="rule.ruleKey"
                  :aria-label="`附加规则${index + 1}标识`"
                  maxlength="64"
              /></label>
              <label
                >分析角色<select v-model="rule.role" :aria-label="`附加规则${index + 1}角色`">
                  <option value="PRIMARY">主规则</option>
                  <option value="AUXILIARY">辅助规则</option>
                  <option value="GUARDRAIL">保护规则</option>
                </select></label
              >
              <el-button @click="pickRule(index)">选择规则 {{ index + 2 }} 的已发布版本</el-button>
              <p>{{ rule.versionName || '尚未选择' }}</p>
              <label v-if="rule.version?.evaluatorKey === 'online-document-change-validator'"
                >变更指标<select
                  v-model="rule.accuracy"
                  :aria-label="`附加规则${index + 1}变更指标`"
                >
                  <option :value="false">结构有效</option>
                  <option :value="true">目标正文准确比例</option>
                </select></label
              >
              <label
                >比较方式<select
                  v-model="rule.comparison"
                  :aria-label="`附加规则${index + 1}比较方式`"
                >
                  <option value="EQ">等于</option>
                  <option value="GE">大于等于</option>
                  <option value="LE">小于等于</option>
                </select></label
              >
              <label
                >阈值（比例填十进制文本，布尔填 true/false）<input
                  v-model="rule.threshold"
                  :aria-label="`附加规则${index + 1}阈值`"
              /></label>
              <label
                >共同期望 JSON<textarea
                  v-model="rule.expected"
                  :aria-label="`附加规则${index + 1}共同期望`"
                  rows="4"
                />
              </label>
              <p>
                文本：assertions 断言列表；变更：requiredOutputType 与可选
                targetContentSha256；任务终态/账本规则使用空对象 {}。
              </p>
              <label
                >覆盖文档<select
                  v-model="rule.documentId"
                  :aria-label="`附加规则${index + 1}覆盖文档`"
                >
                  <option value="">使用共同期望</option>
                  <option v-for="id in uniqueDocumentIds" :key="id" :value="id">{{ id }}</option>
                </select></label
              >
              <label v-if="rule.documentId"
                >文档完整期望（留空使用共同要求）<textarea
                  v-model="rule.overrides[rule.documentId]"
                  :aria-label="`附加规则${index + 1}文档期望`"
                  rows="4"
                />
              </label>
              <el-button @click="additionalRules.splice(index, 1)"
                >移除规则 {{ index + 2 }}</el-button
              >
            </details>
            <el-button
              :disabled="additionalRules.length >= ONLINE_MAX_RULES - 1"
              @click="additionalRules.push(newOnlineRule(++ruleSequence))"
              >增加冻结规则</el-button
            >
          </section>
        </section>
        <section v-show="step === 2" aria-label="固定保护与样本要求">
          <h3>固定保护与样本要求</h3>
          <p>
            每组一个执行槽；独立文档每组至少30个、主规则有效覆盖至少80%。低样本工程验证不能作比较结论。
          </p>
          <p>
            各组最近20个已确认终态 Task
            中失败比例达到30%会暂停准入；未知结算达到300秒、依赖或资源漂移也会暂停。未知成本与用量不填零，费用和真实业务变更不自动回滚。
          </p>
          <p>
            SRM至少100个独立文档且每组期望数至少10；阈值0.001，每300秒复核。它检查分配可信度，不证明Prompt效果。
          </p>
          <p>
            总授权、单任务上限、任务数、分配截止与观察窗口共同限制准入；预算占用与最终结算分别核对。上述保护由服务端冻结，不允许临时降低门槛。
          </p>
        </section>
        <el-button v-if="step === 0" type="primary" @click="advanceScope"
          >继续：规则与预算</el-button
        >
        <el-button
          v-if="step === 1"
          native-type="submit"
          type="primary"
          :disabled="!version || baselineLoading || !baseline"
          >核对草案</el-button
        >
      </fieldset>
    </form>
    <section v-if="prepared && step === 2" aria-label="创建草案确认">
      <h3>核对冻结范围与预算</h3>
      <p>
        {{ prepared.name }}，{{ prepared.documentIds.length }} 个文档，候选概率
        {{ prepared.candidateWeightBps / 100 }}%。
      </p>
      <p>
        Agent：{{
          agents.find((item) => String(item.id) === prepared!.agentId)?.name || prepared.agentId
        }}；{{ prepared.rules.length }} 条冻结规则。
      </p>
      <details>
        <summary>核对全部范围与 Prompt</summary>
        <ul>
          <li v-for="id in prepared.documentIds" :key="id">
            {{ documentOptions?.find((item) => String(item.id) === id)?.title || `文档 #${id}` }}
          </li>
        </ul>
        <h4>当前基线参考（最终配置以创建后服务端冻结为准）</h4>
        <pre>{{ baseline?.systemPrompt }}</pre>
        <h4>候选完整系统提示词</h4>
        <pre>{{ prepared.candidateAgentPrompt }}</pre>
      </details>
      <p>
        分配窗口 {{ readableWindow(prepared.assignmentWindowSeconds) }}（{{
          prepared.assignmentWindowSeconds
        }}
        秒），停止后观察 {{ readableWindow(prepared.completionObservationSeconds) }}（{{
          prepared.completionObservationSeconds
        }}
        秒）。
      </p>
      <p>
        分析模式：{{
          prepared.analysisPlan.mode === 'ENGINEERING' ? '工程验证' : '描述性比较'
        }}；最小有价值绝对差 {{ prepared.analysisPlan.mdeAbsoluteRatio }}；理由：{{
          prepared.analysisPlan.mdeReason
        }}。
      </p>
      <p>
        原始质量主规则：{{ versionName }} v{{
          version?.versionNo
        }}；其余规则按上一步配置冻结，正式文档仍须人工审批。
      </p>
      <p>
        授权 {{ prepared.authorizedTokenBudget }} Token，每任务最多预留
        {{ prepared.perTaskTokenLimit }}；任务数上限 {{ prepared.maxTaskCount }}。
      </p>
      <p>同文档重复任务不增加独立样本。确认创建后不能改比例、范围或评分要求。</p>
      <el-button :disabled="busy || !allowed" type="primary" @click="create">{{
        busy ? '正在创建' : '确认创建草案'
      }}</el-button>
    </section>
    <PublishedVersionPicker
      :open="picker"
      :space-id="spaceId"
      kind="evaluators"
      :exclude-ids="[]"
      :evaluator-keys="pickerIndex === null ? ONLINE_EVALUATOR_KEYS : ONLINE_LIVE_RULE_KEYS"
      dialog-width="min(760px, 94vw)"
      @close="picker = false"
      @select="selectVersion"
    />
    <template #footer
      ><el-button v-if="step > 0" :disabled="busy" @click="previousStep">返回上一步</el-button
      ><el-button :disabled="busy" @click="$emit('close')">关闭</el-button></template
    >
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElDialog } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import { getAgent, listAgents, type AgentOption } from '@/features/agent/api/agent-api'
import { listDocumentTree } from '@/features/document/api/document-api'
import type { DocumentTreeNode } from '@/features/document/types'
import type { AgentDetail } from '@/features/agent/types'
import PublishedVersionPicker from '../components/PublishedVersionPicker.vue'
import DurationInput from '../components/DurationInput.vue'
import type { DatasetVersion, EvaluatorVersion, TestCaseVersion } from '../types'
import { createOnlineExperiment, queryPublishedOnlineVersions } from './api'
import {
  ONLINE_MAX_RULES,
  ONLINE_LIVE_RULE_KEYS,
  newOnlineRule,
  onlineDecimal,
  onlineExpected,
  onlineRuleFromDraft,
  type OnlineRuleDraft,
} from './rule-draft'
import { ONLINE_EVALUATOR_KEYS, validOnlineId } from './presentation'
import type { OnlineCreateRequest, OnlineExperiment } from './types'

const props = defineProps<{ open: boolean; spaceId: string; allowed: boolean }>()
const emit = defineEmits<{ close: []; created: [value: OnlineExperiment] }>()
const step = ref(0)
const stepNavigation = ref<HTMLElement | null>(null)
watch(step, async () => {
  await nextTick()
  const body = stepNavigation.value?.closest('.online-experiment-create-body')
  if (body) body.scrollTop = 0
})
function previousStep() {
  step.value = Math.max(0, step.value - 1)
  prepared.value = null
}
function advanceScope() {
  error.value = ''
  if (
    !name.value.trim() ||
    [...name.value.trim()].length > 100 ||
    !prompt.value.trim() ||
    !baseline.value ||
    baselineLoading.value ||
    !validOnlineId(agentId.value)
  ) {
    error.value = '请补齐实验名称、已启用 Agent 和候选提示词，并等待读取基线参考。'
    return
  }
  if (
    !documentIds.value.length ||
    documentIds.value.length > 1000 ||
    documentIds.value.some((id) => !validOnlineId(id)) ||
    uniqueDocumentIds.value.length !== documentIds.value.length
  ) {
    error.value = '请选择 1～1000 份文档，ID 必须有效且不重复。'
    return
  }
  step.value = 1
}
const name = ref(''),
  agentId = ref(''),
  documents = ref(''),
  prompt = ref(''),
  total = ref(''),
  perTask = ref(''),
  tasks = ref(100),
  weight = ref(5000),
  assignmentSeconds = ref(604800),
  observationSeconds = ref(172800)
const weightPercent = ref('50')
watch(weightPercent, (value) => {
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(String(value))
  weight.value = match ? Number(match[1]) * 100 + Number((match[2] || '').padEnd(2, '0')) : NaN
})
const documentOptions = ref<DocumentTreeNode[] | null>(null),
  documentKeyword = ref(''),
  documentsLoading = ref(false),
  documentsError = ref('')
let documentsRead: AbortController | undefined
const visibleDocuments = computed(
  () =>
    documentOptions.value?.filter((item) =>
      item.title.toLowerCase().includes(documentKeyword.value.trim().toLowerCase()),
    ) || [],
)
async function loadDocuments() {
  if (!props.allowed || !props.open) return
  documentsRead?.abort()
  const current = new AbortController(),
    space = props.spaceId
  documentsRead = current
  documentsLoading.value = true
  documentsError.value = ''
  try {
    const tree = await listDocumentTree(space, { status: 'NORMAL', signal: current.signal })
    if (current.signal.aborted || !props.allowed || !props.open || props.spaceId !== space) return
    const flatten = (nodes: DocumentTreeNode[]): DocumentTreeNode[] =>
      nodes.flatMap((node) => [
        ...(node.nodeType === 'DOCUMENT' && validOnlineId(String(node.id)) ? [node] : []),
        ...flatten(node.children || []),
      ])
    documentOptions.value = flatten(tree)
  } catch (failure) {
    if (!current.signal.aborted) documentsError.value = normalizeApiError(failure).message
  } finally {
    if (!current.signal.aborted) documentsLoading.value = false
  }
}
function toggleDocument(id: string) {
  const selected = new Set(documentIds.value)
  if (selected.has(id)) selected.delete(id)
  else selected.add(id)
  documents.value = [...selected].join('\n')
}
function readableWindow(seconds: number) {
  if (!Number.isInteger(seconds) || seconds < 0) return '待填写有效秒数'
  if (seconds === 0) return '0 秒'
  return (
    [
      [86400, '天'],
      [3600, '小时'],
      [60, '分钟'],
      [1, '秒'],
    ] as const
  )
    .map(([unit, label]) => {
      const count = Math.floor(seconds / Number(unit))
      seconds %= Number(unit)
      return count ? `${count} ${label}` : ''
    })
    .filter(Boolean)
    .join(' ')
}
const mode = ref<'ENGINEERING' | 'DESCRIPTIVE'>('ENGINEERING'),
  mde = ref('0.1'),
  mdeReason = ref(''),
  humanRequired = ref(true),
  outputType = ref('CHANGE_REQUEST')
const assertions = ref([{ operator: 'CONTAINS', value: '' }]),
  targetHashes = ref<Record<string, string>>({}),
  version = ref<EvaluatorVersion | null>(null),
  versionName = ref(''),
  picker = ref(false)
const agents = ref<AgentOption[]>([]),
  busy = ref(false),
  error = ref(''),
  prepared = ref<OnlineCreateRequest | null>(null)
const baseline = ref<AgentDetail | null>(null),
  baselineLoading = ref(false),
  additionalRules = ref<OnlineRuleDraft[]>([]),
  primaryOverrides = ref<Record<string, string>>({}),
  overrideDocument = ref(''),
  pickerIndex = ref<number | null>(null)
let baselineRead: AbortController | undefined,
  ruleSequence = 1
let pending: AbortController | undefined, retry: { fingerprint: string; key: string } | undefined
const documentIds = computed(() =>
  documents.value
    .trim()
    .split(/[\s,，]+/)
    .filter(Boolean),
)
const uniqueDocumentIds = computed(() =>
  [...new Set(documentIds.value)].filter((id) => validOnlineId(id)),
)
const fingerprint = computed(() =>
  JSON.stringify([
    props.spaceId,
    name.value,
    agentId.value,
    documents.value,
    prompt.value,
    total.value,
    perTask.value,
    tasks.value,
    weight.value,
    assignmentSeconds.value,
    observationSeconds.value,
    mode.value,
    mde.value,
    mdeReason.value,
    humanRequired.value,
    version.value,
    assertions.value,
    outputType.value,
    targetHashes.value,
    primaryOverrides.value,
    additionalRules.value,
    baseline.value,
  ]),
)
watch(fingerprint, () => {
  prepared.value = null
})
watch([() => props.spaceId, () => props.allowed], () => {
  step.value = 0
  documentsRead?.abort()
  documentOptions.value = null
  documentKeyword.value = ''
  documentsError.value = ''
  documentsLoading.value = false
  name.value = ''
  agentId.value = ''
  documents.value = ''
  prompt.value = ''
  total.value = ''
  perTask.value = ''
  version.value = null
  versionName.value = ''
  targetHashes.value = {}
  assertions.value = [{ operator: 'CONTAINS', value: '' }]
  baselineRead?.abort()
  baseline.value = null
  additionalRules.value = []
  primaryOverrides.value = {}
  overrideDocument.value = ''
  ruleSequence = 1
})
watch([agentId, () => props.open, () => props.allowed, () => props.spaceId], async () => {
  baselineRead?.abort()
  baseline.value = null
  baselineLoading.value = false
  if (!props.open || !props.allowed || !validOnlineId(agentId.value)) return
  const current = new AbortController(),
    captured = agentId.value
  baselineRead = current
  baselineLoading.value = true
  try {
    const value = await getAgent(captured, current.signal)
    if (current.signal.aborted) return
    if (
      String(value.id) !== captured ||
      String(value.spaceId) !== props.spaceId ||
      value.status !== 'ENABLED' ||
      typeof value.systemPrompt !== 'string'
    )
      throw new Error('当前Agent参考不可核对')
    baseline.value = value
  } catch (failure) {
    if (!current.signal.aborted) error.value = normalizeApiError(failure).message
  } finally {
    if (baselineRead === current) baselineLoading.value = false
  }
})
watch(
  [() => props.open, () => props.spaceId, () => props.allowed],
  async () => {
    pending?.abort()
    prepared.value = null
    picker.value = false
    busy.value = false
    error.value = ''
    agents.value = []
    retry = undefined
    if (!props.open || !props.allowed) return
    const controller = new AbortController()
    pending = controller
    try {
      const values = await listAgents(props.spaceId, controller.signal)
      if (!controller.signal.aborted)
        agents.value = values.filter(
          (item) => String(item.spaceId) === props.spaceId && item.status === 'ENABLED',
        )
    } catch (e) {
      if (!controller.signal.aborted) error.value = normalizeApiError(e).message
    }
  },
  { immediate: true },
)
function selectVersion(value: EvaluatorVersion | TestCaseVersion | DatasetVersion, label: string) {
  if (!('evaluatorKey' in value)) return
  if (pickerIndex.value === null) {
    version.value = value
    versionName.value = label
  } else {
    const rule = additionalRules.value[pickerIndex.value]
    if (!rule) return
    rule.version = value
    rule.versionName = label
    rule.accuracy = false
    rule.threshold = value.evaluatorKey === ONLINE_LIVE_RULE_KEYS[0] ? '1' : 'true'
    rule.expected =
      value.evaluatorKey === ONLINE_LIVE_RULE_KEYS[1]
        ? '{"requiredOutputType":"CHANGE_REQUEST"}'
        : '{}'
    rule.overrides = {}
  }
  picker.value = false
}
function pickRule(index: number | null) {
  pickerIndex.value = index
  picker.value = true
}
function prepare() {
  prepared.value = null
  error.value = ''
  if (!props.allowed) return
  try {
    const ids = documentIds.value
    if (
      !validOnlineId(agentId.value) ||
      !agents.value.some((agent) => String(agent.id) === agentId.value) ||
      !baseline.value ||
      baselineLoading.value
    )
      throw new Error('请选择当前空间的已启用 Agent')
    if (
      !ids.length ||
      ids.length > 1000 ||
      ids.some((id) => !validOnlineId(id)) ||
      new Set(ids).size !== ids.length
    )
      throw new Error('文档 ID 须唯一、有效，数量为 1～1000')
    if (
      !validOnlineId(total.value) ||
      !validOnlineId(perTask.value) ||
      BigInt(total.value) < BigInt(perTask.value)
    )
      throw new Error('预算须为正整数文本，总授权不能小于单任务上限')
    if (
      !Number.isInteger(tasks.value) ||
      tasks.value < 1 ||
      tasks.value > 10000 ||
      !Number.isInteger(weight.value) ||
      weight.value < 1 ||
      weight.value > 9999
    )
      throw new Error('请检查任务数上限和候选概率')
    if (
      !Number.isInteger(assignmentSeconds.value) ||
      assignmentSeconds.value < 1 ||
      assignmentSeconds.value > 2592000 ||
      !Number.isInteger(observationSeconds.value) ||
      observationSeconds.value < 0 ||
      observationSeconds.value > 604800
    )
      throw new Error('请检查分配及观察窗口')
    const normalizedMde = onlineDecimal(mde.value)
    if (
      !/^(0\.\d+|1)$/.test(normalizedMde) ||
      !/[1-9]/.test(normalizedMde) ||
      !mdeReason.value.trim()
    )
      throw new Error('差异比例须大于 0、至多 1，且填写业务理由')
    if (
      !name.value.trim() ||
      [...name.value.trim()].length > 100 ||
      !prompt.value.trim() ||
      !version.value
    )
      throw new Error('请补齐名称、候选提示词和已发布主规则')
    const isText = version.value.evaluatorKey === ONLINE_EVALUATOR_KEYS[0]
    if (
      isText &&
      (!assertions.value.length ||
        assertions.value.some(
          (item) =>
            [...item.value].length > 2000 ||
            (item.operator === 'SHA256' && !/^[0-9a-f]{64}$/.test(item.value)),
        ))
    )
      throw new Error('请检查原始文本断言及摘要格式')
    if (
      !isText &&
      ids.some((id) => targetHashes.value[id] && !/^[0-9a-f]{64}$/.test(targetHashes.value[id]))
    )
      throw new Error('目标正文摘要须为 64 位小写十六进制')
    const payload = {
      spaceId: props.spaceId,
      agentId: agentId.value,
      name: name.value.trim(),
      documentIds: [...ids],
      candidateAgentPrompt: prompt.value,
      candidateWeightBps: weight.value,
      authorizedTokenBudget: total.value,
      maxTaskCount: tasks.value,
      perTaskTokenLimit: perTask.value,
      assignmentWindowSeconds: assignmentSeconds.value,
      completionObservationSeconds: observationSeconds.value,
      analysisPlan: {
        mode: mode.value,
        mdeAbsoluteRatio: normalizedMde,
        mdeReason: mdeReason.value.trim(),
        humanQualityRequired: humanRequired.value,
      },
      rules: [
        {
          ruleKey: 'original-quality',
          role: 'PRIMARY' as const,
          evaluatorVersionId: String(version.value.id),
          metricKey: isText
            ? 'evaluation.online-original-text.pass-ratio'
            : 'evaluation.online-document-change.valid',
          valueType: isText ? ('NUMBER' as const) : ('BOOLEAN' as const),
          unit: isText ? 'ratio' : 'boolean',
          comparison: 'EQ' as const,
          threshold: isText ? '1' : true,
          evidenceTarget: isText ? ('ORIGINAL_TEXT' as const) : ('ORIGINAL_CHANGE' as const),
          expectedBindings: ids.map((documentId) => ({
            documentId,
            expectedJson: onlineExpected(
              version.value!.evaluatorKey ?? '',
              primaryOverrides.value[documentId]?.trim()
                ? JSON.parse(primaryOverrides.value[documentId]!)
                : isText
                  ? {
                      assertions: assertions.value.map((item) => ({
                        field: 'originalText',
                        ...item,
                      })),
                    }
                  : {
                      requiredOutputType: outputType.value,
                      ...(targetHashes.value[documentId]
                        ? { targetContentSha256: targetHashes.value[documentId] }
                        : {}),
                    },
            ),
          })),
        },
        ...additionalRules.value.map((rule) => onlineRuleFromDraft(rule, ids)),
      ],
    }
    if (
      payload.rules.length > ONLINE_MAX_RULES ||
      new Set(payload.rules.map((rule) => rule.ruleKey)).size !== payload.rules.length
    )
      throw new Error('规则标识须唯一，最多50条')
    const identity = JSON.stringify(payload)
    if (retry?.fingerprint !== identity)
      retry = { fingerprint: identity, key: window.crypto.randomUUID() }
    prepared.value = { ...payload, clientRequestKey: retry.key }
    step.value = 2
  } catch (e) {
    error.value = normalizeApiError(e).message
  }
}
async function create() {
  if (!prepared.value || busy.value || !props.allowed || !version.value) return
  const payload = prepared.value,
    identity = fingerprint.value,
    chosen = version.value
  pending?.abort()
  const controller = new AbortController()
  pending = controller
  busy.value = true
  error.value = ''
  try {
    const selected = [chosen, ...additionalRules.value.map((rule) => rule.version!)]
    const selectedById = new Map(selected.map((item) => [String(item.id), item]))
    const published = await queryPublishedOnlineVersions(
      props.spaceId,
      [...selectedById.keys()],
      controller.signal,
    )
    if (
      controller.signal.aborted ||
      identity !== fingerprint.value ||
      !props.allowed ||
      !props.open
    )
      return
    if (
      published.length !== selectedById.size ||
      new Set(published.map((item) => String(item.id))).size !== selectedById.size ||
      published.some((item) => {
        const original = selectedById.get(String(item.id))
        return (
          !original ||
          String(item.spaceId) !== props.spaceId ||
          item.status !== 'PUBLISHED' ||
          item.contentHash !== original.contentHash ||
          item.evaluatorKey !== original.evaluatorKey ||
          String(item.evaluatorId) !== String(original.evaluatorId)
        )
      })
    )
      throw new Error('规则版本或父资源已不可用，请重新选择')
    const value = await createOnlineExperiment(payload, controller.signal)
    if (
      controller.signal.aborted ||
      identity !== fingerprint.value ||
      !props.allowed ||
      !props.open
    )
      return
    if (
      value.spaceId !== props.spaceId ||
      value.agentId !== payload.agentId ||
      value.manifestSchemaVersion !== 2
    )
      throw new Error('创建结果身份不一致，请通过相同请求重试')
    emit('created', value)
  } catch (e) {
    if (!controller.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
onBeforeUnmount(() => {
  documentsRead?.abort()
  pending?.abort()
  baselineRead?.abort()
})
</script>
<style scoped>
:global(.online-experiment-create-body) {
  max-height: 70vh;
  overflow-y: auto;
}
fieldset {
  border: 0;
  padding: 0;
}
label {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin: 12px 0;
}
input,
select,
textarea {
  box-sizing: border-box;
  min-width: 0;
  padding: 8px;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  font: inherit;
}
.row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.create-steps {
  position: sticky;
  top: 0;
  z-index: 1;
  background: var(--adw-surface);
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  list-style: none;
  padding: 8px 0;
  color: var(--adw-text-secondary);
}
.create-steps [aria-current='step'] {
  color: var(--adw-color-primary);
  font-weight: 600;
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-height: 240px;
  overflow-y: auto;
  background: var(--adw-surface-muted);
  padding: 12px;
}
.document-picker {
  padding: 12px;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
}
.document-picker__list {
  max-height: 220px;
  overflow-y: auto;
}
summary {
  cursor: pointer;
}
.check {
  flex-direction: row;
  align-items: center;
}
.assertion {
  display: flex;
  gap: 8px;
  margin: 8px 0;
}
.assertion input {
  flex: 1;
}
[role='alert'] {
  color: var(--adw-color-danger);
}
@media (max-width: 650px) {
  .row {
    grid-template-columns: 1fr;
  }
  .assertion {
    flex-wrap: wrap;
  }
}
</style>
