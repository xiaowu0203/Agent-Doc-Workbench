<template>
  <SkillCard
    v-if="capability.type === 'SKILL'"
    :skill="skillCard"
    :layout="layout"
    :can-manage="true"
    @detail="$emit('detail', capability)"
    @versions="$emit('versions', capability)"
    @edit="$emit('edit', capability)"
    @upload="$emit('upload', capability)"
    @toggle="$emit('toggle', capability)"
  />
  <article v-else class="capability-card" :class="`capability-card--${layout}`">
    <header class="capability-card__header">
      <span class="capability-card__icon" :class="`capability-card__icon--${capability.type}`">
        <el-icon><component :is="typeIcon" /></el-icon>
      </span>
      <div class="capability-card__identity">
        <el-tag size="small" effect="light" :type="tagType">{{ typeLabel }}</el-tag>
        <strong>{{ capability.displayName }}</strong>
        <code>{{ capability.technicalKey }}</code>
      </div>
      <el-dropdown trigger="click" @command="handleCommand">
        <button class="capability-card__more" type="button" aria-label="能力操作">
          <el-icon><MoreFilled /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="detail">查看详情</el-dropdown-item>
            <el-dropdown-item command="versions">版本管理</el-dropdown-item>
            <el-dropdown-item command="edit">编辑元数据</el-dropdown-item>
            <el-dropdown-item command="toggle" :divided="true">
              {{ capability.status === 1 ? '停用' : '启用' }} {{ typeLabel }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <p class="capability-card__description">{{ capability.description || '暂未填写能力说明' }}</p>

    <dl class="capability-card__metrics">
      <div>
        <dt>状态</dt>
        <dd :class="capability.status === 1 ? 'is-enabled' : 'is-disabled'">
          <i></i>{{ capability.status === 1 ? '已启用' : '已停用' }}
        </dd>
      </div>
      <div>
        <dt>最新版本</dt>
        <dd>
          {{
            capability.latestPublishedVersionNo === null
              ? '未发布'
              : `v${capability.latestPublishedVersionNo}`
          }}
        </dd>
      </div>
      <div>
        <dt>安装</dt>
        <dd>{{ capability.installationCount }} 个空间</dd>
      </div>
    </dl>

    <footer class="capability-card__actions">
      <el-button @click="$emit('versions', capability)">版本管理</el-button>
      <el-button type="primary" @click="$emit('detail', capability)">查看详情</el-button>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { Collection, Connection, Cpu, MoreFilled } from '@element-plus/icons-vue'
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon, ElTag } from 'element-plus'
import { computed } from 'vue'

import SkillCard from '@/features/skill/components/SkillCard.vue'
import { toSystemSkill } from '@/features/system-capability/skill-adapter'
import type { SystemCapability } from '@/features/system-capability/types'

const props = defineProps<{ capability: SystemCapability; layout: 'grid' | 'list' }>()
const emit = defineEmits<{
  detail: [capability: SystemCapability]
  versions: [capability: SystemCapability]
  edit: [capability: SystemCapability]
  upload: [capability: SystemCapability]
  toggle: [capability: SystemCapability]
}>()

const skillCard = computed(() => toSystemSkill(props.capability))

const typeLabel = computed(
  () =>
    ({ SKILL: 'Skill', AGENT_TEMPLATE: 'Agent 模板', MCP_TEMPLATE: 'MCP 模板' })[
      props.capability.type
    ],
)
const tagType = computed(
  () =>
    ({ SKILL: 'primary', AGENT_TEMPLATE: 'success', MCP_TEMPLATE: 'info' })[
      props.capability.type
    ] as 'primary' | 'success' | 'info',
)
const typeIcon = computed(
  () =>
    ({ SKILL: Collection, AGENT_TEMPLATE: Cpu, MCP_TEMPLATE: Connection })[props.capability.type],
)
function handleCommand(command: string): void {
  if (command === 'detail') emit('detail', props.capability)
  if (command === 'versions') emit('versions', props.capability)
  if (command === 'edit') emit('edit', props.capability)
  if (command === 'upload') emit('upload', props.capability)
  if (command === 'toggle') emit('toggle', props.capability)
}
</script>

<style scoped>
.capability-card {
  display: flex;
  min-width: 0;
  min-height: 330px;
  flex-direction: column;
  padding: var(--adw-space-5);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  background: var(--adw-surface);
  box-shadow: var(--adw-shadow-card);
}
.capability-card__header {
  display: flex;
  align-items: flex-start;
  gap: var(--adw-space-3);
}
.capability-card__icon {
  display: inline-flex;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  font-size: 24px;
}
.capability-card__icon--SKILL {
  color: #6246ea;
  background: #eeeaff;
}
.capability-card__icon--AGENT_TEMPLATE {
  color: #1769e8;
  background: #e2f0ff;
}
.capability-card__icon--MCP_TEMPLATE {
  color: #0897af;
  background: #dff8fa;
}
.capability-card__identity {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}
.capability-card__identity :deep(.el-tag) {
  justify-self: start;
}
.capability-card__identity strong,
.capability-card__identity code {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.capability-card__identity strong {
  color: var(--adw-text-primary);
  font-size: 16px;
}
.capability-card__identity code {
  color: var(--adw-text-secondary);
  font-family: inherit;
  font-size: 12px;
}
.capability-card__more {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: var(--adw-radius-sm);
  background: transparent;
  color: var(--adw-text-secondary);
  cursor: pointer;
}
.capability-card__more:hover {
  background: var(--adw-surface-muted);
}
.capability-card__description {
  display: -webkit-box;
  min-height: 42px;
  margin: var(--adw-space-4) 0;
  overflow: hidden;
  color: var(--adw-text-secondary);
  font-size: 13px;
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}
.capability-card__metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 0;
  padding: var(--adw-space-3) 0;
  border-top: 1px solid var(--adw-border-color-light);
  border-bottom: 1px solid var(--adw-border-color-light);
}
.capability-card__metrics div {
  min-width: 0;
  padding: 0 var(--adw-space-3);
}
.capability-card__metrics div + div {
  border-left: 1px solid var(--adw-border-color-light);
}
.capability-card__metrics dt {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.capability-card__metrics dd {
  margin: 5px 0 0;
  color: var(--adw-text-primary);
  font-size: 13px;
  white-space: nowrap;
}
.capability-card__metrics dd i {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 6px;
  border-radius: 50%;
  background: currentColor;
}
.is-enabled {
  color: var(--adw-color-success) !important;
}
.is-disabled {
  color: #d99000 !important;
}
.capability-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--adw-space-3);
  margin-top: auto;
  padding-top: var(--adw-space-4);
}
.capability-card__actions :deep(.el-button) {
  width: 100%;
  margin: 0;
}
.capability-card--list {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(260px, 1.2fr) minmax(220px, 1fr) minmax(240px, 1fr);
  align-items: center;
  gap: var(--adw-space-5);
}
.capability-card--list .capability-card__description {
  margin: 0;
}
.capability-card--list .capability-card__metrics {
  border: 0;
  padding: 0;
}
.capability-card--list .capability-card__actions {
  grid-column: 1 / -1;
  margin: 0;
  padding-top: var(--adw-space-3);
  border-top: 1px solid var(--adw-border-color-light);
  justify-self: end;
}
.capability-card--list .capability-card__actions {
  display: flex;
}
.capability-card--list .capability-card__actions :deep(.el-button) {
  width: auto;
  min-width: 120px;
}
@media (max-width: 960px) {
  .capability-card--list {
    display: flex;
  }
  .capability-card--list .capability-card__description {
    margin: var(--adw-space-4) 0;
  }
  .capability-card--list .capability-card__metrics {
    padding: var(--adw-space-3) 0;
    border-top: 1px solid var(--adw-border-color-light);
    border-bottom: 1px solid var(--adw-border-color-light);
  }
  .capability-card--list .capability-card__actions {
    display: grid;
    margin-top: auto;
    padding-top: var(--adw-space-4);
    border: 0;
  }
  .capability-card--list .capability-card__actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
