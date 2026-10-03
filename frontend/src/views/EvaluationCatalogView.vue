<template>
  <section class="catalog-page" :aria-label="label">
    <div class="catalog-page__heading">
      <div>
        <h2>{{ resource?.name || label }}{{ resourceId ? ' · 版本管理' : '' }}</h2>
        <p>
          {{
            resourceId
              ? resource?.description || `资源 #${resourceId}`
              : '管理可复现的测试资产，发布后版本与绑定冻结。'
          }}
        </p>
      </div>
      <div class="catalog-page__actions">
        <el-button v-if="resourceId" @click="backToList">返回列表</el-button>
        <el-button v-if="canManage && !resourceId" type="primary" @click="openResourceDialog"
          >新建{{ label }}</el-button
        >
        <el-button
          v-if="canManage && resource && !resource.archived"
          type="primary"
          @click="openVersionDialog"
          >新建草稿版本</el-button
        >
        <el-button
          v-if="canManage && resource && !resource.archived"
          :disabled="busy"
          type="danger"
          plain
          @click="archiveResource"
          >归档资源</el-button
        >
      </div>
    </div>
    <p v-if="sourceTaskId" class="catalog-page__source">
      已选择来源任务 #{{ sourceTaskId }}。创建测试用例前需再次核验回放资格。<RouterLink
        v-if="canReadTask"
        :to="`/spaces/${spaceId}/tasks/${sourceTaskId}?tab=evidence`"
        >返回执行证据</RouterLink
      >
    </p>
    <el-alert
      v-if="resource?.archived"
      :title="`主资源已归档：${archiveNotice}`"
      type="info"
      :closable="false"
    />
    <form v-if="!resourceId" class="catalog-page__filters" @submit.prevent="loadList(1)">
      <el-input
        v-model="keyword"
        :aria-label="`搜索${label}`"
        :placeholder="`搜索${label}名称或说明`"
        clearable
      />
      <el-select v-model="archiveFilter" aria-label="资源归档状态"
        ><el-option label="未归档" value="active" /><el-option
          label="已归档"
          value="archived" /><el-option label="全部资源" value="all"
      /></el-select>
      <el-button native-type="submit" type="primary">查询</el-button
      ><el-button @click="resetFilters">重置</el-button>
    </form>
    <DataState :loading="loading" :error="error" @retry="reload">
      <template v-if="!resourceId">
        <el-table :data="page.records" class="catalog-page__table">
          <el-table-column :label="label" min-width="230"
            ><template #default="{ row }"
              ><div class="catalog-page__name">
                <span class="catalog-page__initial">{{ row.name.slice(0, 1) }}</span>
                <div>
                  <RouterLink :to="resourcePath(row.id)">{{ row.name }}</RouterLink>
                  <p>{{ row.description || '暂无说明' }}</p>
                </div>
              </div></template
            ></el-table-column
          >
          <el-table-column label="资源状态" width="110"
            ><template #default="{ row }"
              ><el-tag :type="row.archived ? 'info' : 'success'">{{
                row.archived ? '已归档' : '未归档'
              }}</el-tag></template
            ></el-table-column
          >
          <el-table-column
            v-if="section === 'evaluators'"
            prop="evaluatorKey"
            label="Evaluator key"
            min-width="180"
          />
          <el-table-column label="资源 ID" min-width="185"
            ><template #default="{ row }">{{ row.id }}</template></el-table-column
          >
          <el-table-column label="版本与绑定" width="120"
            ><template #default="{ row }"
              ><RouterLink :to="resourcePath(row.id)">查看版本</RouterLink></template
            ></el-table-column
          >
        </el-table>
        <p v-if="!page.records.length" class="catalog-page__empty">
          没有匹配的{{ label }}。{{
            canManage
              ? `可以新建${label}，再创建草稿版本。`
              : '请调整查询条件，或联系空间管理者添加资源。'
          }}
        </p>
        <div class="catalog-page__pagination">
          <span>共 {{ page.total }} 条</span
          ><el-pagination
            layout="prev, pager, next"
            :total="page.total"
            :page-size="20"
            :current-page="page.pageNum"
            @current-change="loadList"
          />
        </div>
      </template>
      <div v-else-if="resource" class="catalog-page__detail">
        <aside class="catalog-page__timeline" aria-label="版本时间线">
          <h3>版本时间线</h3>
          <button
            v-for="version in versions.records"
            :key="String(version.id)"
            type="button"
            :aria-pressed="String(selectedVersion?.id) === String(version.id)"
            @click="selectVersion(String(version.id))"
          >
            <div>
              <strong>v{{ version.versionNo }}</strong
              ><EvaluationStatusTag domain="version" :status="version.status" />
            </div>
            <span>{{ version.publishedAt ? `发布于 ${version.publishedAt}` : '尚未发布' }}</span
            ><span>#{{ version.id }}</span>
          </button>
          <p v-if="!versions.records.length">尚未创建版本。</p>
          <el-pagination
            v-if="versions.total > 10"
            small
            layout="prev, next"
            :total="versions.total"
            :page-size="10"
            :current-page="versions.pageNum"
            @current-change="loadVersions"
          />
        </aside>
        <div class="catalog-page__version">
          <DataState :loading="versionLoading" :error="versionError" @retry="reloadSelectedVersion">
            <template v-if="selectedVersion">
              <div class="catalog-page__heading">
                <h3>
                  v{{ selectedVersion.versionNo }}
                  <EvaluationStatusTag domain="version" :status="selectedVersion.status" />
                </h3>
                <div class="catalog-page__actions">
                  <el-button
                    v-if="editable"
                    :loading="busy"
                    :disabled="bindingDirty"
                    @click="publishVersion"
                    >核对并发布</el-button
                  ><el-button
                    v-if="canManage && selectedVersion.status === 'PUBLISHED'"
                    :disabled="busy"
                    type="danger"
                    plain
                    @click="archiveVersion"
                    >归档版本</el-button
                  >
                </div>
              </div>
              <p class="catalog-page__rule">
                {{
                  editable
                    ? '草稿可配置。先保存配置和绑定，再核对冻结信息发布。'
                    : '此版本只读，配置与绑定保持历史取值。'
                }}
              </p>
              <dl class="catalog-page__facts">
                <dt>内容 hash</dt>
                <dd>{{ selectedVersion.contentHash || '发布时生成' }}</dd>
                <dt>发布时间</dt>
                <dd>{{ selectedVersion.publishedAt || '尚未发布' }}</dd>
              </dl>
              <template v-if="evaluatorVersion">
                <dl class="catalog-page__facts">
                  <dt>Evaluator key</dt>
                  <dd>{{ evaluatorVersion.evaluatorKey }}</dd>
                  <dt>实现版本</dt>
                  <dd>{{ evaluatorVersion.implementationVersion || '未提供' }}</dd>
                  <dt>config / result schema</dt>
                  <dd>
                    {{ evaluatorVersion.configSchemaVersion }} /
                    {{ evaluatorVersion.resultSchemaVersion }}
                  </dd>
                </dl>
                <JsonConfigEditor
                  v-model="configForm.configJson"
                  label="configJson"
                  :readonly="!editable"
                />
                <p class="catalog-page__hint">
                  首版 config/result schema 为 1。{{
                    currentGuide?.label || '请核对受支持的评估器契约'
                  }}
                </p>
                <details v-if="currentGuide">
                  <summary>查看最小配置示例</summary>
                  <pre>{{ formatJson(currentGuide.example) }}</pre>
                </details>
                <el-button v-if="editable" :loading="busy" type="primary" @click="saveConfig"
                  >保存草稿配置</el-button
                >
              </template>
              <template v-if="testCaseVersion">
                <dl class="catalog-page__facts">
                  <dt>来源 Task / Execution</dt>
                  <dd>
                    <RouterLink
                      v-if="canReadTask && testCaseVersion.sourceTaskId"
                      :to="`/spaces/${spaceId}/tasks/${testCaseVersion.sourceTaskId}?tab=evidence`"
                      >#{{ testCaseVersion.sourceTaskId }}</RouterLink
                    ><span v-else>{{ testCaseVersion.sourceTaskId || '未提供' }}</span> /
                    {{ testCaseVersion.sourceExecutionId || '未提供' }}
                  </dd>
                  <dt>冻结文档版本</dt>
                  <dd>{{ testCaseVersion.documentVersionSnapshot ?? '未提供' }}</dd>
                  <dt>文档内容 hash</dt>
                  <dd>{{ testCaseVersion.documentContentSha256 || '未提供' }}</dd>
                  <dt>输入 snapshot schema / hash</dt>
                  <dd>
                    {{ testCaseVersion.sourceInputSchemaVersion }} /
                    {{ testCaseVersion.sourceInputHash }}
                  </dd>
                  <dt>执行 snapshot schema / hash</dt>
                  <dd>
                    {{ testCaseVersion.sourceExecutionSchemaVersion }} /
                    {{ testCaseVersion.sourceExecutionHash }}
                  </dd>
                  <dt>expected schema</dt>
                  <dd>{{ testCaseVersion.expectedSchemaVersion }}</dd>
                </dl>
                <JsonConfigEditor
                  v-model="configForm.expectedJson"
                  label="expectedJson"
                  :readonly="!editable"
                />
                <el-form label-position="top"
                  ><el-form-item label="来源类型"
                    ><el-select
                      v-model="configForm.sourceType"
                      :disabled="!editable"
                      aria-label="来源类型"
                      ><el-option value="LIVE" label="LIVE" /></el-select></el-form-item
                  ><el-form-item label="脱敏与裁剪说明"
                    ><el-input
                      v-model="configForm.sanitizationNote"
                      type="textarea"
                      :readonly="!editable"
                      maxlength="1000"
                      show-word-limit
                      aria-label="脱敏与裁剪说明" /></el-form-item
                ></el-form>
                <el-button v-if="editable" :loading="busy" type="primary" @click="saveConfig"
                  >保存草稿配置</el-button
                >
              </template>
              <CatalogBindings
                v-if="section !== 'evaluators'"
                :key="String(selectedVersion.id)"
                :kind="section"
                :version-id="String(selectedVersion.id)"
                :space-id="spaceId"
                :editable="editable"
                @dirty="bindingDirty = $event"
              />
            </template>
            <p v-else>选择版本查看配置与冻结依据。</p>
          </DataState>
        </div>
      </div>
    </DataState>
    <p v-if="actionError" role="alert">{{ actionError }}</p>

    <el-dialog v-model="resourceDialog" :title="`新建${label}`" width="560px">
      <el-form label-position="top"
        ><el-form-item label="名称"
          ><el-input
            v-model="resourceForm.name"
            maxlength="200"
            aria-label="资源名称" /></el-form-item
        ><el-form-item label="说明"
          ><el-input
            v-model="resourceForm.description"
            type="textarea"
            maxlength="1000"
            aria-label="资源说明" /></el-form-item
        ><el-form-item v-if="section === 'evaluators'" label="Evaluator key"
          ><el-select v-model="resourceForm.evaluatorKey" aria-label="Evaluator key"
            ><el-option
              v-for="guide in EVALUATOR_GUIDES"
              :key="guide.key"
              :value="guide.key"
              :label="`${guide.label} · ${guide.key}`" /></el-select></el-form-item
      ></el-form>
      <p v-if="dialogError" role="alert">{{ dialogError }}</p>
      <template #footer
        ><el-button @click="resourceDialog = false">取消</el-button
        ><el-button type="primary" :loading="busy" @click="createResource"
          >创建资源</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="versionDialog"
      title="新建草稿版本"
      width="800px"
      :close-on-click-modal="false"
    >
      <template v-if="section === 'test-cases'"
        ><TaskSourcePicker
          v-if="versionDialog"
          :space-id="spaceId"
          :initial-task-id="sourceTaskId || undefined"
          @select="selectedSourceTask = $event" /><JsonConfigEditor
          v-model="newVersionForm.expectedJson"
          label="expectedJson" /><el-form label-position="top"
          ><el-form-item label="来源类型"
            ><el-select v-model="newVersionForm.sourceType" aria-label="新版本来源类型"
              ><el-option value="LIVE" label="LIVE" /></el-select></el-form-item
          ><el-form-item label="脱敏与裁剪说明"
            ><el-input
              v-model="newVersionForm.sanitizationNote"
              type="textarea"
              maxlength="1000"
              aria-label="新版本脱敏说明" /></el-form-item></el-form
      ></template>
      <template v-else-if="section === 'evaluators'"
        ><p>
          config/result schema：1。Evaluator key：{{
            'evaluatorKey' in (resource || {}) ? (resource as Evaluator).evaluatorKey : ''
          }}
        </p>
        <JsonConfigEditor v-model="newVersionForm.configJson" label="configJson" />
        <details v-if="currentGuide">
          <summary>查看最小配置示例</summary>
          <pre>{{ formatJson(currentGuide.example) }}</pre>
          <el-button @click="newVersionForm.configJson = formatJson(currentGuide.example)"
            >使用示例</el-button
          >
        </details></template
      >
      <p v-else>创建空草稿版本后，从已发布测试用例版本中添加绑定并排序。</p>
      <p v-if="dialogError" role="alert">{{ dialogError }}</p>
      <template #footer
        ><el-button @click="versionDialog = false">取消</el-button
        ><el-button
          type="primary"
          :loading="busy"
          :disabled="section === 'test-cases' && !selectedSourceTask"
          @click="createVersion"
          >创建草稿版本</el-button
        ></template
      >
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElMessageBox,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import DataState from '@/shared/components/DataState.vue'
import { normalizeApiError } from '@/api/errors'
import { useWorkspaceStore } from '@/stores/workspace'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import * as api from '@/features/evaluation/api/evaluation-api'
import { getTask, getReplayEligibility } from '@/features/task/api/task-api'
import type {
  EvaluationDataset,
  EvaluationTestCase,
  Evaluator,
  DatasetVersion,
  TestCaseVersion,
  EvaluatorVersion,
  EvaluationPage,
} from '@/features/evaluation/types'
import {
  EVALUATOR_GUIDES,
  formatJson,
  replayReason,
  type CatalogSection,
} from '@/features/evaluation/catalog'
import CatalogBindings from '@/features/evaluation/components/CatalogBindings.vue'
import EvaluationStatusTag from '@/features/evaluation/components/EvaluationStatusTag.vue'
import JsonConfigEditor from '@/features/evaluation/components/JsonConfigEditor.vue'
import TaskSourcePicker from '@/features/evaluation/components/TaskSourcePicker.vue'
type CatalogResource = EvaluationDataset | EvaluationTestCase | Evaluator
type CatalogVersion = DatasetVersion | TestCaseVersion | EvaluatorVersion
const props = defineProps<{ section: CatalogSection; resourceId?: string }>()
const route = useRoute(),
  router = useRouter(),
  workspace = useWorkspaceStore()
const spaceId = computed(() => String(route.params.spaceId)),
  label = computed(
    () => ({ 'test-cases': '测试用例', datasets: '数据集', evaluators: '评估器' })[props.section],
  )
const inSpace = computed(() => String(workspace.currentSpaceId) === spaceId.value)
const canRead = computed(
  () => inSpace.value && workspace.hasPermission(SPACE_PERMISSIONS.EVALUATION_READ),
)
const canManage = computed(
  () => canRead.value && workspace.hasPermission(SPACE_PERMISSIONS.EVALUATION_MANAGE),
)
const canReadTask = computed(
  () => inSpace.value && workspace.hasPermission(SPACE_PERMISSIONS.TASK_READ),
)
const sourceTaskId = computed(() =>
  props.section === 'test-cases' &&
  typeof route.query.sourceTaskId === 'string' &&
  /^[1-9]\d{0,18}$/.test(route.query.sourceTaskId)
    ? route.query.sourceTaskId
    : null,
)
const keyword = ref(''),
  archiveFilter = ref('active'),
  loading = ref(false),
  error = ref(''),
  actionError = ref(''),
  busy = ref(false)
const page = ref<EvaluationPage<CatalogResource>>({
    records: [],
    total: 0,
    pageNum: 1,
    pageSize: 20,
  }),
  resource = ref<CatalogResource | null>(null)
const versions = ref<EvaluationPage<CatalogVersion>>({
    records: [],
    total: 0,
    pageNum: 1,
    pageSize: 10,
  }),
  selectedVersion = ref<CatalogVersion | null>(null)
const versionLoading = ref(false),
  versionError = ref(''),
  bindingDirty = ref(false)
const editable = computed(
  () => canManage.value && !resource.value?.archived && selectedVersion.value?.status === 'DRAFT',
)
const archiveNotice = computed(() =>
  props.section === 'evaluators'
    ? '不能新建版本或新建绑定；已发布用例的既有 PUBLISHED 评估器绑定仍可使用，历史数据可查看。'
    : '不能新建版本或用于新运行，历史数据仍可查看。',
)
const evaluatorVersion = computed(() =>
  props.section === 'evaluators' ? (selectedVersion.value as EvaluatorVersion | null) : null,
)
const testCaseVersion = computed(() =>
  props.section === 'test-cases' ? (selectedVersion.value as TestCaseVersion | null) : null,
)
const currentGuide = computed(() =>
  EVALUATOR_GUIDES.find(
    (item) =>
      item.key ===
      (resource.value && 'evaluatorKey' in resource.value ? resource.value.evaluatorKey : null),
  ),
)
const configForm = reactive({
  configJson: '{}',
  expectedJson: '{}',
  sourceType: 'LIVE',
  sanitizationNote: '',
})
const newVersionForm = reactive({ ...configForm })
const resourceForm = reactive({ name: '', description: '', evaluatorKey: 'task-terminal-status' })
const resourceDialog = ref(false),
  versionDialog = ref(false),
  dialogError = ref(''),
  selectedSourceTask = ref<string | null>(null)
let mainRequest: AbortController | undefined,
  versionRequest: AbortController | undefined,
  versionsRequest: AbortController | undefined,
  mutationRequest: AbortController | undefined
function startMutation() {
  mutationRequest?.abort()
  const request = new AbortController()
  mutationRequest = request
  busy.value = true
  return request
}
function resourcePath(id: string | number) {
  return `/spaces/${spaceId.value}/evaluation/${props.section}/${id}`
}
function backToList() {
  void router.push(`/spaces/${spaceId.value}/evaluation/${props.section}`)
}
function resetFilters() {
  keyword.value = ''
  archiveFilter.value = 'active'
  void loadList(1)
}
function ensureSpace(value: { spaceId: string | number }) {
  if (String(value.spaceId) !== spaceId.value) throw new Error('资源空间归属不一致')
}
async function loadList(pageNum: number) {
  mainRequest?.abort()
  const request = new AbortController()
  mainRequest = request
  loading.value = true
  error.value = ''
  page.value.records = []
  if (!canRead.value) {
    loading.value = false
    error.value = '缺少当前空间评估读取权限'
    return
  }
  try {
    const query = {
      spaceId: spaceId.value,
      keyword: keyword.value.trim() || undefined,
      archived: archiveFilter.value === 'all' ? undefined : archiveFilter.value === 'archived',
      pageNum,
      pageSize: 20,
    }
    const result =
      props.section === 'test-cases'
        ? await api.searchTestCases(query, request.signal)
        : props.section === 'datasets'
          ? await api.searchDatasets(query, request.signal)
          : await api.searchEvaluators(query, request.signal)
    if (request.signal.aborted) return
    result.records.forEach(ensureSpace)
    page.value = result
  } catch (e) {
    if (!request.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}
async function loadDetail() {
  mainRequest?.abort()
  const request = new AbortController()
  mainRequest = request
  loading.value = true
  error.value = ''
  resource.value = null
  selectedVersion.value = null
  if (!canRead.value || !props.resourceId) {
    loading.value = false
    return
  }
  const id = props.resourceId
  try {
    const value =
      props.section === 'test-cases'
        ? await api.getTestCase(id, request.signal)
        : props.section === 'datasets'
          ? await api.getDataset(id, request.signal)
          : await api.getEvaluator(id, request.signal)
    if (request.signal.aborted) return
    ensureSpace(value)
    if (String(value.id) !== id) throw new Error('资源身份不一致')
    resource.value = value
    await loadVersions(1)
    if (!request.signal.aborted && sourceTaskId.value && canManage.value && !value.archived)
      openVersionDialog()
  } catch (e) {
    if (!request.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}
async function loadVersions(pageNum: number) {
  versionsRequest?.abort()
  const request = new AbortController()
  versionsRequest = request
  if (!props.resourceId || !canRead.value) return
  try {
    const query = { spaceId: spaceId.value, parentId: props.resourceId, pageNum, pageSize: 10 }
    const result =
      props.section === 'test-cases'
        ? await api.searchTestCaseVersions(query, request.signal)
        : props.section === 'datasets'
          ? await api.searchDatasetVersions(query, request.signal)
          : await api.searchEvaluatorVersions(query, request.signal)
    if (request.signal.aborted) return
    result.records.forEach(validateVersionIdentity)
    versions.value = result
    const queryId =
      typeof route.query.versionId === 'string' && /^[1-9]\d{0,18}$/.test(route.query.versionId)
        ? route.query.versionId
        : null
    await loadVersion(queryId || String(result.records[0]?.id || ''))
  } catch (e) {
    if (!request.signal.aborted) versionError.value = normalizeApiError(e).message
  }
}
function validateVersionIdentity(value: CatalogVersion) {
  ensureSpace(value)
  const parentId =
    'datasetId' in value
      ? value.datasetId
      : 'testCaseId' in value
        ? value.testCaseId
        : value.evaluatorId
  if (String(parentId) !== props.resourceId) throw new Error('版本不属于当前资源')
}
async function selectVersion(id: string) {
  if (bindingDirty.value || configDirty()) {
    try {
      await ElMessageBox.confirm('切换版本会丢弃未保存的配置或绑定，继续？', '切换版本')
    } catch {
      return
    }
  }
  await router.replace({ query: { ...route.query, versionId: id } })
}
async function loadVersion(id: string) {
  mutationRequest?.abort()
  busy.value = false
  versionRequest?.abort()
  const request = new AbortController()
  versionRequest = request
  selectedVersion.value = null
  bindingDirty.value = false
  versionError.value = ''
  versionLoading.value = true
  try {
    if (!id || !canRead.value) return
    const value =
      props.section === 'test-cases'
        ? await api.getTestCaseVersion(id, request.signal)
        : props.section === 'datasets'
          ? await api.getDatasetVersion(id, request.signal)
          : await api.getEvaluatorVersion(id, request.signal)
    if (request.signal.aborted) return
    validateVersionIdentity(value)
    if (String(value.id) !== id) throw new Error('版本身份不一致')
    selectedVersion.value = value
    configForm.configJson = 'configJson' in value ? value.configJson || '{}' : '{}'
    configForm.expectedJson = 'expectedJson' in value ? value.expectedJson || '{}' : '{}'
    configForm.sourceType = 'sourceType' in value ? value.sourceType || 'LIVE' : 'LIVE'
    configForm.sanitizationNote = 'sanitizationNote' in value ? value.sanitizationNote || '' : ''
  } catch (e) {
    if (!request.signal.aborted) versionError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) versionLoading.value = false
  }
}
function reloadSelectedVersion() {
  if (selectedVersion.value) void loadVersion(String(selectedVersion.value.id))
  else void loadVersions(versions.value.pageNum)
}
function reload() {
  return props.resourceId ? loadDetail() : loadList(page.value.pageNum)
}
function configDirty() {
  const value = selectedVersion.value
  return (
    value &&
    ('configJson' in value
      ? configForm.configJson !== (value.configJson || '{}')
      : 'expectedJson' in value
        ? configForm.expectedJson !== (value.expectedJson || '{}') ||
          configForm.sourceType !== value.sourceType ||
          configForm.sanitizationNote !== (value.sanitizationNote || '')
        : false)
  )
}
function openResourceDialog() {
  Object.assign(resourceForm, { name: '', description: '', evaluatorKey: 'task-terminal-status' })
  dialogError.value = ''
  resourceDialog.value = true
}
function openVersionDialog() {
  Object.assign(newVersionForm, {
    configJson: '{}',
    expectedJson: '{}',
    sourceType: 'LIVE',
    sanitizationNote: '',
  })
  selectedSourceTask.value = null
  dialogError.value = ''
  versionDialog.value = true
}
async function createResource() {
  if (!canManage.value || busy.value) return
  if (!resourceForm.name.trim()) {
    dialogError.value = '请输入资源名称'
    return
  }
  const request = startMutation()
  dialogError.value = ''
  try {
    const payload = {
      spaceId: spaceId.value,
      name: resourceForm.name.trim(),
      description: resourceForm.description.trim() || null,
    }
    const created =
      props.section === 'test-cases'
        ? await api.createTestCase(payload, request.signal)
        : props.section === 'datasets'
          ? await api.createDataset(payload, request.signal)
          : await api.createEvaluator(
              { ...payload, evaluatorKey: resourceForm.evaluatorKey },
              request.signal,
            )
    if (request.signal.aborted) return
    ensureSpace(created)
    resourceDialog.value = false
    ElMessage.success(`${label.value}已创建`)
    await router.push({
      path: resourcePath(created.id),
      query: sourceTaskId.value ? { sourceTaskId: sourceTaskId.value } : {},
    })
  } catch (e) {
    if (!request.signal.aborted) dialogError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) busy.value = false
  }
}
async function createVersion() {
  if (!canManage.value || !resource.value || resource.value.archived || busy.value) return
  const request = startMutation()
  const parentId = resource.value.id
  const sourceId = selectedSourceTask.value
  const form = { ...newVersionForm }
  dialogError.value = ''
  try {
    let value: CatalogVersion
    if (props.section === 'test-cases') {
      if (
        !sourceId ||
        !canReadTask.value ||
        !workspace.hasPermission(SPACE_PERMISSIONS.DOCUMENT_READ)
      )
        throw new Error('请选择并核验来源任务')
      const task = await getTask(sourceId, request.signal)
      if (request.signal.aborted) return
      ensureSpace(task)
      if (
        String(task.id) !== sourceId ||
        task.status !== 'COMPLETED' ||
        task.executionMode !== 'LIVE' ||
        !['ORIGINAL', 'RERUN', 'REVIEW_REWORK'].includes(task.lineageType)
      )
        throw new Error('来源必须是已完成的 LIVE 任务')
      const eligibility = await getReplayEligibility(sourceId, request.signal)
      if (request.signal.aborted) return
      if (String(eligibility.sourceTaskId) !== sourceId || !eligibility.replayable)
        throw new Error(replayReason(eligibility.reasonCode))
      value = await api.createTestCaseVersion(
        {
          testCaseId: parentId,
          sourceTaskId: sourceId,
          expectedSchemaVersion: 1,
          expectedJson: formatJson(form.expectedJson),
          sourceType: form.sourceType,
          sanitizationNote: form.sanitizationNote || null,
        },
        request.signal,
      )
    } else if (props.section === 'datasets')
      value = await api.createDatasetVersion({ datasetId: parentId }, request.signal)
    else
      value = await api.createEvaluatorVersion(
        {
          evaluatorId: parentId,
          configSchemaVersion: 1,
          configJson: formatJson(form.configJson),
          resultSchemaVersion: 1,
        },
        request.signal,
      )
    if (request.signal.aborted) return
    validateVersionIdentity(value)
    versionDialog.value = false
    ElMessage.success('草稿版本已创建')
    await router.replace({ query: { versionId: String(value.id) } })
    await loadVersions(1)
  } catch (e) {
    if (!request.signal.aborted) dialogError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) busy.value = false
  }
}
async function saveConfig() {
  if (!editable.value || !selectedVersion.value || busy.value) return
  const request = startMutation()
  actionError.value = ''
  try {
    const id = selectedVersion.value.id
    const updated = evaluatorVersion.value
      ? await api.updateEvaluatorVersion(
          id,
          {
            configSchemaVersion: evaluatorVersion.value.configSchemaVersion || 1,
            configJson: formatJson(configForm.configJson),
            resultSchemaVersion: evaluatorVersion.value.resultSchemaVersion || 1,
          },
          request.signal,
        )
      : await api.updateTestCaseVersion(
          id,
          {
            expectedSchemaVersion: testCaseVersion.value?.expectedSchemaVersion || 1,
            expectedJson: formatJson(configForm.expectedJson),
            sourceType: configForm.sourceType,
            sanitizationNote: configForm.sanitizationNote || null,
          },
          request.signal,
        )
    if (request.signal.aborted) return
    validateVersionIdentity(updated)
    selectedVersion.value = updated
    ElMessage.success('草稿配置已保存')
    if (evaluatorVersion.value) configForm.configJson = evaluatorVersion.value.configJson || '{}'
    if (testCaseVersion.value) {
      configForm.expectedJson = testCaseVersion.value.expectedJson || '{}'
      configForm.sanitizationNote = testCaseVersion.value.sanitizationNote || ''
    }
  } catch (e) {
    if (!request.signal.aborted) actionError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) busy.value = false
  }
}
async function publishVersion() {
  if (!editable.value || !selectedVersion.value || busy.value) return
  if (configDirty() || bindingDirty.value) {
    actionError.value = '请先保存草稿配置和全部绑定，再发布。'
    return
  }
  const id = selectedVersion.value.id
  try {
    await ElMessageBox.confirm(
      '发布将冻结当前配置、来源快照和已保存绑定，之后不能修改。请核对页面中的冻结依据。',
      `发布 v${selectedVersion.value.versionNo}`,
      { confirmButtonText: '确认发布', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  if (!editable.value || String(selectedVersion.value?.id) !== String(id)) return
  const request = startMutation()
  actionError.value = ''
  try {
    const value =
      props.section === 'datasets'
        ? await api.publishDatasetVersion(id, request.signal)
        : props.section === 'test-cases'
          ? await api.publishTestCaseVersion(id, request.signal)
          : await api.publishEvaluatorVersion(id, request.signal)
    if (request.signal.aborted) return
    validateVersionIdentity(value)
    selectedVersion.value = value
    ElMessage.success('版本已发布并冻结')
    await loadVersions(versions.value.pageNum)
  } catch (e) {
    if (!request.signal.aborted) actionError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) busy.value = false
  }
}
async function archiveResource() {
  if (!canManage.value || !resource.value || resource.value.archived || busy.value) return
  const id = resource.value.id
  try {
    await ElMessageBox.confirm(archiveNotice.value, `归档${label.value}`, {
      type: 'warning',
      confirmButtonText: '确认归档',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  if (!canManage.value || String(resource.value?.id) !== String(id)) return
  const request = startMutation()
  actionError.value = ''
  try {
    const value =
      props.section === 'datasets'
        ? await api.archiveDataset(id, request.signal)
        : props.section === 'test-cases'
          ? await api.archiveTestCase(id, request.signal)
          : await api.archiveEvaluator(id, request.signal)
    if (request.signal.aborted) return
    ensureSpace(value)
    resource.value = value
    ElMessage.success('资源已归档')
  } catch (e) {
    if (!request.signal.aborted) actionError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) busy.value = false
  }
}
async function archiveVersion() {
  if (
    !canManage.value ||
    !selectedVersion.value ||
    selectedVersion.value.status !== 'PUBLISHED' ||
    busy.value
  )
    return
  const id = selectedVersion.value.id
  try {
    await ElMessageBox.confirm(
      '归档后此版本不能用于新绑定或新运行，历史记录仍可查看。',
      '归档已发布版本',
      { type: 'warning', confirmButtonText: '确认归档', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  if (!canManage.value || String(selectedVersion.value?.id) !== String(id)) return
  const request = startMutation()
  actionError.value = ''
  try {
    const value =
      props.section === 'datasets'
        ? await api.archiveDatasetVersion(id, request.signal)
        : props.section === 'test-cases'
          ? await api.archiveTestCaseVersion(id, request.signal)
          : await api.archiveEvaluatorVersion(id, request.signal)
    if (request.signal.aborted) return
    validateVersionIdentity(value)
    selectedVersion.value = value
    ElMessage.success('版本已归档')
    await loadVersions(versions.value.pageNum)
  } catch (e) {
    if (!request.signal.aborted) actionError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) busy.value = false
  }
}
watch(
  () => [props.section, props.resourceId, spaceId.value, canRead.value],
  () => {
    mainRequest?.abort()
    versionRequest?.abort()
    versionsRequest?.abort()
    mutationRequest?.abort()
    busy.value = false
    resourceDialog.value = false
    versionDialog.value = false
    resource.value = null
    selectedVersion.value = null
    versions.value.records = []
    actionError.value = ''
    bindingDirty.value = false
    keyword.value = ''
    archiveFilter.value = 'active'
    void (props.resourceId ? loadDetail() : loadList(1))
  },
  { immediate: true },
)
watch(
  () => route.query.versionId,
  () => {
    if (resource.value)
      void loadVersion(
        typeof route.query.versionId === 'string'
          ? route.query.versionId
          : String(versions.value.records[0]?.id || ''),
      )
  },
)
onBeforeUnmount(() => {
  mainRequest?.abort()
  versionRequest?.abort()
  versionsRequest?.abort()
  mutationRequest?.abort()
})
</script>
<style scoped>
.catalog-page {
  display: grid;
  gap: var(--adw-space-5);
}
.catalog-page__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--adw-space-4);
}
h2,
h3 {
  margin: 0;
}
h2 {
  font-size: var(--adw-font-size-subtitle);
}
p {
  margin: var(--adw-space-2) 0 0;
  color: var(--adw-text-secondary);
  line-height: 1.6;
}
.catalog-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--adw-space-2);
}
.catalog-page__actions .el-button {
  margin-left: 0;
}
.catalog-page__filters {
  display: flex;
  gap: var(--adw-space-3);
}
.catalog-page__filters .el-input {
  max-width: 330px;
}
.catalog-page__filters .el-select {
  width: 150px;
}
.catalog-page__name {
  display: flex;
  align-items: center;
  gap: var(--adw-space-3);
}
.catalog-page__name p {
  font-size: var(--adw-font-size-caption);
}
.catalog-page__initial {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  flex-shrink: 0;
  border-radius: var(--adw-radius-sm);
  background: var(--adw-color-primary-soft);
  color: var(--adw-color-primary);
  font-weight: 600;
}
a {
  color: var(--adw-color-primary);
  text-decoration: none;
}
a:focus-visible,
button:focus-visible {
  outline: 2px solid var(--adw-color-primary);
}
.catalog-page__pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--adw-space-4);
  color: var(--adw-text-secondary);
}
.catalog-page__detail {
  display: grid;
  grid-template-columns: 210px minmax(0, 1fr);
  gap: var(--adw-space-5);
}
.catalog-page__timeline {
  border-right: 1px solid var(--adw-border-color);
  padding-right: var(--adw-space-4);
}
.catalog-page__timeline button {
  display: grid;
  gap: 6px;
  width: 100%;
  margin-top: var(--adw-space-3);
  padding: var(--adw-space-3);
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
  text-align: left;
  color: var(--adw-text-primary);
  background: transparent;
  cursor: pointer;
}
.catalog-page__timeline button[aria-pressed='true'] {
  background: var(--adw-color-primary-soft);
  border-color: var(--adw-color-primary);
}
.catalog-page__timeline button div {
  display: flex;
  justify-content: space-between;
  gap: var(--adw-space-2);
}
.catalog-page__timeline span {
  color: var(--adw-text-secondary);
  font-size: var(--adw-font-size-caption);
  overflow-wrap: anywhere;
}
.catalog-page__version {
  min-width: 0;
}
.catalog-page__facts {
  display: grid;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: var(--adw-space-3);
  padding: var(--adw-space-4);
  background: var(--adw-surface-muted);
  border-radius: var(--adw-radius-sm);
  font-size: var(--adw-font-size-caption);
}
dt {
  color: var(--adw-text-secondary);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
.catalog-page__rule {
  margin-bottom: var(--adw-space-4);
}
.catalog-page__hint,
.catalog-page__source {
  font-size: var(--adw-font-size-caption);
}
.catalog-page__empty {
  padding: var(--adw-space-6) 0;
  text-align: center;
}
details {
  margin: var(--adw-space-3) 0;
}
summary {
  cursor: pointer;
  color: var(--adw-color-primary);
}
pre {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  background: var(--adw-surface-muted);
  padding: var(--adw-space-3);
}
p[role='alert'] {
  color: var(--adw-color-danger);
}
@media (max-width: 1100px) {
  .catalog-page__detail {
    grid-template-columns: minmax(0, 1fr);
  }
  .catalog-page__timeline {
    border-right: 0;
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: var(--adw-space-2);
  }
  .catalog-page__timeline button {
    width: auto;
  }
}
@media (max-width: 600px) {
  .catalog-page__heading,
  .catalog-page__filters {
    flex-direction: column;
  }
  .catalog-page__filters .el-input,
  .catalog-page__filters .el-select {
    max-width: none;
    width: 100%;
  }
  .catalog-page__facts {
    grid-template-columns: minmax(0, 1fr);
  }
  .catalog-page__pagination {
    flex-wrap: wrap;
  }
}
</style>
