<template>
  <article class="agent-card" :class="{ 'agent-card--list': layout === 'list' }">
    <header class="agent-card__heading">
      <span class="agent-card__icon" :style="{ background: iconBackground }">
        <el-icon><Aim /></el-icon>
      </span>
      <div class="agent-card__identity">
        <strong :title="agent.name">{{ agent.name }}</strong>
        <p :title="agent.description || ''">{{ agent.description || '暂未填写 Agent 描述' }}</p>
      </div>
      <el-tag :type="agent.status === 'ENABLED' ? 'success' : 'info'" effect="light" size="small">
        {{ agent.status === 'ENABLED' ? '已启用' : '已停用' }}
      </el-tag>
      <el-dropdown v-if="canManage" trigger="click" @command="handleCommand">
        <button class="agent-card__more" type="button" aria-label="Agent 操作">
          <el-icon><MoreFilled /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="edit">编辑配置</el-dropdown-item>
            <el-dropdown-item command="toggle">
              {{ agent.status === 'ENABLED' ? '停用 Agent' : '启用 Agent' }}
            </el-dropdown-item>
            <el-dropdown-item command="delete" divided>删除 Agent</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <div class="agent-card__tags">
      <el-tag effect="plain" size="small">{{ agent.modelDisplayName || '未知模型' }}</el-tag>
      <el-tag effect="plain" size="small" type="primary">{{ agent.skillSelectionMode }}</el-tag>
      <el-tag v-if="!agent.externalMcpEnabled" effect="plain" size="small" type="info">
        外部 MCP 关闭
      </el-tag>
    </div>

    <dl class="agent-card__metrics">
      <div>
        <dd>{{ agent.skillCount }}</dd>
        <dt>Skill</dt>
      </div>
      <div>
        <dd>{{ agent.mcpCount }}</dd>
        <dt>MCP</dt>
      </div>
      <el-tooltip content="全部绑定 Skill 声明工具与绑定 MCP 已发现工具，按名称去重">
        <div>
          <dd>{{ agent.toolCount }}</dd>
          <dt>工具</dt>
        </div>
      </el-tooltip>
    </dl>

    <dl class="agent-card__limits">
      <div>
        <dt>Token 预算</dt>
        <dd>{{ tokenBudgetLabel }}</dd>
      </div>
      <div>
        <dt>超时（秒）</dt>
        <dd>{{ agent.executionTimeoutSeconds }}</dd>
      </div>
    </dl>

    <footer class="agent-card__actions">
      <el-button @click="$emit('configure', agent)">
        <el-icon><Setting /></el-icon>
        {{ canConfigure ? '配置' : '查看' }}
      </el-button>
      <el-tooltip content="任务创建页将在下一个开发切片开放">
        <span>
          <el-button type="primary" disabled>新建任务</el-button>
        </span>
      </el-tooltip>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { Aim, MoreFilled, Setting } from '@element-plus/icons-vue'
import {
  ElButton,
  ElDropdown,
  ElDropdownItem,
  ElDropdownMenu,
  ElIcon,
  ElTag,
  ElTooltip,
} from 'element-plus'
import { computed } from 'vue'

import type { AgentCard } from '@/features/agent/types'

const props = defineProps<{
  agent: AgentCard
  layout: 'grid' | 'list'
  canManage: boolean
  canConfigure: boolean
}>()

const emit = defineEmits<{
  configure: [agent: AgentCard]
  toggle: [agent: AgentCard]
  delete: [agent: AgentCard]
}>()

const iconBackground = computed(() => {
  const colors = [
    'linear-gradient(135deg, #2563eb, #1746c2)',
    'linear-gradient(135deg, #13a777, #0b8a62)',
    'linear-gradient(135deg, #f4a51c, #ef8b0c)',
    'linear-gradient(135deg, #7655df, #5b3bc4)',
    'linear-gradient(135deg, #15a7ba, #08889b)',
  ]
  const seed = String(props.agent.id)
    .split('')
    .reduce((total, value) => total + value.charCodeAt(0), 0)
  return colors[seed % colors.length]
})

const tokenBudgetLabel = computed(() => {
  if (props.agent.tokenBudget === null) return '跟随空间'
  if (props.agent.tokenBudget >= 1000 && props.agent.tokenBudget % 1000 === 0) {
    return `${props.agent.tokenBudget / 1000}K Token`
  }
  return `${props.agent.tokenBudget.toLocaleString()} Token`
})

function handleCommand(command: string): void {
  if (command === 'edit') emit('configure', props.agent)
  if (command === 'toggle') emit('toggle', props.agent)
  if (command === 'delete') emit('delete', props.agent)
}
</script>

<style scoped>
.agent-card {
  display: flex;
  min-width: 0;
  min-height: 312px;
  flex-direction: column;
  padding: var(--adw-space-4);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  background: var(--adw-surface);
  box-shadow: var(--adw-shadow-card);
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}
.agent-card:hover {
  border-color: #b8c8ee;
  box-shadow: 0 8px 24px rgb(16 24 40 / 8%);
  transform: translateY(-2px);
}
.agent-card__heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: var(--adw-space-3);
}
.agent-card__icon {
  display: inline-flex;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  color: #fff;
  font-size: 25px;
  box-shadow: 0 6px 16px rgb(36 91 219 / 18%);
}
.agent-card__identity {
  min-width: 0;
  flex: 1;
}
.agent-card__identity strong,
.agent-card__identity p {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.agent-card__identity strong {
  display: block;
  color: var(--adw-text-primary);
  font-size: 17px;
}
.agent-card__identity p {
  margin: 6px 0 0;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.agent-card__more {
  display: inline-flex;
  width: 28px;
  height: 28px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 5px;
  color: var(--adw-text-secondary);
  background: transparent;
  cursor: pointer;
}
.agent-card__more:hover {
  background: var(--adw-surface-muted);
}
.agent-card__tags {
  display: flex;
  min-height: 25px;
  flex-wrap: wrap;
  gap: var(--adw-space-2);
  margin: var(--adw-space-4) 0 var(--adw-space-3) 60px;
}
.agent-card__metrics {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  margin: 0 0 var(--adw-space-2);
  padding: var(--adw-space-3) 0;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-surface-muted);
}
.agent-card__metrics > div {
  display: grid;
  gap: 2px;
  text-align: center;
}
.agent-card__metrics > div + div {
  border-left: 1px solid var(--adw-border-color);
}
.agent-card__metrics dd,
.agent-card__metrics dt,
.agent-card__limits dd,
.agent-card__limits dt {
  margin: 0;
}
.agent-card__metrics dd {
  color: var(--adw-text-primary);
  font-size: 17px;
  font-weight: 700;
}
.agent-card__metrics dt {
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.agent-card__limits {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin: 0;
  padding: 9px 12px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
}
.agent-card__limits div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.agent-card__limits div + div {
  padding-left: var(--adw-space-3);
  border-left: 1px solid var(--adw-border-color);
}
.agent-card__limits dt,
.agent-card__limits dd {
  font-size: 12px;
}
.agent-card__limits dt {
  color: var(--adw-text-tertiary);
}
.agent-card__limits dd {
  color: var(--adw-text-primary);
}
.agent-card__actions {
  display: grid;
  grid-template-columns: 1fr 1.25fr;
  gap: var(--adw-space-3);
  margin-top: auto;
  padding-top: var(--adw-space-3);
}
.agent-card__actions :deep(.el-button),
.agent-card__actions > span,
.agent-card__actions > span :deep(.el-button) {
  width: 100%;
  margin: 0;
}
.agent-card--list {
  min-height: 0;
}
@media (min-width: 900px) {
  .agent-card--list {
    display: grid;
    grid-template-columns: minmax(260px, 1.4fr) minmax(210px, 1fr) minmax(250px, 1fr) 250px;
    align-items: center;
    gap: var(--adw-space-5);
  }
  .agent-card--list .agent-card__tags {
    margin: 0;
  }
  .agent-card--list .agent-card__metrics {
    margin: 0;
  }
  .agent-card--list .agent-card__limits {
    display: none;
  }
  .agent-card--list .agent-card__actions {
    margin: 0;
    padding: 0;
  }
}
</style>
