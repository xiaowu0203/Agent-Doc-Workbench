<template>
  <section aria-label="线上报告与结论" class="report-panel">
    <h3>线上报告 · 固定版本</h3>
    <p>{{ ONLINE_DESCRIPTIVE_NOTICE }}</p>
    <section v-if="experiment.decision" aria-label="既有人工结论">
      <p>
        已记录人工结论：{{ evaluationStatus('decision', experiment.decision).label }}。 依据报告
        {{
          experiment.decisionReportRevision == null
            ? '版本未知'
            : `v${experiment.decisionReportRevision}`
        }}；当前报告不改变既有结论及其依据。
      </p>
      <el-button
        v-if="experiment.decisionReportRevision != null"
        :disabled="busy || !readable"
        @click="readDecisionReport"
        >查看结论依据报告</el-button
      >
      <p
        v-if="
          report &&
          experiment.decisionReportRevision != null &&
          report.revision !== experiment.decisionReportRevision
        "
      >
        当前查看 v{{ report.revision }}，与既有决定依据 v{{ experiment.decisionReportRevision }}
        不同；当前版本不会替换决定依据。
      </p>
      <p
        v-else-if="
          report &&
          experiment.decisionReportHash &&
          report.reportHash !== experiment.decisionReportHash
        "
        role="alert"
      >
        当前报告摘要与决定依据不一致，请核对原始依据。
      </p>
      <details>
        <summary>决定依据摘要</summary>
        <p>{{ experiment.decisionReportHash || '历史决定未提供报告摘要，不能推测其身份。' }}</p>
      </details>
    </section>
    <div class="revision-index" aria-label="报告版本记录">
      <div class="row">
        <label
          >历史报告<select
            aria-label="选择历史报告"
            :value="revision"
            :disabled="indexBusy || busy || !readable || !revisions.length"
            @change="selectRevision"
          >
            <option v-if="!revisions.some((item) => item.revision === revision)" :value="revision">
              {{
                requestedRevision === undefined && !report
                  ? '请选择已生成版本'
                  : `指定版本 v${revision}（不在本页）`
              }}
            </option>
            <option v-for="item in revisions" :key="item.revision" :value="item.revision">
              v{{ item.revision }} · {{ formatBusinessTime(item.generatedAt, '生成时间未知')
              }}{{ item.decisionReference ? ' · 已被人工结论引用' : '' }}
            </option>
          </select></label
        >
        <el-button :disabled="indexBusy || busy || !readable" @click="loadRevisions(1)"
          >刷新版本记录</el-button
        >
      </div>
      <p v-if="indexBusy" role="status">正在读取版本记录……</p>
      <p v-else-if="indexError" role="alert">
        版本记录读取失败：{{ indexError }}。可重试或按已知版本读取。
      </p>
      <p v-else-if="!revisionTotal">尚无已冻结报告。</p>
      <p v-if="selectedMetadata" class="report-hint">
        v{{ selectedMetadata.revision }} · 观察截止
        <BusinessTime
          :value="selectedMetadata.observationCutoffAt"
          empty-text="未知"
        />。版本记录只展示元数据，核验结果以读取报告后为准。
      </p>
      <div v-if="revisionTotal > 10" class="row">
        <el-button
          :disabled="indexBusy || revisionPage <= 1"
          @click="loadRevisions(revisionPage - 1)"
          >较新版本</el-button
        >
        <span>第 {{ revisionPage }} 页 / 共 {{ revisionTotal }} 个版本</span>
        <el-button
          :disabled="indexBusy || revisionPage * 10 >= revisionTotal"
          @click="loadRevisions(revisionPage + 1)"
          >较旧版本</el-button
        >
      </div>
    </div>
    <form class="row" @submit.prevent="load(false)">
      <label
        >冻结报告版本<input
          v-model.number="revision"
          aria-label="线上报告版本"
          type="number"
          min="1"
          max="2147483647" /></label
      ><el-button native-type="submit" :disabled="busy || !readable || !validRevision"
        >读取报告概要</el-button
      ><el-button :disabled="busy || !readable || !validRevision" @click="load(true)"
        >核验完整报告</el-button
      >
    </form>
    <p>概要读取核验报告正文；人工决定还需完整输入核验。新反馈不会改写已冻结的旧版本。</p>
    <p class="report-hint">
      首次打开时读取版本记录中的最新报告；链接已指定版本时保留该版本。当前运行状态与冻结报告的观察时间可能不同。
    </p>
    <p v-if="error" role="alert">{{ error }}</p>
    <p v-if="busy" role="status">正在处理报告……</p>
    <p v-if="report && !payload" role="alert">
      报告不可用：{{ report.state }}。协议或身份不兼容，正文和决定入口关闭。
    </p>
    <template v-if="payload && report">
      <p class="report-identity">
        冻结报告 v{{ report.revision }} ·
        {{ report.state === 'SUPPORTED' ? '完整核验通过' : '概要核验通过' }} · 分配截止序号
        {{ payload.assignmentCutoffSequence }} · 观察截止
        <BusinessTime :value="payload.observationCutoffAt" />
      </p>
      <p role="status">
        {{
          payload.decisionEligibility.comparisonAllowed
            ? '描述性比较门禁允许；仍由人工判断'
            : '证据不足或存在阻塞，不能作一般接受或拒绝结论'
        }}
      </p>
      <ul>
        <li v-for="code in payload.decisionEligibility.reasonCodes" :key="code">
          {{ reportIssue(code) }}
        </li>
      </ul>
      <section aria-label="冻结报告门槛对照">
        <h4>样本与覆盖门槛</h4>
        <p>
          当前值与门槛均来自此版冻结报告；未提供门槛时显示未知。达到样本门槛不改变工程验证模式。
        </p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>检查项</th>
                <th>基线</th>
                <th>候选</th>
                <th>冻结门槛</th>
                <th>报告说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="gate in coverageGates" :key="gate.key">
                <th>{{ gate.label }}</th>
                <td v-for="group in groups" :key="group">
                  {{ formatMetricNumber(payload.groups[group][gate.key], gate.unit) }}
                </td>
                <td>{{ gate.requirement }}</td>
                <td>{{ gate.reason }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <section class="report-summary" aria-label="质量成本与耗时概览">
        <h4>质量、成本与耗时</h4>
        <p>以每文档首个接受任务为单位，缺失不填零；延迟差不能直接归因为 Prompt。</p>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>指标</th>
                <th>基线</th>
                <th>候选</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <th>独立参与文档</th>
                <td v-for="group in groups" :key="group">
                  {{ formatMetricNumber(payload.groups[group].participatingDocumentCount) }}
                </td>
              </tr>
              <tr>
                <th>主规则通过 / 有效文档</th>
                <td
                  v-for="group in groups"
                  :key="group"
                  :title="onlineReportFact(payload.groups[group].primaryValidRatio)"
                >
                  {{ formatMetricNumber(payload.groups[group].primaryValidRatio, 'ratio') }}
                </td>
              </tr>
              <tr>
                <th>平均执行耗时</th>
                <td
                  v-for="group in groups"
                  :key="group"
                  :title="
                    onlineReportFact(
                      reportObject(payload.groups[group].firstAssignmentResource)
                        .executionLatencyMeanMillis,
                    )
                  "
                >
                  {{
                    formatMetricNumber(
                      reportObject(payload.groups[group].firstAssignmentResource)
                        .executionLatencyMeanMillis,
                      'millisecond',
                    )
                  }}
                  <small
                    >覆盖
                    {{
                      formatMetricNumber(
                        reportObject(payload.groups[group].firstAssignmentResource)
                          .executionLatencyCoverage,
                        'ratio',
                      )
                    }}</small
                  >
                </td>
              </tr>
              <tr>
                <th>平均费用（分币种）</th>
                <td v-for="group in groups" :key="group">
                  <span
                    v-for="(value, currency) in reportObject(
                      reportObject(payload.groups[group].firstAssignmentResource)
                        .meanCostByCurrency,
                    )"
                    :key="currency"
                    :title="onlineReportFact(value)"
                    >{{ formatMetricNumber(value, String(currency)) }}；</span
                  ><span
                    v-if="
                      !Object.keys(
                        reportObject(
                          reportObject(payload.groups[group].firstAssignmentResource)
                            .meanCostByCurrency,
                        ),
                      ).length
                    "
                    >未知</span
                  >
                  <small
                    >账本覆盖
                    {{
                      formatMetricNumber(
                        reportObject(payload.groups[group].firstAssignmentResource).ledgerCoverage,
                        'ratio',
                      )
                    }}</small
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </section>
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>文档级主分析 · 每文档首个接受任务</th>
              <th>基线</th>
              <th>候选</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in primaryRows" :key="row[0]">
              <th>{{ row[1] }}</th>
              <td
                v-for="group in groups"
                :key="group"
                :title="onlineReportFact(payload.groups[group][row[0]])"
              >
                {{ displayFact(payload.groups[group][row[0]], row[0]) }}
              </td>
            </tr>
            <tr>
              <th>有效比例区间 · 描述性不确定性</th>
              <td v-for="group in groups" :key="group">
                {{
                  formatMetricNumber(
                    reportObject(payload.groups[group].primaryConfidenceInterval).lower,
                    'ratio',
                  )
                }}
                ～
                {{
                  formatMetricNumber(
                    reportObject(payload.groups[group].primaryConfidenceInterval).upper,
                    'ratio',
                  )
                }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p>
        候选减基线有效比例：{{
          formatMetricNumber(
            payload.primaryDifference?.candidateMinusBaselineRatio,
            'percentage-point',
          )
        }}；预注册最小有价值差：{{
          formatMetricNumber(payload.primaryDifference?.mdeAbsoluteRatio, 'percentage-point')
        }}。{{ payload.analysisPlan?.mdeReason }}
      </p>
      <details class="report-details">
        <summary>原始人工质量与盲态覆盖</summary>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>原始人工质量 · 与审批分开</th>
                <th>基线</th>
                <th>候选</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in qualityRows" :key="row[0]">
                <th>{{ row[1] }}</th>
                <td
                  v-for="group in groups"
                  :key="group"
                  :title="onlineReportFact(payload.groups[group][row[0]])"
                >
                  {{ displayFact(payload.groups[group][row[0]], row[0]) }}
                </td>
              </tr>
              <tr v-for="stratum in blindnessGroups" :key="stratum">
                <th>{{ stratum }} · 平均等级 / 有效文档数</th>
                <td
                  v-for="group in groups"
                  :key="group"
                  :title="onlineReportFact(quality(group, stratum).meanRubricScore)"
                >
                  {{ formatMetricNumber(quality(group, stratum).meanRubricScore) }} /
                  {{ formatMetricNumber(quality(group, stratum).documentCount) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </details>
      <details class="report-details">
        <summary>审批过程与人工修改</summary>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>审批过程 · 最新已验证导入事实</th>
                <th>基线</th>
                <th>候选</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in approvalRows" :key="row[0]">
                <th>{{ row[1] }}</th>
                <td
                  v-for="group in groups"
                  :key="group"
                  :title="onlineReportFact(payload.approvalFeedback?.groups?.[group]?.[row[0]])"
                >
                  {{ displayFact(payload.approvalFeedback?.groups?.[group]?.[row[0]], row[0]) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>尚未导入可能表示待审批或没有适用提案；不等于拒绝，也不覆盖原始质量评分。</p>
      </details>
      <details class="report-details">
        <summary>全部任务与资源明细</summary>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>全部 Task 辅助分析 · 重复任务不增加独立样本</th>
                <th>基线</th>
                <th>候选</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in taskRows" :key="row[0]">
                <th>{{ row[1] }}</th>
                <td v-for="group in groups" :key="group">
                  {{ onlineReportFact(payload.groups[group][row[0]]) }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>首 Task 资源 · 缺失不填零</th>
                <th>基线</th>
                <th>候选</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in resourceRows" :key="row[0]">
                <th>{{ row[1] }}</th>
                <td v-for="group in groups" :key="group">
                  {{
                    displayFact(
                      reportObject(payload.groups[group].firstAssignmentResource)[row[0]],
                      row[0],
                    )
                  }}
                </td>
              </tr>
              <tr>
                <th>首 Task 平均费用 · 按币种分别展示</th>
                <td v-for="group in groups" :key="group">
                  <span
                    v-for="(value, currency) in reportObject(
                      reportObject(payload.groups[group].firstAssignmentResource)
                        .meanCostByCurrency,
                    )"
                    :key="currency"
                    :title="onlineReportFact(value)"
                    >{{ formatMetricNumber(value, String(currency)) }}；</span
                  ><span
                    v-if="
                      !Object.keys(
                        reportObject(
                          reportObject(payload.groups[group].firstAssignmentResource)
                            .meanCostByCurrency,
                        ),
                      ).length
                    "
                    >未知</span
                  >
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <p>
          全部 Task 费用：<span
            v-for="(value, currency) in payload.budgetByCurrency"
            :key="currency"
            :title="String(value)"
            >{{ formatMetricNumber(value, currency) }}；</span
          ><span v-if="!Object.keys(payload.budgetByCurrency || {}).length">未知</span
          >。不同币种不相加；延迟差不能归因为 Prompt。
        </p>
      </details>
      <p v-for="kind in ['range', 'runtime'] as const" :key="kind">
        {{ kind === 'range' ? '范围' : '参与文档' }} SRM：{{
          payload.srmDiagnostics?.[kind]?.status || '未知'
        }}；分配诊断 p={{
          onlineReportFact(payload.srmDiagnostics?.[kind]?.pValue)
        }}。小样本未启用不算通过，p 值不用于组间质量证明。
      </p>
      <details>
        <summary>实际窗口、规则分母与异常</summary>
        <p v-for="(value, key) in payload.actualWindows" :key="key">
          {{ key }}：{{ onlineReportFact(value) }}
        </p>
        <div
          v-for="[title, rules] in [
            ['文档规则', payload.ruleOutcomes],
            ['Task 辅助规则', payload.taskRuleOutcomes],
          ] as const"
          :key="title"
        >
          <h4>{{ title }}</h4>
          <p v-for="(outcomes, key) in rules" :key="key">
            {{ key }}：<span v-for="(value, outcome) in outcomes" :key="outcome"
              >{{ outcome }} {{ onlineReportFact(value) }}；</span
            >
          </p>
        </div>
        <p>
          反馈分层：<span v-for="(value, key) in payload.feedbackStrata" :key="key"
            >{{ key }} {{ value }}；</span
          >
        </p>
        <ul>
          <li
            v-for="code in [...(payload.exceptions || []), ...(payload.limitations || [])]"
            :key="code"
          >
            {{ onlineIssue(code) }}
          </li>
        </ul>
        <p>报告摘要 {{ report.reportHash }} / 输入摘要 {{ report.inputHash }}</p>
      </details>
      <form v-if="manageable && !experiment.decision" @submit.prevent="confirmDecision">
        <h4>人工结论</h4>
        <p>{{ ONLINE_DESCRIPTIVE_NOTICE }}</p>
        <p>
          只记录判断，不自动推广配置。普通结论需 STOPPED；安全拒绝仅能引用本报告已确认的安全反馈。
        </p>
        <label
          >结论<select v-model="decision" aria-label="线上人工结论">
            <option value="INSUFFICIENT_EVIDENCE">证据不足</option>
            <option value="ACCEPTED">接受候选</option>
            <option value="REJECTED">拒绝候选</option>
          </select></label
        >
        <label
          v-if="
            decision === 'REJECTED' && payload.decisionEligibility.confirmedSafetyFeedbackIds.length
          "
          >独立安全依据<select v-model="safetyId" aria-label="拒绝安全反馈">
            <option value="">普通拒绝</option>
            <option
              v-for="id in payload.decisionEligibility.confirmedSafetyFeedbackIds"
              :key="id"
              :value="id"
            >
              已确认安全反馈 #{{ id }}
            </option>
          </select></label
        >
        <label
          >判断理由<textarea
            v-model="reason"
            aria-label="线上结论理由"
            maxlength="2000"
            rows="3"
          /></label
        ><el-button native-type="submit" :disabled="busy || !decisionAllowed || !reason.trim()"
          >核对结论</el-button
        >
      </form>
    </template>
    <form
      v-if="canRecalculate && experiment.manifestSchemaVersion === 2"
      @submit.prevent="confirmRecalculate"
    >
      <h4>显式冻结报告</h4>
      <p>
        填写已接受序号及观察截止；不能用 Task 数替代序号。相同输入返回同一报告，新输入追加
        revision。
      </p>
      <el-button :disabled="busy" @click="useSuggestedRange">填入当前接受范围</el-button>
      <p v-if="suggestedZone">
        建议来自服务端时区 {{ suggestedZone }}，分配序号可能继续增长；填入表单不会自动生成报告。
      </p>
      <el-button v-if="payload" :disabled="busy" @click="useFrozenRange"
        >沿用当前冻结报告范围</el-button
      >
      <p v-if="payload">
        此操作仅复制当前报告的原始序号与截止时间，不代表当前最新范围；仍需核对后确认冻结。
      </p>
      <label
        >分配截止序号<input
          v-model="sequence"
          aria-label="报告分配截止序号"
          inputmode="numeric" /></label
      ><label
        >观察截止（部署服务本地时间，精确到毫秒）<input
          v-model="cutoff"
          aria-label="报告观察截止"
          placeholder="2026-10-12T16:00:00.000" /></label
      ><el-button native-type="submit" :disabled="busy">核对重算范围</el-button>
    </form>
    <el-dialog
      :model-value="!!confirmation"
      :title="confirmation === 'recalculate' ? '确认冻结报告' : '确认人工结论'"
      width="min(620px, 94vw)"
      @close="confirmation = null"
      ><p>{{ ONLINE_DESCRIPTIVE_NOTICE }}</p>
      <p v-if="confirmation === 'recalculate'">
        分配序号 ≤ {{ sequence }}，观察截止 {{ cutoff }}。保留全部失败、取消和未知项。
      </p>
      <p v-else>
        引用 revision {{ report?.revision }} / {{ report?.reportHash }}；{{ decision }}，理由：{{
          reason
        }}。{{ safetyId ? `独立安全依据 #${safetyId}` : '' }}
      </p>
      <template #footer
        ><el-button :disabled="busy" @click="confirmation = null">返回</el-button
        ><el-button type="primary" :disabled="busy || !confirmationCurrent" @click="submit"
          >确认提交</el-button
        ></template
      ></el-dialog
    >
  </section>
</template>
<script setup lang="ts">
import { formatBusinessTime } from '@/shared/utils/date-time'
import BusinessTime from '@/shared/components/BusinessTime.vue'
import { formatMetricNumber } from '../display'
import { evaluationStatus } from '../status'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElButton, ElDialog } from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import {
  decideOnlineExperiment,
  getOnlineReport,
  getOnlineReportRevisions,
  getOnlineReportRangeSuggestion,
  recalculateOnlineReport,
} from './api'
import {
  ONLINE_DESCRIPTIVE_NOTICE,
  onlineIdentity,
  onlineIssue,
  validOnlineId,
} from './presentation'
import { onlineReportFact, onlineReportPayload, reportObject } from './report'
import type {
  OnlineDecisionRequest,
  OnlineExperiment,
  OnlineRecalculateRequest,
  OnlineReport,
  OnlineReportRevision,
} from './types'
const props = defineProps<{
  experiment: OnlineExperiment
  readable: boolean
  manageable: boolean
  canRecalculate: boolean
  requestedRevision?: number
}>()
const emit = defineEmits<{ decided: []; loaded: [revision: number] }>()
const groups = ['BASELINE', 'CANDIDATE'] as const,
  blindnessGroups = ['BLINDED', 'NON_BLINDED', 'UNKNOWN']
function coverageRequirement(value: unknown) {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0 && value <= 10000
    ? `每组至少 ${formatMetricNumber(value / 10000, 'ratio')}`
    : '未知'
}
function reportIssue(code: string) {
  const reasons: Record<string, string> = {
    DOCUMENT_SAMPLE_INSUFFICIENT: '至少一组独立参与文档数未达到冻结门槛',
    PRIMARY_COVERAGE_INSUFFICIENT: '至少一组主规则有效覆盖未达到冻结门槛',
    BLINDED_QUALITY_COVERAGE_INSUFFICIENT: '至少一组有效盲态原始质量覆盖未达到冻结门槛',
  }
  return reasons[code] || onlineIssue(code)
}
const coverageGates = computed(() => {
  const plan = payload.value?.analysisPlan
  const reasons = payload.value?.decisionEligibility.reasonCodes || []
  const reason = (code: string) =>
    reasons.includes(code) ? reportIssue(code) : '本报告未列为此项阻塞；仍需核对整体结论。'
  return [
    {
      key: 'participatingDocumentCount',
      label: '独立参与文档',
      unit: 'count',
      requirement:
        Number.isSafeInteger(plan?.minDocumentsPerGroup) && plan!.minDocumentsPerGroup! > 0
          ? `每组至少 ${plan!.minDocumentsPerGroup} 个`
          : '未知',
      reason: reason('DOCUMENT_SAMPLE_INSUFFICIENT'),
    },
    {
      key: 'primaryValidCoverage',
      label: '主规则有效覆盖',
      unit: 'ratio',
      requirement: coverageRequirement(plan?.minCoverageBps),
      reason: reason('PRIMARY_COVERAGE_INSUFFICIENT'),
    },
    {
      key: 'blindedQualityCoverage',
      label: '有效盲态原始质量覆盖',
      unit: 'ratio',
      requirement:
        plan?.humanQualityRequired === false
          ? '本计划未要求'
          : coverageRequirement(plan?.minBlindedCoverageBps),
      reason: reason('BLINDED_QUALITY_COVERAGE_INSUFFICIENT'),
    },
  ]
})
const primaryRows = [
  ['rangeDocumentCount', '范围文档数'],
  ['participatingDocumentCount', '独立参与文档'],
  ['primaryPassDocumentCount', '已证实通过文档'],
  ['primaryFailDocumentCount', '失败文档'],
  ['primaryMissingDocumentCount', '缺失文档'],
  ['primaryValidRatio', '通过 / 有效文档'],
  ['primaryValidCoverage', '有效 / 全部参与文档'],
  ['provenCoverage', '通过 / 全部参与文档'],
] as const
const qualityRows = [
  ['originalEvidenceRecordedDocumentCount', '已记录原始证据文档'],
  ['originalEvidenceUnknownDocumentCount', '原始证据未知'],
  ['originalEvidenceUnratedDocumentCount', '已记录但未评价'],
  ['blindedDocumentCount', '有效盲态文档'],
  ['nonBlindedDocumentCount', '非盲态文档'],
  ['unknownBlindnessDocumentCount', '盲态未知'],
  ['unratedDocumentCount', '未评分文档'],
  ['blindedQualityCoverage', '盲态 / 全部首任务文档'],
  ['blindedQualityRecordedOriginalCoverage', '盲态 / 已记录原产物（解释性覆盖）'],
] as const
const approvalRows = [
  ['selectedRequestCount', '已选择最新导入提案数'],
  ['status_APPROVED', '已审批'],
  ['status_MERGED', '已合并'],
  ['status_REJECTED', '已拒绝'],
  ['status_RETURNED', '已退回'],
  ['comparableRequestCount', '可比较原始与审批内容'],
  ['notComparableRequestCount', '不可比较'],
  ['unknownRequestCount', '来源不可验证'],
  ['unrecordedAssignmentCount', '没有已验证导入事实的 Task'],
  ['meanHumanModificationRatio', '可比较提案的平均人工修改比例'],
] as const
const taskRows = [
  ['assignedTaskCount', '已接受 Task'],
  ['createdTaskCount', '已落库'],
  ['startedTaskCount', '已开始'],
  ['terminalTaskCount', '已终态'],
  ['completedTaskCount', '已完成'],
  ['failedTaskCount', '失败'],
  ['timedOutTaskCount', '超时'],
  ['userCanceledTaskCount', '取消'],
  ['pendingTaskCount', '在途'],
  ['unknownTaskCount', '未知'],
  ['ledgerUnknownCount', '账本未知'],
  ['estimatedTokenLedgerCount', '估算账本'],
  ['tokenEstimationUnknownCount', '估算状态未知'],
] as const
const resourceRows = [
  ['ledgerTokens', 'Token'],
  ['ledgerCoverage', '账本覆盖'],
  ['currencyCoverage', '币种覆盖'],
  ['queueLatencyMeanMillis', '平均排队耗时'],
  ['queueLatencyCoverage', '排队覆盖'],
  ['executionLatencyMeanMillis', '平均执行耗时'],
  ['executionLatencyCoverage', '执行覆盖'],
  ['endToEndLatencyMeanMillis', '平均端到端耗时'],
  ['endToEndLatencyCoverage', '端到端覆盖'],
] as const
const report = ref<OnlineReport | null>(null),
  revision = ref(props.requestedRevision ?? 1),
  busy = ref(false),
  error = ref(''),
  sequence = ref(''),
  cutoff = ref('')
const decision = ref<OnlineDecisionRequest['decision']>('INSUFFICIENT_EVIDENCE'),
  reason = ref(''),
  safetyId = ref('')
const confirmation = ref<'recalculate' | 'decision' | null>(null),
  captured = ref('')
const revisions = ref<OnlineReportRevision[]>([]),
  revisionTotal = ref(0),
  revisionPage = ref(1),
  indexBusy = ref(false),
  indexError = ref('')
const suggestedZone = ref('')
let indexPending: AbortController | undefined
let suggestionPending: AbortController | undefined
const selectedMetadata = computed(() =>
  revisions.value.find((item) => item.revision === revision.value),
)
let pending: AbortController | undefined, retry: { fingerprint: string; key: string } | undefined
const payload = computed(() => onlineReportPayload(report.value, props.experiment))
const validRevision = computed(
  () => Number.isInteger(revision.value) && revision.value > 0 && revision.value <= 2147483647,
)
const decisionAllowed = computed(
  () =>
    props.manageable &&
    !props.experiment.decision &&
    report.value?.state === 'SUPPORTED' &&
    !!payload.value &&
    (decision.value === 'REJECTED' && !!safetyId.value
      ? payload.value.decisionEligibility.safetyRejectedAllowed &&
        payload.value.decisionEligibility.confirmedSafetyFeedbackIds.includes(safetyId.value)
      : props.experiment.status === 'STOPPED' &&
        (decision.value === 'INSUFFICIENT_EVIDENCE' ||
          payload.value.decisionEligibility.comparisonAllowed)),
)
const identity = computed(() =>
  JSON.stringify([
    onlineIdentity(props.experiment),
    props.readable,
    props.manageable,
    props.canRecalculate,
    revision.value,
    report.value?.revision,
    report.value?.reportHash,
    report.value?.state,
    decision.value,
    reason.value,
    safetyId.value,
    sequence.value,
    cutoff.value,
  ]),
)
const confirmationCurrent = computed(
  () =>
    captured.value === identity.value &&
    (confirmation.value === 'recalculate' ? props.canRecalculate : decisionAllowed.value),
)
watch(identity, () => {
  confirmation.value = null
})
watch(revision, (value) => {
  if (report.value && report.value.revision !== value) report.value = null
})
watch(
  () => props.requestedRevision,
  (value) => {
    if (value !== undefined && value !== revision.value) {
      pending?.abort()
      busy.value = false
      revision.value = value
      report.value = null
      void load(false)
    }
  },
)
watch(decision, (value) => {
  if (value !== 'REJECTED') safetyId.value = ''
})
watch(
  [
    () => props.experiment.id,
    () => props.experiment.spaceId,
    () => props.experiment.manifestHash,
    () => props.readable,
  ],
  () => {
    pending?.abort()
    indexPending?.abort()
    revisions.value = []
    revisionTotal.value = 0
    indexBusy.value = false
    indexError.value = ''
    busy.value = false
    report.value = null
    error.value = ''
    retry = undefined
    reason.value = ''
    safetyId.value = ''
    sequence.value = ''
    cutoff.value = ''
    suggestedZone.value = ''
    confirmation.value = null
    revision.value = props.requestedRevision ?? 1
    if (props.readable) {
      if (props.requestedRevision !== undefined) void load(false)
      void loadRevisions(1, true)
    }
  },
  { immediate: true },
)
watch([() => props.manageable, () => props.canRecalculate], () => {
  pending?.abort()
  busy.value = false
  confirmation.value = null
  retry = undefined
})
watch(
  () => [props.experiment.id, props.experiment.spaceId, props.experiment.manifestSchemaVersion, props.readable, props.canRecalculate],
  async () => {
    suggestionPending?.abort()
    if (!props.readable || !props.canRecalculate || props.experiment.manifestSchemaVersion !== 2) return
    const controller = new AbortController()
    suggestionPending = controller
    const id = props.experiment.id
    try {
      const value = await getOnlineReportRangeSuggestion(id, controller.signal)
      if (controller.signal.aborted || sequence.value || cutoff.value || value.experimentId !== id ||
          !validOnlineId(value.assignmentCutoffSequence, true)) return
      sequence.value = value.assignmentCutoffSequence
      cutoff.value = value.observationCutoffAt
      suggestedZone.value = value.timeZone
    } catch {
      // 手动填入仍可重试，自动建议失败不妨碍查看报告。
    }
  },
  { immediate: true },
)
function quality(group: (typeof groups)[number], stratum: string) {
  return reportObject(reportObject(payload.value?.groups[group].qualityByBlindness)[stratum])
}
function displayFact(value: unknown, key: string) {
  const ratio = [
    'primaryValidRatio',
    'primaryValidCoverage',
    'provenCoverage',
    'blindedQualityCoverage',
    'blindedQualityRecordedOriginalCoverage',
    'meanHumanModificationRatio',
    'ledgerCoverage',
    'currencyCoverage',
    'queueLatencyCoverage',
    'executionLatencyCoverage',
    'endToEndLatencyCoverage',
  ].includes(key)
  return formatMetricNumber(
    value,
    ratio ? 'ratio' : key.endsWith('Millis') ? 'millisecond' : undefined,
  )
}
function store(value: OnlineReport, expectedRevision?: number) {
  if (
    value.experimentId !== props.experiment.id ||
    (expectedRevision !== undefined && value.revision !== expectedRevision)
  )
    throw new Error('报告身份或版本不一致')
  report.value = value
  revision.value = value.revision
  emit('loaded', value.revision)
}
function useFrozenRange() {
  if (!payload.value || busy.value || !props.canRecalculate) return
  sequence.value = String(payload.value.assignmentCutoffSequence)
  cutoff.value = payload.value.observationCutoffAt
  confirmation.value = null
  suggestedZone.value = ''
}
async function useSuggestedRange() {
  if (busy.value || !props.readable || !props.canRecalculate) return
  pending?.abort()
  const controller = new AbortController(),
    id = props.experiment.id,
    before = identity.value
  pending = controller
  busy.value = true
  error.value = ''
  try {
    const value = await getOnlineReportRangeSuggestion(id, controller.signal)
    if (controller.signal.aborted || !props.canRecalculate || before !== identity.value) return
    if (value.experimentId !== id || !validOnlineId(value.assignmentCutoffSequence, true))
      throw new Error('报告范围身份或序号无效')
    sequence.value = value.assignmentCutoffSequence
    cutoff.value = value.observationCutoffAt
    suggestedZone.value = value.timeZone
  } catch (e) {
    if (!controller.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
async function loadRevisions(page: number, chooseLatest = false) {
  if (!props.readable) return
  indexPending?.abort()
  const controller = new AbortController(),
    id = props.experiment.id,
    priorRevision = revision.value
  indexPending = controller
  indexBusy.value = true
  indexError.value = ''
  try {
    const value = await getOnlineReportRevisions(id, page, controller.signal)
    if (controller.signal.aborted || !props.readable) return
    if (value.records.some((item) => item.experimentId !== id))
      throw new Error('报告版本身份不一致')
    revisions.value = value.records
    revisionTotal.value = value.total
    revisionPage.value = value.pageNum
    const latest = value.records[0]
    if (
      chooseLatest &&
      props.requestedRevision === undefined &&
      latest &&
      revision.value === priorRevision &&
      !busy.value &&
      !report.value
    ) {
      revision.value = latest.revision
      await load(false)
    }
  } catch (e) {
    if (!controller.signal.aborted) indexError.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) indexBusy.value = false
  }
}
function readDecisionReport() {
  if (props.experiment.decisionReportRevision == null) return
  revision.value = props.experiment.decisionReportRevision
  void load(false)
}
function selectRevision(event: Event) {
  pending?.abort()
  busy.value = false
  revision.value = Number((event.target as HTMLSelectElement).value)
  void load(false)
}
async function load(full: boolean) {
  if (!props.readable || !validRevision.value || busy.value) return
  pending?.abort()
  const controller = new AbortController(),
    id = props.experiment.id,
    selectedRevision = revision.value
  pending = controller
  busy.value = true
  error.value = ''
  report.value = null
  try {
    const value = await getOnlineReport(id, selectedRevision, full, controller.signal)
    if (!controller.signal.aborted && props.readable && selectedRevision === revision.value)
      store(value, selectedRevision)
  } catch (e) {
    if (!controller.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
function confirmDecision() {
  if (!decisionAllowed.value || !reason.value.trim()) return
  captured.value = identity.value
  confirmation.value = 'decision'
}
function confirmRecalculate() {
  error.value = ''
  if (
    !props.canRecalculate ||
    !validOnlineId(sequence.value, true) ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{3})?$/.test(cutoff.value) ||
    !Number.isFinite(Date.parse(`${cutoff.value}Z`))
  ) {
    error.value = '请填写规范截止序号与服务部署时区的日期时间（秒或毫秒）'
    return
  }
  captured.value = identity.value
  confirmation.value = 'recalculate'
}
async function submit() {
  if (!confirmation.value || !confirmationCurrent.value || busy.value) return
  const action = confirmation.value,
    id = props.experiment.id,
    snapshot = identity.value
  const data =
    action === 'recalculate'
      ? { assignmentCutoffSequence: sequence.value, observationCutoffAt: cutoff.value }
      : {
          reportRevision: report.value!.revision,
          reportHash: report.value!.reportHash,
          decision: decision.value,
          reason: reason.value.trim(),
          ...(safetyId.value ? { safetyFeedbackId: safetyId.value } : {}),
        }
  const fingerprint = JSON.stringify([id, action, data])
  if (retry?.fingerprint !== fingerprint) retry = { fingerprint, key: window.crypto.randomUUID() }
  const controller = new AbortController()
  pending = controller
  busy.value = true
  error.value = ''
  try {
    if (action === 'recalculate') {
      const value = await recalculateOnlineReport(
        id,
        { ...data, clientRequestKey: retry.key } as OnlineRecalculateRequest,
        controller.signal,
      )
      if (controller.signal.aborted || !props.canRecalculate || snapshot !== identity.value) return
      store(value)
      void loadRevisions(1)
    } else {
      await decideOnlineExperiment(
        id,
        { ...data, clientRequestKey: retry.key } as OnlineDecisionRequest,
        controller.signal,
      )
      if (controller.signal.aborted || !decisionAllowed.value || snapshot !== identity.value) return
      emit('decided')
    }
    retry = undefined
    confirmation.value = null
  } catch (e) {
    if (!controller.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) busy.value = false
  }
}
onBeforeUnmount(() => {
  pending?.abort()
  indexPending?.abort()
  suggestionPending?.abort()
})
</script>
<style scoped>
.report-panel {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  min-width: 0;
}
.report-panel > * {
  min-width: 0;
}
.row {
  display: flex;
  align-items: end;
  gap: 12px;
  flex-wrap: wrap;
}
label {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 12px 0;
}
input,
select,
textarea {
  padding: 8px;
  font: inherit;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
}
.table-wrap {
  min-width: 0;
  max-width: 100%;
  overflow-x: auto;
}
table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  text-align: left;
}
th,
td {
  overflow-wrap: anywhere;
  padding: 10px;
  border-bottom: 1px solid var(--adw-border-color-light);
}
thead {
  background: var(--adw-surface-muted);
}
p,
li {
  overflow-wrap: anywhere;
  color: var(--adw-text-secondary);
  line-height: 1.6;
}
details {
  overflow-wrap: anywhere;
}
.report-summary,
.report-details,
.revision-index {
  min-width: 0;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  padding: var(--adw-space-3);
}
summary {
  cursor: pointer;
  font-weight: 600;
}
.report-summary h4 {
  margin: 0;
}
.report-summary small {
  display: block;
  margin-top: 4px;
  color: var(--adw-text-secondary);
}
.report-identity {
  padding: var(--adw-space-3);
  background: var(--adw-color-primary-soft);
}
.report-hint {
  font-size: var(--adw-font-size-caption);
}
input,
select,
textarea {
  min-width: 0;
  max-width: 100%;
  box-sizing: border-box;
}
[role='alert'] {
  color: var(--adw-color-danger);
}
</style>
