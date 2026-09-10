<template>
  <article class="model-card" :class="{ 'model-card--list': layout === 'list' }">
    <header class="model-card__heading">
      <span class="model-card__icon"
        ><el-icon><Cpu /></el-icon
      ></span>
      <div class="model-card__identity">
        <strong>{{ model.displayName }}</strong>
        <code>{{ model.modelKey }}</code>
      </div>
      <el-tag :type="model.status === 'ENABLED' ? 'success' : 'info'" effect="plain" size="small">
        {{ model.status === 'ENABLED' ? '已启用' : '已停用' }}
      </el-tag>
      <el-dropdown trigger="click" @command="handleCommand">
        <button class="model-card__more" type="button" aria-label="模型配置操作">
          <el-icon><MoreFilled /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="edit">编辑配置</el-dropdown-item>
            <el-dropdown-item command="toggle">
              {{ model.status === 'ENABLED' ? '停用模型' : '启用模型' }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <p class="model-card__description">{{ model.description || '暂无模型描述' }}</p>

    <dl class="model-card__config">
      <div>
        <dt>供应商</dt>
        <dd>{{ getProviderLabel(model.provider) }}</dd>
      </div>
      <div>
        <dt>适配器</dt>
        <dd :title="model.adapterType">{{ getAdapterLabel(model.adapterType) }}</dd>
      </div>
      <div>
        <dt>API Key</dt>
        <dd>{{ model.apiKeyConfigured ? '已配置' : '未配置' }}</dd>
      </div>
      <div>
        <dt>配置版本</dt>
        <dd>v{{ model.configVersion }}</dd>
      </div>
    </dl>

    <div class="model-card__metrics">
      <div>
        <span>上下文窗口</span><strong>{{ formatTokenLimit(model.contextWindow) }}</strong>
      </div>
      <div>
        <span>最大输出</span><strong>{{ formatTokenLimit(model.maxOutputTokens) }}</strong>
      </div>
      <div>
        <span>关联 Agent</span><strong>{{ model.agentCount }} 个</strong>
      </div>
    </div>

    <div class="model-card__pricing">
      <span>输入 {{ formatPrice(model.inputPricePerMillion) }}</span>
      <span>输出 {{ formatPrice(model.outputPricePerMillion) }}</span>
    </div>

    <footer class="model-card__actions">
      <el-button
        :loading="testing"
        :disabled="!model.apiKeyConfigured"
        @click="$emit('test', model)"
      >
        测试连接
      </el-button>
      <el-button type="primary" @click="$emit('edit', model)">配置</el-button>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { Cpu, MoreFilled } from '@element-plus/icons-vue'
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon, ElTag } from 'element-plus'

import { getAdapterLabel, getProviderLabel } from '@/features/model/model-options'
import type { ModelConfig } from '@/features/model/types'

const props = defineProps<{
  model: ModelConfig
  layout: 'grid' | 'list'
  testing: boolean
}>()

const emit = defineEmits<{
  edit: [model: ModelConfig]
  test: [model: ModelConfig]
  toggle: [model: ModelConfig]
}>()

function handleCommand(command: string): void {
  if (command === 'edit') emit('edit', props.model)
  if (command === 'toggle') emit('toggle', props.model)
}

function formatTokenLimit(value: number | null): string {
  if (value === null) return '—'
  if (value >= 1_000_000) return `${Number((value / 1_000_000).toFixed(1))}M`
  if (value >= 1_000) return `${Number((value / 1_000).toFixed(1))}K`
  return String(value)
}

function formatPrice(value: number | null): string {
  return value === null ? '未配置' : `¥${value} / 百万 Token`
}
</script>

<style scoped>
.model-card {
  display: flex;
  min-width: 0;
  min-height: 330px;
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

.model-card:hover {
  border-color: #b8c8ee;
  box-shadow: 0 8px 24px rgb(16 24 40 / 8%);
  transform: translateY(-2px);
}

.model-card__heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: var(--adw-space-3);
}
.model-card__icon {
  display: inline-flex;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: #635bdb;
  font-size: 21px;
}
.model-card__identity {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}
.model-card__identity strong {
  overflow: hidden;
  color: var(--adw-text-primary);
  font-size: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-card__identity code {
  overflow: hidden;
  color: var(--adw-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-card__more {
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
.model-card__more:hover {
  background: var(--adw-surface-muted);
}
.model-card__description {
  min-height: 40px;
  margin: var(--adw-space-4) 0;
  overflow: hidden;
  color: var(--adw-text-secondary);
  font-size: 13px;
  line-height: 20px;
}
.model-card__config {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 12px;
  margin: 0;
  padding-bottom: var(--adw-space-4);
  border-bottom: 1px solid var(--adw-border-color-light);
}
.model-card__config div {
  display: grid;
  min-width: 0;
  gap: 3px;
}
.model-card__config dt,
.model-card__metrics span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.model-card__config dd {
  margin: 0;
  overflow: hidden;
  color: var(--adw-text-primary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-card__metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-top: var(--adw-space-4);
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
}
.model-card__metrics div {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
}
.model-card__metrics div + div {
  border-left: 1px solid var(--adw-border-color-light);
}
.model-card__metrics strong {
  color: var(--adw-text-primary);
  font-size: 14px;
}
.model-card__pricing {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 2px 0;
  color: var(--adw-text-secondary);
  font-size: 11px;
}
.model-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--adw-space-3);
  margin-top: auto;
  padding-top: var(--adw-space-4);
}
.model-card__actions :deep(.el-button) {
  width: 100%;
  margin: 0;
}
.model-card--list {
  min-height: 0;
}

@media (min-width: 900px) {
  .model-card--list {
    display: grid;
    grid-template-columns: minmax(260px, 1.1fr) minmax(300px, 1.3fr) minmax(250px, 1fr) 230px;
    align-items: center;
    gap: 24px;
  }
  .model-card--list .model-card__description {
    display: none;
  }
  .model-card--list .model-card__config {
    padding: 0;
    border: 0;
  }
  .model-card--list .model-card__metrics {
    margin: 0;
  }
  .model-card--list .model-card__pricing {
    display: none;
  }
  .model-card--list .model-card__actions {
    margin: 0;
    padding: 0;
  }
}
</style>
