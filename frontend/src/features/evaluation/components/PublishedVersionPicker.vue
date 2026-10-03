<template>
  <el-dialog
    :model-value="open"
    :title="
      kind === 'evaluators'
        ? '选择已发布评估器版本'
        : kind === 'datasets'
          ? '选择已发布数据集版本'
          : '选择已发布测试用例版本'
    "
    width="760px"
    @close="$emit('close')"
  >
    <form class="picker-search" @submit.prevent="searchParents(1)">
      <el-input
        v-model="keyword"
        placeholder="搜索未归档资源"
        aria-label="搜索绑定资源"
        clearable
      />
      <el-button native-type="submit">查询</el-button>
    </form>
    <DataState :loading="loading" :error="error" @retry="searchParents(parentPage.pageNum)">
      <div class="picker-resources">
        <button
          v-for="parent in parentPage.records"
          :key="String(parent.id)"
          type="button"
          :aria-pressed="String(selectedParent) === String(parent.id)"
          @click="selectParent(parent.id)"
        >
          {{ parent.name }}
        </button>
      </div>
      <p v-if="!parentPage.records.length">没有可选资源，请先创建并发布版本。</p>
      <el-pagination
        v-if="parentPage.total > 10"
        small
        layout="prev, pager, next"
        :total="parentPage.total"
        :page-size="10"
        :current-page="parentPage.pageNum"
        @current-change="searchParents"
      />
      <DataState
        v-if="selectedParent"
        :loading="versionsLoading"
        :error="versionError"
        :empty="!versionsLoading && !versionPage.records.length"
        empty-text="此资源没有可绑定的已发布版本。"
        @retry="loadVersions(versionPage.pageNum)"
      >
        <div
          v-for="version in versionPage.records"
          :key="String(version.id)"
          class="picker-version"
        >
          <div>
            <strong>v{{ version.versionNo }}</strong
            ><span> #{{ version.id }}</span>
            <p>
              {{
                'evaluatorKey' in version
                  ? version.evaluatorKey
                  : 'sourceTaskId' in version
                    ? `来源 Task #${version.sourceTaskId}`
                    : `内容 hash：${version.contentHash}`
              }}
            </p>
          </div>
          <el-button
            :disabled="excludeIds.includes(String(version.id)) || !validVersion(version)"
            @click="choose(version)"
            >{{
              excludeIds.includes(String(version.id))
                ? '已绑定'
                : validVersion(version)
                  ? '选择'
                  : '契约不可用'
            }}</el-button
          >
        </div>
      </DataState>
      <el-pagination
        v-if="selectedParent && versionPage.total > 10"
        small
        layout="prev, pager, next"
        :total="versionPage.total"
        :page-size="10"
        :current-page="versionPage.pageNum"
        @current-change="loadVersions"
      />
    </DataState>
    <template #footer><el-button @click="$emit('close')">关闭</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { ElDialog, ElInput, ElButton, ElPagination } from 'element-plus'
import DataState from '@/shared/components/DataState.vue'
import { normalizeApiError } from '@/api/errors'
import {
  searchEvaluators,
  searchTestCases,
  searchEvaluatorVersions,
  searchTestCaseVersions,
  searchDatasets,
  searchDatasetVersions,
} from '../api/evaluation-api'
import { EVALUATOR_GUIDES } from '../catalog'
import type {
  Evaluator,
  EvaluationTestCase,
  EvaluatorVersion,
  TestCaseVersion,
  EvaluationPage,
  EvaluationDataset,
  DatasetVersion,
} from '../types'
import type { EntityId } from '@/features/workspace/types'
const props = defineProps<{
  open: boolean
  spaceId: string
  kind: 'evaluators' | 'test-cases' | 'datasets'
  excludeIds: string[]
}>()
const emit = defineEmits<{
  close: []
  select: [version: EvaluatorVersion | TestCaseVersion | DatasetVersion, name: string]
}>()
const keyword = ref(''),
  selectedParent = ref<EntityId>(),
  loading = ref(false),
  versionsLoading = ref(false),
  error = ref(''),
  versionError = ref('')
const parentPage = ref<EvaluationPage<Evaluator | EvaluationTestCase | EvaluationDataset>>({
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
})
const versionPage = ref<EvaluationPage<EvaluatorVersion | TestCaseVersion | DatasetVersion>>({
  records: [],
  total: 0,
  pageNum: 1,
  pageSize: 10,
})
let parentRequest: AbortController | undefined, versionRequest: AbortController | undefined
function validVersion(value: EvaluatorVersion | TestCaseVersion | DatasetVersion) {
  if (String(value.spaceId) !== props.spaceId || value.status !== 'PUBLISHED' || !value.contentHash)
    return false
  if ('evaluatorKey' in value) {
    if (
      !EVALUATOR_GUIDES.some((item) => item.key === value.evaluatorKey) ||
      value.configSchemaVersion !== 1 ||
      value.resultSchemaVersion !== 1
    )
      return false
    try {
      JSON.parse(value.configJson || '')
    } catch {
      return false
    }
  }
  return true
}
async function searchParents(pageNum: number) {
  parentRequest?.abort()
  versionRequest?.abort()
  selectedParent.value = undefined
  versionPage.value.records = []
  const request = new AbortController()
  parentRequest = request
  loading.value = true
  error.value = ''
  parentPage.value.records = []
  try {
    const query = {
      spaceId: props.spaceId,
      keyword: keyword.value.trim() || undefined,
      archived: false,
      pageNum,
      pageSize: 10,
    }
    const result =
      props.kind === 'evaluators'
        ? await searchEvaluators(query, request.signal)
        : props.kind === 'datasets'
          ? await searchDatasets(query, request.signal)
          : await searchTestCases(query, request.signal)
    if (request.signal.aborted) return
    if (result.records.some((item) => String(item.spaceId) !== props.spaceId || item.archived))
      throw new Error('绑定资源归属或状态不一致')
    parentPage.value = result
  } catch (e) {
    if (!request.signal.aborted) error.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) loading.value = false
  }
}
function selectParent(id: EntityId) {
  selectedParent.value = id
  void loadVersions(1)
}
async function loadVersions(pageNum: number) {
  versionRequest?.abort()
  const request = new AbortController()
  versionRequest = request
  versionsLoading.value = true
  versionError.value = ''
  versionPage.value.records = []
  try {
    const query = {
      spaceId: props.spaceId,
      parentId: selectedParent.value,
      status: 'PUBLISHED' as const,
      pageNum,
      pageSize: 10,
    }
    const result =
      props.kind === 'evaluators'
        ? await searchEvaluatorVersions(query, request.signal)
        : props.kind === 'datasets'
          ? await searchDatasetVersions(query, request.signal)
          : await searchTestCaseVersions(query, request.signal)
    if (request.signal.aborted) return
    if (
      result.records.some(
        (item) =>
          String(item.spaceId) !== props.spaceId ||
          String(
            'evaluatorId' in item
              ? item.evaluatorId
              : 'datasetId' in item
                ? item.datasetId
                : item.testCaseId,
          ) !== String(selectedParent.value),
      )
    )
      throw new Error('绑定版本归属不一致')
    versionPage.value = result
  } catch (e) {
    if (!request.signal.aborted) versionError.value = normalizeApiError(e).message
  } finally {
    if (!request.signal.aborted) versionsLoading.value = false
  }
}
function choose(version: EvaluatorVersion | TestCaseVersion | DatasetVersion) {
  if (!validVersion(version) || props.excludeIds.includes(String(version.id))) return
  const name =
    parentPage.value.records.find((item) => String(item.id) === String(selectedParent.value))
      ?.name || ''
  emit('select', version, name)
  emit('close')
}
watch(
  () => [props.open, props.spaceId, props.kind],
  () => {
    parentRequest?.abort()
    versionRequest?.abort()
    if (props.open) {
      keyword.value = ''
      void searchParents(1)
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => {
  parentRequest?.abort()
  versionRequest?.abort()
})
</script>
<style scoped>
.picker-search {
  display: flex;
  gap: var(--adw-space-3);
  margin-bottom: var(--adw-space-4);
}
.picker-resources {
  display: flex;
  flex-wrap: wrap;
  gap: var(--adw-space-2);
  margin-bottom: var(--adw-space-3);
}
.picker-resources button {
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-sm);
  padding: 8px 12px;
  color: var(--adw-text-primary);
  background: transparent;
  cursor: pointer;
}
.picker-resources button[aria-pressed='true'] {
  background: var(--adw-color-primary-soft);
  color: var(--adw-color-primary);
}
.picker-version {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-3);
  border-bottom: 1px solid var(--adw-border-color);
  padding: var(--adw-space-3) 0;
  overflow-wrap: anywhere;
}
.picker-version p {
  color: var(--adw-text-secondary);
  margin: 4px 0;
}
</style>
