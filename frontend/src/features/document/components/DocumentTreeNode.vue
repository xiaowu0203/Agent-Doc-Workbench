<template>
  <div class="document-tree-node">
    <div
      class="document-tree-node__row"
      :title="canMove ? '拖动以移动' : undefined"
      :class="{
        'document-tree-node__row--selected': String(selectedId) === String(node.id),
        'document-tree-node__row--dragging': String(draggingId) === String(node.id),
        'document-tree-node__row--drop-target': String(dropTargetId) === String(node.id),
      }"
      :data-document-tree-node-id="node.id"
      :data-document-tree-node-type="node.nodeType"
      role="button"
      tabindex="0"
      :aria-expanded="node.children.length ? expanded : undefined"
      @click="handleRowClick"
      @keydown.enter.prevent="handleRowClick"
      @keydown.space.prevent="handleRowClick"
      @pointerdown="handlePointerDown"
    >
      <span
        v-if="node.children.length"
        class="document-tree-node__toggle"
        :aria-label="expanded ? '收起目录' : '展开目录'"
        @click.stop="$emit('toggle', node.id)"
      >
        <el-icon><component :is="expanded ? ArrowDown : ArrowRight" /></el-icon>
      </span>
      <span v-else class="document-tree-node__toggle document-tree-node__toggle--empty"></span>
      <el-icon class="document-tree-node__icon">
        <component
          :is="node.nodeType === 'DIRECTORY' || node.children.length ? Folder : Document"
        />
      </el-icon>
      <input
        v-if="editing"
        ref="titleInput"
        v-model="draftTitle"
        class="document-tree-node__title-input"
        maxlength="200"
        aria-label="编辑名称"
        @click.stop
        @pointerdown.stop
        @keydown.enter.prevent.stop="commitRename"
        @keydown.esc.prevent.stop="cancelRename"
        @blur="commitRename"
      />
      <span v-else class="document-tree-node__title" @dblclick.stop="startRename">
        {{ node.title }}
      </span>
      <span v-if="node.nodeType !== 'DIRECTORY'" class="document-tree-node__type">{{
        node.docType === 'FORMAL' ? '正式' : '草稿'
      }}</span>
    </div>
    <div v-if="node.children.length && expanded" class="document-tree-node__children">
      <DocumentTreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :selected-id="selectedId"
        :expanded-ids="expandedIds"
        :can-move="canMove"
        :can-rename="canRename"
        :renaming-id="renamingId"
        :dragging-id="draggingId"
        :drop-target-id="dropTargetId"
        @select="$emit('select', $event)"
        @toggle="$emit('toggle', $event)"
        @pointer-start="$emit('pointer-start', $event)"
        @rename="$emit('rename', $event)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown, ArrowRight, Document, Folder } from '@element-plus/icons-vue'
import { ElIcon } from 'element-plus'
import { computed, nextTick, ref } from 'vue'

import type { EntityId } from '@/features/workspace/types'
import type { DocumentTreeNode as DocumentTreeNodeData } from '@/features/document/types'

defineOptions({ name: 'DocumentTreeNode' })

const props = defineProps<{
  node: DocumentTreeNodeData
  selectedId: EntityId | null
  expandedIds: Set<string>
  canMove: boolean
  canRename: boolean
  renamingId: EntityId | null
  draggingId: EntityId | null
  dropTargetId: EntityId | null
}>()

const emit = defineEmits<{
  select: [node: DocumentTreeNodeData]
  toggle: [id: EntityId]
  'pointer-start': [payload: { node: DocumentTreeNodeData; event: PointerEvent }]
  rename: [payload: { node: DocumentTreeNodeData; title: string }]
}>()

const expanded = computed(() => props.expandedIds.has(String(props.node.id)))
const editing = ref(false)
const draftTitle = ref('')
const titleInput = ref<HTMLInputElement | null>(null)

function handleRowClick(): void {
  emit('select', props.node)
  if (props.node.children.length) emit('toggle', props.node.id)
}

function handlePointerDown(event: PointerEvent): void {
  if (!props.canMove || event.button !== 0) return
  emit('pointer-start', { node: props.node, event })
}

function startRename(): void {
  if (!props.canRename || props.renamingId !== null) return
  draftTitle.value = props.node.title
  editing.value = true
  void nextTick(() => {
    titleInput.value?.focus()
    titleInput.value?.select()
  })
}

function cancelRename(): void {
  editing.value = false
  draftTitle.value = ''
}

function commitRename(): void {
  if (!editing.value || props.renamingId !== null) return
  const title = draftTitle.value.trim()
  if (!title || title === props.node.title) {
    cancelRename()
    return
  }
  editing.value = false
  draftTitle.value = ''
  emit('rename', { node: props.node, title })
}
</script>

<style scoped>
.document-tree-node__row {
  display: flex;
  width: 100%;
  min-height: 38px;
  align-items: center;
  gap: 7px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  color: var(--adw-text-secondary);
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.document-tree-node__row[title='拖动以移动'] {
  cursor: grab;
  user-select: none;
}

.document-tree-node__row[title='拖动以移动']:active {
  cursor: grabbing;
}

.document-tree-node__row:hover,
.document-tree-node__row--selected,
.document-tree-node__row--drop-target {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}

.document-tree-node__row--dragging {
  opacity: 0.45;
}

.document-tree-node__toggle {
  display: inline-flex;
  width: 16px;
  height: 20px;
  flex: 0 0 16px;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}

.document-tree-node__toggle--empty {
  cursor: default;
}

.document-tree-node__toggle .el-icon {
  font-size: 13px;
}

.document-tree-node__icon {
  flex: 0 0 auto;
  color: var(--adw-color-primary);
}

.document-tree-node__title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-tree-node__title-input {
  min-width: 0;
  width: 100%;
  height: 28px;
  padding: 0 6px;
  border: 1px solid var(--adw-color-primary);
  border-radius: 4px;
  outline: none;
  color: var(--adw-text-primary);
  background: var(--adw-surface);
  font: inherit;
}

.document-tree-node__type {
  margin-left: auto;
  color: var(--adw-text-tertiary);
  font-size: 11px;
}

.document-tree-node__children {
  padding-left: 18px;
}
</style>
