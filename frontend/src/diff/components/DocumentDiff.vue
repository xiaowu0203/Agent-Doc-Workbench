<template>
  <div class="document-diff" :class="`document-diff--${mode}`">
    <div v-if="!hunks.length" class="document-diff__empty">两个版本没有文本差异</div>
    <article v-for="hunk in hunks" :key="hunk.key" class="diff-hunk">
      <header class="diff-hunk__header">
        <el-checkbox
          v-if="selectable"
          :model-value="selectedKeys.includes(hunk.key)"
          @change="toggle(hunk.key, Boolean($event))"
        >
          接受此变更块
        </el-checkbox>
        <span v-else>变更块</span>
        <span class="diff-hunk__tools">
          <small
            ><b>+{{ hunk.added }}</b> / <i>-{{ hunk.removed }}</i></small
          >
          <el-button v-if="commentable" size="small" link @click="$emit('comment', hunk.key)">
            批注
          </el-button>
        </span>
      </header>

      <div v-if="mode === 'inline'" class="diff-lines">
        <div
          v-for="(line, index) in hunk.lines"
          :key="index"
          class="diff-line"
          :class="`is-${line.kind}`"
        >
          <span class="diff-line__number">{{ line.oldLine ?? '' }}</span>
          <span class="diff-line__number">{{ line.newLine ?? '' }}</span>
          <span class="diff-line__sign">{{ sign(line.kind) }}</span>
          <code>{{ line.text || ' ' }}</code>
        </div>
      </div>

      <div v-else class="split-diff">
        <div class="split-diff__side">
          <div class="split-diff__label">原文</div>
          <div
            v-for="(line, index) in hunk.lines.filter((item) => item.kind !== 'added')"
            :key="index"
            class="diff-line"
            :class="`is-${line.kind}`"
          >
            <span class="diff-line__number">{{ line.oldLine ?? '' }}</span>
            <span class="diff-line__sign">{{ sign(line.kind) }}</span>
            <code>{{ line.text || ' ' }}</code>
          </div>
        </div>
        <div class="split-diff__side">
          <div class="split-diff__label">变更后</div>
          <div
            v-for="(line, index) in hunk.lines.filter((item) => item.kind !== 'removed')"
            :key="index"
            class="diff-line"
            :class="`is-${line.kind}`"
          >
            <span class="diff-line__number">{{ line.newLine ?? '' }}</span>
            <span class="diff-line__sign">{{ sign(line.kind) }}</span>
            <code>{{ line.text || ' ' }}</code>
          </div>
        </div>
      </div>
    </article>
  </div>
</template>

<script setup lang="ts">
import { ElButton, ElCheckbox } from 'element-plus'
import { computed } from 'vue'

import { buildDiffHunks, type DiffLineKind } from '@/diff/utils/line-diff'

const props = defineProps<{
  oldContent: string
  newContent: string
  mode: 'inline' | 'split'
  selectable?: boolean
  commentable?: boolean
  selectedKeys: string[]
}>()

const emit = defineEmits<{
  'update:selectedKeys': [value: string[]]
  comment: [key: string]
}>()

const hunks = computed(() => buildDiffHunks(props.oldContent, props.newContent))

function toggle(key: string, selected: boolean): void {
  const keys = new Set(props.selectedKeys)
  if (selected) keys.add(key)
  else keys.delete(key)
  emit('update:selectedKeys', [...keys])
}

function sign(kind: DiffLineKind): string {
  if (kind === 'added') return '+'
  if (kind === 'removed') return '−'
  return ' '
}
</script>

<style scoped>
.document-diff {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.document-diff__empty {
  padding: 72px 20px;
  color: var(--adw-text-secondary);
  text-align: center;
}

.diff-hunk {
  overflow: hidden;
  border: 1px solid var(--adw-border-color);
  border-radius: 8px;
  background: #fff;
}

.diff-hunk__header {
  display: flex;
  min-height: 38px;
  align-items: center;
  justify-content: space-between;
  padding: 0 12px;
  color: var(--adw-text-secondary);
  background: #f6f8fb;
  border-bottom: 1px solid var(--adw-border-color-light);
}

.diff-hunk__header b {
  color: var(--adw-color-success);
  font-style: normal;
}
.diff-hunk__header i {
  color: var(--adw-color-danger);
  font-style: normal;
}
.diff-hunk__tools {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.diff-lines {
  overflow-x: auto;
}

.diff-line {
  display: grid;
  min-height: 28px;
  grid-template-columns: 46px 46px 24px minmax(320px, 1fr);
  align-items: stretch;
  font-size: 13px;
  line-height: 28px;
}

.diff-line__number {
  color: #8b95a6;
  background: rgb(15 23 42 / 3%);
  border-right: 1px solid rgb(15 23 42 / 7%);
  text-align: right;
  padding-right: 9px;
  user-select: none;
}

.diff-line__sign {
  text-align: center;
  user-select: none;
}
.diff-line code {
  padding: 0 10px;
  white-space: pre;
}
.diff-line.is-added {
  background: #ebf8f1;
}
.diff-line.is-added .diff-line__sign {
  color: #087a50;
}
.diff-line.is-removed {
  background: #fff0f0;
}
.diff-line.is-removed .diff-line__sign {
  color: #bd3039;
}

.split-diff {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
}

.split-diff__side {
  min-width: 0;
  overflow-x: auto;
}
.split-diff__side + .split-diff__side {
  border-left: 1px solid var(--adw-border-color);
}
.split-diff__label {
  padding: 7px 12px;
  color: var(--adw-text-secondary);
  background: #fbfcfd;
  font-size: 12px;
}
.split-diff .diff-line {
  grid-template-columns: 46px 24px minmax(260px, 1fr);
}

@media (max-width: 900px) {
  .split-diff {
    grid-template-columns: 1fr;
  }
  .split-diff__side + .split-diff__side {
    border-top: 1px solid var(--adw-border-color);
    border-left: 0;
  }
}
</style>
