<template>
  <el-dialog
    v-model="visible"
    class="global-search-dialog"
    width="min(720px, calc(100vw - 32px))"
    :show-close="false"
    :close-on-click-modal="true"
    @opened="focusInput"
    @closed="resetSearch"
  >
    <template #header>
      <div class="global-search__input-wrap">
        <el-icon :size="20"><Search /></el-icon>
        <input
          ref="inputRef"
          v-model="keyword"
          type="search"
          maxlength="100"
          autocomplete="off"
          placeholder="搜索文档、任务或 Agent"
          aria-label="全局搜索"
          @keydown="handleInputKeydown"
        />
        <kbd>ESC</kbd>
      </div>
    </template>

    <div v-if="!spaceId" class="global-search__state">
      <p v-if="workspaceStore.spaces.length">请先进入一个空间，再搜索其中的内容。</p>
      <template v-else>
        <p>当前账号还没有空间，创建空间后即可使用搜索。</p>
        <el-button type="primary" @click="requestSpaceCreate">创建空间</el-button>
      </template>
    </div>
    <div v-else-if="keyword.trim().length < 2" class="global-search__state">
      输入至少 2 个字符开始搜索
    </div>
    <div v-else-if="loading" class="global-search__state">正在搜索…</div>
    <div v-else-if="errorMessage" class="global-search__state global-search__state--error">
      {{ errorMessage }}
    </div>
    <template v-else>
      <nav class="global-search__tabs" aria-label="搜索结果类型">
        <button
          v-for="tab in tabs"
          :key="tab.value"
          type="button"
          :class="{ active: activeType === tab.value }"
          @click="activeType = tab.value"
        >
          {{ tab.label }} <span>{{ tab.total }}</span>
        </button>
      </nav>

      <div v-if="visibleItems.length" ref="resultsRef" class="global-search__results">
        <section v-for="section in visibleSections" :key="section.type">
          <header>
            <span>{{ section.label }}</span>
            <small>共 {{ section.group.total }} 条</small>
          </header>
          <button
            v-for="item in section.group.records"
            :key="`${item.type}-${item.id}`"
            type="button"
            class="global-search__result"
            :class="{ active: selectedItem === item }"
            @mouseenter="selectedIndex = visibleItems.indexOf(item)"
            @click="openResult(item)"
          >
            <span class="global-search__icon" :class="`is-${item.type.toLowerCase()}`">
              <el-icon
                ><Document v-if="item.type === 'DOCUMENT'" /><Tickets
                  v-else-if="item.type === 'TASK'" /><Cpu v-else
              /></el-icon>
            </span>
            <span class="global-search__content">
              <strong>
                <span
                  v-for="(part, index) in highlightParts(item.title)"
                  :key="`title-${index}`"
                  :class="{ 'global-search__match': part.matched }"
                  >{{ part.text }}</span
                >
              </strong>
              <small>
                <span
                  v-for="(part, index) in highlightParts(item.subtitle || typeLabel(item.type))"
                  :key="`subtitle-${index}`"
                  :class="{ 'global-search__match': part.matched }"
                  >{{ part.text }}</span
                >
                · {{ formatTime(item.updatedAt) }}
              </small>
            </span>
            <span v-if="statusLabel(item)" class="global-search__status">{{
              statusLabel(item)
            }}</span>
            <span class="global-search__enter">↵</span>
          </button>
        </section>
      </div>
      <div v-else class="global-search__state">没有找到匹配结果</div>
    </template>

    <footer class="global-search__footer">
      <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
      <span><kbd>↵</kbd> 打开</span>
      <span>仅搜索当前空间</span>
    </footer>
  </el-dialog>
</template>

<script setup lang="ts">
import { Cpu, Document, Search, Tickets } from '@element-plus/icons-vue'
import { ElButton, ElDialog, ElIcon } from 'element-plus'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import { searchWorkbench } from '@/features/search/api/search-api'
import type {
  WorkbenchSearchGroup,
  WorkbenchSearchItem,
  WorkbenchSearchResult,
  WorkbenchSearchType,
} from '@/features/search/types'
import { useWorkspaceStore } from '@/stores/workspace'

type SearchFilter = 'ALL' | WorkbenchSearchType

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; 'create-space': [] }>()
const router = useRouter()
const workspaceStore = useWorkspaceStore()
const inputRef = ref<HTMLInputElement>()
const resultsRef = ref<globalThis.HTMLElement>()
const keyword = ref('')
const activeType = ref<SearchFilter>('ALL')
const loading = ref(false)
const errorMessage = ref('')
const selectedIndex = ref(0)
const result = ref<WorkbenchSearchResult | null>(null)
let debounceTimer: ReturnType<typeof setTimeout> | null = null
let requestController: AbortController | null = null

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const spaceId = computed(() => workspaceStore.currentSpaceId)
const emptyGroup = (): WorkbenchSearchGroup => ({ records: [], total: 0 })
const sections = computed(() => [
  { type: 'DOCUMENT' as const, label: '文档', group: result.value?.documents ?? emptyGroup() },
  { type: 'TASK' as const, label: '任务', group: result.value?.tasks ?? emptyGroup() },
  { type: 'AGENT' as const, label: 'Agent', group: result.value?.agents ?? emptyGroup() },
])
const visibleSections = computed(() =>
  sections.value.filter(
    (section) =>
      section.group.records.length > 0 &&
      (activeType.value === 'ALL' || activeType.value === section.type),
  ),
)
const visibleItems = computed(() =>
  visibleSections.value.flatMap((section) => section.group.records),
)
const selectedItem = computed(() => visibleItems.value[selectedIndex.value] ?? null)
const total = computed(() => sections.value.reduce((sum, section) => sum + section.group.total, 0))
const tabs = computed(() => [
  { value: 'ALL' as const, label: '全部', total: total.value },
  ...sections.value.map((section) => ({
    value: section.type as SearchFilter,
    label: section.label,
    total: section.group.total,
  })),
])

watch(keyword, () => scheduleSearch())
watch(activeType, () => (selectedIndex.value = 0))
watch(selectedIndex, () => {
  void nextTick(() =>
    resultsRef.value
      ?.querySelector('.global-search__result.active')
      ?.scrollIntoView({ block: 'nearest' }),
  )
})
watch(spaceId, () => {
  result.value = null
  if (visible.value) scheduleSearch()
})

onMounted(() => window.addEventListener('keydown', handleGlobalKeydown))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  clearPendingSearch()
})

function handleGlobalKeydown(event: globalThis.KeyboardEvent): void {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    visible.value = true
  }
}

function handleInputKeydown(event: globalThis.KeyboardEvent): void {
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (visibleItems.value.length)
      selectedIndex.value = (selectedIndex.value + 1) % visibleItems.value.length
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (visibleItems.value.length) {
      selectedIndex.value =
        (selectedIndex.value - 1 + visibleItems.value.length) % visibleItems.value.length
    }
  } else if (event.key === 'Enter' && selectedItem.value) {
    event.preventDefault()
    void openResult(selectedItem.value)
  }
}

function scheduleSearch(): void {
  clearPendingSearch()
  result.value = null
  errorMessage.value = ''
  if (!spaceId.value || keyword.value.trim().length < 2) {
    loading.value = false
    return
  }
  loading.value = true
  debounceTimer = setTimeout(() => void executeSearch(), 350)
}

async function executeSearch(): Promise<void> {
  if (!spaceId.value) return
  const controller = new AbortController()
  requestController = controller
  try {
    result.value = await searchWorkbench(spaceId.value, keyword.value.trim(), controller.signal)
    selectedIndex.value = 0
  } catch (error) {
    if (!controller.signal.aborted) errorMessage.value = normalizeApiError(error).message
  } finally {
    if (!controller.signal.aborted) loading.value = false
  }
}

function clearPendingSearch(): void {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = null
  requestController?.abort()
  requestController = null
}

function focusInput(): void {
  void nextTick(() => inputRef.value?.focus())
}

function resetSearch(): void {
  clearPendingSearch()
  keyword.value = ''
  activeType.value = 'ALL'
  result.value = null
  errorMessage.value = ''
  loading.value = false
  selectedIndex.value = 0
}

function requestSpaceCreate(): void {
  visible.value = false
  emit('create-space')
}

async function openResult(item: WorkbenchSearchItem): Promise<void> {
  const currentSpaceId = spaceId.value
  if (!currentSpaceId) return
  visible.value = false
  if (item.type === 'DOCUMENT') {
    await router.push({
      name: 'space-documents',
      params: { spaceId: currentSpaceId, documentId: item.id },
    })
  } else if (item.type === 'TASK') {
    await router.push({
      name: 'space-task-detail',
      params: { spaceId: currentSpaceId, taskId: item.id },
    })
  } else {
    await router.push({
      name: 'space-agents',
      params: { spaceId: currentSpaceId },
      query: { agentId: String(item.id) },
    })
  }
}

function typeLabel(type: WorkbenchSearchType): string {
  return { DOCUMENT: '文档', TASK: '任务', AGENT: 'Agent' }[type]
}

function highlightParts(value: string): Array<{ text: string; matched: boolean }> {
  const needle = keyword.value.trim()
  if (!needle) return [{ text: value, matched: false }]
  const source = value.toLocaleLowerCase()
  const target = needle.toLocaleLowerCase()
  const parts: Array<{ text: string; matched: boolean }> = []
  let offset = 0
  let matchIndex = source.indexOf(target)
  while (matchIndex >= 0) {
    if (matchIndex > offset) parts.push({ text: value.slice(offset, matchIndex), matched: false })
    const end = matchIndex + needle.length
    parts.push({ text: value.slice(matchIndex, end), matched: true })
    offset = end
    matchIndex = source.indexOf(target, offset)
  }
  if (offset < value.length) parts.push({ text: value.slice(offset), matched: false })
  return parts.length ? parts : [{ text: value, matched: false }]
}

function statusLabel(item: WorkbenchSearchItem): string {
  const labels: Record<string, string> = {
    FORMAL: '正式',
    DRAFT: '草稿',
    ENABLED: '已启用',
    DISABLED: '已停用',
    PENDING: '待运行',
    DISPATCHED: '已分发',
    RUNNING: '运行中',
    WAITING_INPUT: '等待输入',
    WAITING_AUTH: '等待授权',
    CANCELING: '取消中',
    COMPLETED: '已完成',
    TERMINATED: '已终止',
    FAILED: '异常失败',
  }
  return item.status ? (labels[item.status] ?? item.status) : ''
}

function formatTime(value: string | null): string {
  if (!value) return '暂无更新时间'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? value
    : new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit' }).format(date)
}
</script>

<style scoped>
:global(.global-search-dialog) {
  margin-top: 10vh;
  padding: 0;
  overflow: hidden;
  border-radius: 14px;
}
:global(.global-search-dialog .el-dialog__header) {
  margin: 0;
  padding: 0;
}
:global(.global-search-dialog .el-dialog__body) {
  padding: 0;
}
.global-search__input-wrap {
  display: flex;
  height: 64px;
  align-items: center;
  gap: 12px;
  padding: 0 20px;
  border-bottom: 1px solid var(--adw-border-color);
}
.global-search__input-wrap input {
  min-width: 0;
  flex: 1;
  border: 0;
  outline: 0;
  color: var(--adw-text-primary);
  background: transparent;
  font: inherit;
  font-size: 17px;
}
kbd {
  padding: 2px 6px;
  border: 1px solid var(--adw-border-color);
  border-radius: 5px;
  color: var(--adw-text-tertiary);
  background: var(--adw-surface-muted);
  font-size: 11px;
}
.global-search__tabs {
  display: flex;
  gap: 4px;
  padding: 12px 16px 8px;
  border-bottom: 1px solid var(--adw-border-color-light);
}
.global-search__tabs button {
  padding: 7px 11px;
  border: 0;
  border-radius: 7px;
  color: var(--adw-text-secondary);
  background: transparent;
  cursor: pointer;
}
.global-search__tabs button.active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.global-search__tabs span {
  margin-left: 4px;
  font-size: 11px;
}
.global-search__results {
  max-height: min(56vh, 520px);
  overflow-y: auto;
  padding: 8px;
}
.global-search__results section header {
  display: flex;
  justify-content: space-between;
  padding: 9px 10px 5px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.global-search__result {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 12px;
  padding: 10px;
  border: 0;
  border-radius: 9px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}
.global-search__result.active {
  background: var(--adw-color-primary-soft);
}
.global-search__icon {
  display: inline-flex;
  width: 36px;
  height: 36px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.global-search__icon.is-task {
  color: var(--adw-color-success);
  background: var(--adw-color-success-soft);
}
.global-search__icon.is-agent {
  color: var(--adw-color-accent);
  background: #f0edff;
}
.global-search__content {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}
.global-search__content strong,
.global-search__content small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.global-search__content strong {
  color: var(--adw-text-primary);
  font-size: 14px;
}
.global-search__match {
  padding: 0 1px;
  border-radius: 3px;
  color: #7a4b00;
  background: #fff0a6;
  font-weight: 700;
}
.global-search__content small {
  color: var(--adw-text-tertiary);
}
.global-search__status {
  padding: 3px 7px;
  border-radius: 10px;
  color: var(--adw-text-secondary);
  background: var(--adw-surface-muted);
  font-size: 11px;
}
.global-search__enter {
  visibility: hidden;
  color: var(--adw-text-tertiary);
}
.global-search__result.active .global-search__enter {
  visibility: visible;
}
.global-search__state {
  display: grid;
  min-height: 220px;
  place-content: center;
  justify-items: center;
  gap: 14px;
  color: var(--adw-text-tertiary);
  text-align: center;
}
.global-search__state--error {
  color: var(--adw-color-danger);
}
.global-search__footer {
  display: flex;
  gap: 18px;
  padding: 10px 18px;
  border-top: 1px solid var(--adw-border-color-light);
  color: var(--adw-text-tertiary);
  font-size: 11px;
}
.global-search__footer span:last-child {
  margin-left: auto;
}
.global-search__footer kbd {
  margin-right: 3px;
}
@media (max-width: 600px) {
  .global-search__status,
  .global-search__footer {
    display: none;
  }
}
</style>
