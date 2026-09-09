<template>
  <section class="create-page">
    <PageHeader title="新建任务" description="选择一个 Agent，围绕文档提交明确目标"
      ><template #breadcrumb><span>任务 / 新建任务</span></template
      ><template #actions
        ><el-button :loading="saving" @click="saveDraft">保存草稿</el-button></template
      ></PageHeader
    >
    <div class="create-layout">
      <el-form class="create-form" label-position="top">
        <article class="form-section surface-card">
          <h2><b>1</b>任务目标</h2>
          <el-form-item label="任务名称" required
            ><el-input v-model="form.name" maxlength="100" show-word-limit /></el-form-item
          ><el-form-item label="目标描述" required
            ><el-input
              v-model="form.instruction"
              type="textarea"
              :rows="4"
              maxlength="4000"
              show-word-limit
          /></el-form-item>
        </article>
        <article class="form-section surface-card">
          <h2><b>2</b>关联文档</h2>
          <div class="document-fields">
            <el-form-item label="当前空间"
              ><el-input
                :model-value="workspace.currentSpace?.name || '当前空间'"
                disabled /></el-form-item
            ><el-form-item class="document-picker" label="文档"
              ><el-select
                v-model="form.documentId"
                filterable
                placeholder="选择文档"
                @change="loadOptions"
                ><el-option
                  v-for="doc in documents"
                  :key="String(doc.id)"
                  :label="`${doc.docType === 'FORMAL' ? '正式文档' : '草稿'} / ${doc.title}`"
                  :value="doc.id" /></el-select></el-form-item
            ><el-form-item label="读取访问"
              ><el-select v-model="form.readScope"
                ><el-option label="全文" value="FULL" /><el-option
                  label="仅选中区域"
                  value="RANGES" /></el-select
            ></el-form-item>
          </div>
          <div class="range-summary">
            <div>
              <strong>{{
                form.focusRegions.length
                  ? `已添加 ${form.focusRegions.length} 个关注区域`
                  : '尚未添加关注区域'
              }}</strong>
              <p>
                {{
                  form.readScope === 'FULL'
                    ? 'Agent 可阅读全文，关注区域用于说明重点。'
                    : 'Agent 只能读取下方选中的区域。'
                }}
              </p>
            </div>
            <el-button type="primary" plain @click="openRangeDialog()">{{
              form.focusRegions.length ? '管理关注区域' : '设置关注区域'
            }}</el-button>
          </div>
          <div v-if="form.focusRegions.length" class="focus-list">
            <article
              v-for="region in visibleFocusRegions"
              :key="`${region.start}-${region.originalIndex}`"
            >
              <div>
                <strong>区域 {{ region.originalIndex + 1 }} · {{ region.length }} 字符</strong>
                <p>{{ region.textPreview }}</p>
                <small>{{ region.instruction }}</small>
              </div>
              <div class="focus-actions">
                <el-button text type="primary" @click="openRangeDialog(region.originalIndex)"
                  >修改</el-button
                ><el-button text type="danger" @click="removeRegion(region.originalIndex)"
                  >删除</el-button
                >
              </div>
            </article>
            <el-button
              v-if="form.focusRegions.length > 2"
              text
              type="primary"
              class="focus-toggle"
              @click="showAllFocusRegions = !showAllFocusRegions"
              >{{
                showAllFocusRegions ? '收起' : `展开其余 ${form.focusRegions.length - 2} 个区域`
              }}</el-button
            >
          </div>
          <el-alert
            v-if="options"
            :type="options.documentType === 'FORMAL' ? 'warning' : 'success'"
            :closable="false"
            show-icon
            :title="
              options.documentType === 'FORMAL'
                ? '正式文档：结果将生成变更请求，不能直接修改'
                : '草稿文档：Agent 可直接应用结构化变更'
            "
          />
        </article>
        <article class="form-section surface-card">
          <h2><b>3</b>选择 Agent</h2>
          <DataState
            :loading="optionsLoading"
            :error="optionsError"
            :empty="!!options && !options.agents.length"
            empty-text="没有可用于该文档的 Agent"
            @retry="loadOptions"
            ><div class="agent-grid">
              <button
                v-for="agent in options?.agents || []"
                :key="String(agent.id)"
                type="button"
                class="agent-option"
                :class="{ 'agent-option--active': String(form.agentId) === String(agent.id) }"
                @click="form.agentId = agent.id"
              >
                <strong>{{ agent.name }}</strong
                ><span
                  >{{ agent.modelDisplayName || '未知模型' }} · {{ agent.skillSelectionMode }}</span
                >
                <dl>
                  <div>
                    <dt>Skill</dt>
                    <dd>{{ agent.skillCount }}</dd>
                  </div>
                  <div>
                    <dt>外部 MCP</dt>
                    <dd>{{ agent.mcpCount }}</dd>
                  </div>
                  <div>
                    <dt>Token 上限</dt>
                    <dd>{{ formatTokens(agent.tokenBudget) }}</dd>
                  </div>
                </dl>
              </button>
            </div></DataState
          >
        </article>
      </el-form>
      <aside class="summary surface-card">
        <h2>任务摘要</h2>
        <dl>
          <div>
            <dt>文档</dt>
            <dd>{{ selectedDocument?.title || '未选择' }}</dd>
          </div>
          <div>
            <dt>Agent</dt>
            <dd>{{ selectedAgent?.name || '未选择' }}</dd>
          </div>
          <div>
            <dt>读取范围</dt>
            <dd>{{ form.readScope === 'FULL' ? '全文' : `${form.focusRegions.length} 个区域` }}</dd>
          </div>
          <div>
            <dt>处理方式</dt>
            <dd>
              {{
                options?.documentType === 'FORMAL' ? '生成变更请求' : options ? '直接更新草稿' : '—'
              }}
            </dd>
          </div>
          <div>
            <dt>预算</dt>
            <dd>{{ formatTokens(effectiveBudget) }} Token</dd>
          </div>
          <div>
            <dt>超时</dt>
            <dd>{{ selectedAgent?.executionTimeoutSeconds || '—' }} 秒</dd>
          </div>
        </dl>
      </aside>
    </div>
    <el-dialog
      v-model="rangeDialogVisible"
      title="管理关注区域"
      width="min(1120px, 94vw)"
      destroy-on-close
      ><p class="range-help">
        在左侧拖选文字，右侧填写处理要求并添加。点击已选区域可重新定位和修改。
      </p>
      <div class="range-editor">
        <textarea
          ref="rangeDocumentRef"
          class="range-document"
          :value="documentContent"
          readonly
          @select="captureSelection"
        ></textarea>
        <aside class="range-panel">
          <section class="range-current">
            <strong>{{
              editingRegionIndex === null ? '当前选区' : '修改区域 ' + (editingRegionIndex + 1)
            }}</strong
            ><span>{{ pendingLength > 0 ? `${pendingLength} 个字符` : '请在左侧选择文字' }}</span>
            <p>{{ pendingPreview || '选择后，这里会显示选中的内容。' }}</p>
            <el-input
              v-model="pendingInstruction"
              type="textarea"
              :rows="4"
              maxlength="1000"
              show-word-limit
              placeholder="填写希望 Agent 如何处理，例如：补充背景和示例"
            />
            <div class="range-current-actions">
              <el-button v-if="editingRegionIndex !== null" @click="resetPendingRegion"
                >取消修改</el-button
              ><el-button
                type="primary"
                :disabled="pendingLength <= 0 || !pendingInstruction.trim()"
                @click="savePendingRegion"
                >{{ editingRegionIndex === null ? '添加此区域' : '保存修改' }}</el-button
              >
            </div>
          </section>
          <section class="range-selected">
            <header>
              <strong>已选区域</strong><span>{{ draftRegions.length }} / 20</span>
            </header>
            <div v-if="!draftRegions.length" class="range-empty">尚未添加关注区域。</div>
            <article
              v-for="(region, index) in draftRegions"
              :key="`${region.start}-${index}`"
              class="range-item"
              :class="{ 'range-item--active': editingRegionIndex === index }"
              @click="editRegion(index)"
            >
              <span>区域 {{ index + 1 }} · {{ region.length }} 字符</span>
              <p>{{ region.textPreview }}</p>
              <small>{{ region.instruction }}</small
              ><el-button text type="danger" @click.stop="removeDraftRegion(index)">删除</el-button>
            </article>
          </section>
        </aside>
      </div>
      <template #footer
        ><el-button @click="rangeDialogVisible = false">取消</el-button
        ><el-button type="primary" @click="completeRangeEditing"
          >完成（{{ draftRegions.length }} 个区域）</el-button
        ></template
      ></el-dialog
    >
    <footer class="create-actions">
      <el-button @click="router.push(`/spaces/${spaceId}/tasks`)">取消</el-button
      ><el-button type="primary" :loading="creating" @click="submit">启动任务</el-button>
    </footer>
  </section>
</template>
<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElDialog,
  ElForm,
  ElFormItem,
  ElInput,
  ElMessage,
  ElOption,
  ElSelect,
} from 'element-plus'
import { normalizeApiError } from '@/api/errors'
import { getDocument, listDocumentTree } from '@/features/document/api/document-api'
import type { DocumentTreeNode } from '@/features/document/types'
import {
  createTask,
  getTaskCreateOptions,
  getTaskDraft,
  saveTaskDraft,
  updateTaskDraft,
} from '@/features/task/api/task-api'
import type {
  TaskCreateOptions,
  TaskDraft,
  TaskFocusRegion,
  TaskReadScope,
} from '@/features/task/types'
import type { EntityId } from '@/features/workspace/types'
import DataState from '@/shared/components/DataState.vue'
import PageHeader from '@/shared/components/PageHeader.vue'
import { useWorkspaceStore } from '@/stores/workspace'

const route = useRoute(),
  router = useRouter(),
  workspace = useWorkspaceStore(),
  spaceId = computed(() => route.params.spaceId as string)
const form = reactive<{
  documentId: EntityId | null
  agentId: EntityId | null
  name: string
  instruction: string
  tokenBudget: number | null
  readScope: TaskReadScope
  focusRegions: TaskFocusRegion[]
}>({
  documentId: null,
  agentId: null,
  name: '',
  instruction: '',
  tokenBudget: null,
  readScope: 'FULL',
  focusRegions: [],
})
const documents = ref<DocumentTreeNode[]>([]),
  options = ref<TaskCreateOptions | null>(null),
  optionsLoading = ref(false),
  optionsError = ref(''),
  creating = ref(false),
  saving = ref(false),
  draftId = ref<EntityId | null>(null),
  documentContent = ref(''),
  rangeDialogVisible = ref(false),
  rangeDocumentRef = ref<HTMLTextAreaElement | null>(null),
  draftRegions = ref<TaskFocusRegion[]>([]),
  editingRegionIndex = ref<number | null>(null),
  pendingStart = ref(0),
  pendingLength = ref(0),
  pendingPreview = ref(''),
  pendingInstruction = ref(''),
  showAllFocusRegions = ref(false)
let controller: AbortController | null = null
const selectedDocument = computed(() =>
    documents.value.find((x) => String(x.id) === String(form.documentId)),
  ),
  selectedAgent = computed(() =>
    options.value?.agents.find((x) => String(x.id) === String(form.agentId)),
  )
const visibleFocusRegions = computed(() =>
  (showAllFocusRegions.value ? form.focusRegions : form.focusRegions.slice(0, 2)).map(
    (region, originalIndex) => ({ ...region, originalIndex }),
  ),
)
const effectiveBudget = computed(() => {
  const values = [
    form.tokenBudget,
    selectedAgent.value?.tokenBudget,
    options.value?.spaceTokenBudget,
  ].filter((x): x is number => x != null)
  return values.length ? Math.min(...values) : null
})
onMounted(async () => {
  controller = new AbortController()
  try {
    documents.value = flatten(
      await listDocumentTree(spaceId.value, { status: 'NORMAL', signal: controller.signal }),
    ).filter((x) => x.nodeType === 'DOCUMENT')
    const requestedDraftId = route.query.draftId
    let savedDraft: TaskDraft | null = null
    if (requestedDraftId) {
      savedDraft = await getTaskDraft(requestedDraftId as EntityId, controller.signal)
      form.documentId = savedDraft.documentId
      form.agentId = savedDraft.agentId
    } else {
      const requested = route.query.documentId
      if (requested) form.documentId = requested as string
      else if (documents.value[0]) form.documentId = documents.value[0].id
    }
    if (form.documentId) {
      await loadOptions()
    }
    if (savedDraft) {
      draftId.value = savedDraft.id
      form.name = savedDraft.name || ''
      form.instruction = savedDraft.instruction || ''
      form.tokenBudget = savedDraft.tokenBudget
      form.readScope = savedDraft.readScope
      form.focusRegions = savedDraft.focusRegions.map((region) => ({ ...region }))
      form.agentId = savedDraft.agentId
    }
  } catch (e) {
    if (!controller.signal.aborted) optionsError.value = normalizeApiError(e).message
  }
})
onBeforeUnmount(() => controller?.abort())
function flatten(nodes: DocumentTreeNode[]): DocumentTreeNode[] {
  return nodes.flatMap((node) => [node, ...flatten(node.children || [])])
}
async function loadOptions() {
  if (!form.documentId) return
  controller?.abort()
  controller = new AbortController()
  optionsLoading.value = true
  optionsError.value = ''
  form.agentId = null
  form.focusRegions = []
  showAllFocusRegions.value = false
  try {
    const [creationOptions, document] = await Promise.all([
      getTaskCreateOptions(spaceId.value, form.documentId, controller.signal),
      getDocument(form.documentId, controller.signal),
    ])
    options.value = creationOptions
    documentContent.value = document.content || ''
    const requested = route.query.agentId
    form.agentId =
      options.value.agents.find((x) => String(x.id) === String(requested))?.id ??
      options.value.agents[0]?.id ??
      null
  } catch (e) {
    if (!controller.signal.aborted) optionsError.value = normalizeApiError(e).message
  } finally {
    if (!controller.signal.aborted) optionsLoading.value = false
  }
}
function openRangeDialog(regionIndex?: number) {
  draftRegions.value = form.focusRegions.map((region) => ({ ...region }))
  resetPendingRegion()
  rangeDialogVisible.value = true
  if (regionIndex !== undefined) nextTick(() => editRegion(regionIndex))
}
function captureSelection(event: Event) {
  const target = event.target as HTMLTextAreaElement
  pendingStart.value = target.selectionStart
  pendingLength.value = target.selectionEnd - target.selectionStart
  pendingPreview.value = target.value
    .slice(target.selectionStart, target.selectionEnd)
    .replace(/\s+/g, ' ')
    .slice(0, 220)
}
function resetPendingRegion() {
  editingRegionIndex.value = null
  pendingStart.value = 0
  pendingLength.value = 0
  pendingPreview.value = ''
  pendingInstruction.value = ''
}
function savePendingRegion() {
  if (pendingLength.value <= 0 || !pendingInstruction.value.trim()) return false
  if (editingRegionIndex.value === null && draftRegions.value.length >= 20) {
    ElMessage.warning('每个任务最多添加 20 个关注区域')
    return false
  }
  const region = {
    start: pendingStart.value,
    length: pendingLength.value,
    textPreview: pendingPreview.value.slice(0, 500),
    instruction: pendingInstruction.value.trim(),
  }
  if (editingRegionIndex.value === null) draftRegions.value.push(region)
  else draftRegions.value.splice(editingRegionIndex.value, 1, region)
  draftRegions.value.sort((a, b) => a.start - b.start)
  resetPendingRegion()
  return true
}
function editRegion(index: number) {
  const region = draftRegions.value[index]
  if (!region) return
  editingRegionIndex.value = index
  pendingStart.value = region.start
  pendingLength.value = region.length
  pendingPreview.value =
    region.textPreview ||
    documentContent.value
      .slice(region.start, region.start + region.length)
      .replace(/\s+/g, ' ')
      .slice(0, 220)
  pendingInstruction.value = region.instruction || ''
  nextTick(() => {
    const target = rangeDocumentRef.value
    if (!target) return
    target.focus()
    target.setSelectionRange(region.start, region.start + region.length)
    const lineHeight = Number.parseFloat(window.getComputedStyle(target).lineHeight) || 24
    const lines = documentContent.value.slice(0, region.start).split('\n').length
    target.scrollTop = Math.max(0, (lines - 3) * lineHeight)
  })
}
function removeDraftRegion(index: number) {
  draftRegions.value.splice(index, 1)
  if (editingRegionIndex.value === index) resetPendingRegion()
  else if (editingRegionIndex.value !== null && editingRegionIndex.value > index)
    editingRegionIndex.value--
}
function completeRangeEditing() {
  if (pendingLength.value > 0 || pendingInstruction.value.trim()) {
    if (!savePendingRegion()) {
      ElMessage.warning('请完整选择文字并填写处理要求')
      return
    }
  }
  form.focusRegions = draftRegions.value.map((region) => ({ ...region }))
  showAllFocusRegions.value = false
  rangeDialogVisible.value = false
}
function removeRegion(index: number) {
  form.focusRegions.splice(index, 1)
}
function payload() {
  return {
    spaceId: spaceId.value,
    agentId: form.agentId,
    documentId: form.documentId,
    name: form.name.trim(),
    instruction: form.instruction.trim(),
    tokenBudget: form.tokenBudget,
    readScope: form.readScope,
    focusRegions: form.focusRegions,
  }
}
async function submit() {
  if (!form.documentId || !form.agentId || !form.name.trim() || !form.instruction.trim()) {
    ElMessage.warning('请完整填写任务名称、目标文档、Agent 和目标描述')
    return
  }
  if (form.readScope === 'RANGES' && !form.focusRegions.length) {
    ElMessage.warning('请至少添加一个可读取区域')
    return
  }
  creating.value = true
  try {
    const task = await createTask(payload() as Parameters<typeof createTask>[0])
    ElMessage.success(`任务 ${task.taskNo} 已创建`)
    await router.push(`/spaces/${spaceId.value}/tasks/${task.id}`)
  } catch (e) {
    ElMessage.error(normalizeApiError(e).message)
  } finally {
    creating.value = false
  }
}
async function saveDraft() {
  saving.value = true
  try {
    const draft =
      draftId.value === null
        ? await saveTaskDraft(payload())
        : await updateTaskDraft(draftId.value, payload())
    draftId.value = draft.id
    ElMessage.success('任务草稿已保存')
  } catch (e) {
    ElMessage.error(normalizeApiError(e).message)
  } finally {
    saving.value = false
  }
}
function formatTokens(value: number | null | undefined) {
  return value == null ? '未设置' : value.toLocaleString('zh-CN')
}
</script>
<style scoped>
.create-page {
  display: grid;
  gap: 24px;
}
.create-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 340px;
  gap: 24px;
}
.create-form {
  display: grid;
  gap: 16px;
}
.form-section,
.summary {
  padding: 20px;
}
.form-section h2,
.summary h2 {
  margin: 0 0 18px;
  font-size: 18px;
}
.form-section h2 b {
  display: inline-grid;
  width: 28px;
  height: 28px;
  margin-right: 10px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: var(--adw-color-primary);
}
.document-fields {
  display: grid;
  grid-template-columns: minmax(150px, 0.7fr) minmax(300px, 2fr) minmax(130px, 0.6fr);
  gap: 12px;
}
.document-fields .el-select {
  width: 100%;
}
.range-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
  padding: 14px;
  border: 1px solid #bfd0fa;
  border-radius: 8px;
  background: var(--adw-color-primary-soft);
}
.range-summary p,
.range-help {
  margin: 5px 0 0;
  color: var(--adw-text-secondary);
}
.focus-list {
  display: grid;
  gap: 10px;
  margin-bottom: 16px;
}
.focus-list article {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border: 1px solid var(--adw-border-color);
  border-radius: 8px;
}
.focus-list p {
  margin: 6px 0;
  color: var(--adw-text-secondary);
}
.focus-list small {
  color: var(--adw-color-primary);
}
.focus-actions {
  display: flex;
  flex: none;
}
.range-editor {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(300px, 1fr);
  gap: 16px;
  margin-top: 16px;
}
.range-document {
  box-sizing: border-box;
  width: 100%;
  height: 560px;
  padding: 18px;
  border: 1px solid var(--adw-border-color);
  border-radius: 8px;
  resize: none;
  color: var(--adw-text-primary);
  background: #fbfcfe;
  font:
    14px/1.7 ui-monospace,
    monospace;
}
.range-panel {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 12px;
  height: 560px;
}
.range-current,
.range-selected {
  padding: 14px;
  border: 1px solid var(--adw-border-color);
  border-radius: 8px;
}
.range-current > span {
  float: right;
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.range-current > p {
  max-height: 72px;
  margin: 10px 0;
  padding: 10px;
  overflow: auto;
  border-radius: 6px;
  color: var(--adw-text-secondary);
  background: var(--adw-surface-muted);
}
.range-current-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
.range-selected {
  min-height: 0;
  overflow: auto;
}
.range-selected header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 10px;
}
.range-selected header span {
  color: var(--adw-text-tertiary);
}
.range-empty {
  padding: 22px 8px;
  text-align: center;
  color: var(--adw-text-tertiary);
}
.range-item {
  position: relative;
  display: block;
  width: 100%;
  margin-bottom: 8px;
  padding: 10px 50px 10px 10px;
  text-align: left;
  border: 1px solid var(--adw-border-color-light);
  border-radius: 7px;
  background: #fff;
  cursor: pointer;
}
.range-item:hover,
.range-item--active {
  border-color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.range-item > span {
  font-weight: 600;
}
.range-item p {
  margin: 5px 0;
  overflow: hidden;
  color: var(--adw-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.range-item small {
  display: block;
  overflow: hidden;
  color: var(--adw-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.range-item .el-button {
  position: absolute;
  top: 4px;
  right: 4px;
}
.agent-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.agent-option {
  padding: 18px;
  text-align: left;
  border: 1px solid var(--adw-border-color);
  border-radius: 10px;
  background: #fff;
  cursor: pointer;
}
.agent-option--active {
  border-color: var(--adw-color-primary);
  box-shadow: 0 0 0 2px var(--adw-color-primary-soft);
}
.agent-option span {
  display: block;
  margin: 8px 0 18px;
  color: var(--adw-text-secondary);
}
.agent-option dl {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 0;
}
.agent-option dt {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.agent-option dd {
  margin: 5px 0 0;
}
.summary {
  align-self: start;
  position: sticky;
  top: 0;
}
.summary dl {
  margin: 0;
}
.summary dl div {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  padding: 16px 0;
  border-bottom: 1px solid var(--adw-border-color-light);
}
.summary dd {
  margin: 0;
  text-align: right;
}
.create-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
@media (max-width: 1000px) {
  .create-layout {
    grid-template-columns: 1fr;
  }
  .summary {
    position: static;
  }
  .agent-grid {
    grid-template-columns: 1fr;
  }
  .range-editor {
    grid-template-columns: 1fr;
  }
  .range-document {
    height: 360px;
  }
  .range-panel {
    height: auto;
  }
  .range-selected {
    max-height: 320px;
  }
}
@media (max-width: 720px) {
  .document-fields {
    grid-template-columns: 1fr;
  }
  .range-summary {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
