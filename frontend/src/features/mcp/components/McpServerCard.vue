<template>
  <article class="mcp-card" :class="{ 'mcp-card--list': layout === 'list' }">
    <header class="mcp-card__heading">
      <span class="mcp-card__icon"
        ><el-icon><Connection /></el-icon
      ></span>
      <div class="mcp-card__identity">
        <strong>{{ server.displayName }}</strong>
        <code>{{ server.serverKey }}</code>
      </div>
      <el-tag :type="connectionTag.type" effect="plain" size="small">
        {{ connectionTag.label }}
      </el-tag>
      <el-dropdown v-if="canManage" trigger="click" @command="handleCommand">
        <button class="mcp-card__more" type="button" aria-label="MCP 服务操作">
          <el-icon><MoreFilled /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="edit">编辑配置</el-dropdown-item>
            <el-dropdown-item command="toggle">
              {{ server.status === 1 ? '停用服务' : '启用服务' }}
            </el-dropdown-item>
            <el-dropdown-item command="delete" divided>删除服务</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </header>

    <p class="mcp-card__endpoint" :title="server.endpointUrl">{{ server.endpointUrl }}</p>

    <dl class="mcp-card__config">
      <div>
        <dt>认证方式</dt>
        <dd>{{ server.authType }}</dd>
      </div>
      <div>
        <dt>凭证</dt>
        <dd>{{ credentialLabel }}</dd>
      </div>
      <div>
        <dt>配置版本</dt>
        <dd>v{{ server.configVersion }}</dd>
      </div>
      <div>
        <dt>启停状态</dt>
        <dd :class="{ 'mcp-card__disabled': server.status === 0 }">
          {{ server.status === 1 ? '已启用' : '已停用' }}
        </dd>
      </div>
    </dl>

    <div class="mcp-card__metrics">
      <span
        ><el-icon><Operation /></el-icon>{{ server.discoveredToolCount }} 工具</span
      >
      <span
        ><el-icon><Timer /></el-icon>{{ durationLabel }}</span
      >
    </div>

    <div class="mcp-card__test-result">
      <span>最近连接测试：{{ formatDate(server.lastTestedAt) }}</span>
      <p v-if="server.connectionStatus === 'FAILED'" :title="server.lastTestError || ''">
        {{ server.lastTestError || '连接失败' }}
      </p>
      <p v-else-if="server.connectionStatus === 'UNTESTED'">尚未验证当前配置</p>
      <p v-else>连接与工具发现均已完成</p>
    </div>

    <footer class="mcp-card__actions">
      <el-button
        v-if="canManage"
        :loading="testing"
        :disabled="server.status === 0"
        @click="$emit('test', server)"
      >
        测试连接
      </el-button>
      <el-button type="primary" @click="$emit('detail', server)">
        {{ canManage ? '配置' : '查看' }}
      </el-button>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { Connection, MoreFilled, Operation, Timer } from '@element-plus/icons-vue'
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon, ElTag } from 'element-plus'
import { computed } from 'vue'

import type { McpServer } from '@/features/mcp/types'

const props = defineProps<{
  server: McpServer
  layout: 'grid' | 'list'
  canManage: boolean
  testing: boolean
}>()

const emit = defineEmits<{
  detail: [server: McpServer]
  edit: [server: McpServer]
  test: [server: McpServer]
  toggle: [server: McpServer]
  delete: [server: McpServer]
}>()

const connectionTag = computed(() => {
  if (props.server.status === 0) return { type: 'info' as const, label: '已停用' }
  if (props.server.connectionStatus === 'SUCCESS') {
    return { type: 'success' as const, label: '连接正常' }
  }
  if (props.server.connectionStatus === 'FAILED') {
    return { type: 'danger' as const, label: '连接失败' }
  }
  return { type: 'warning' as const, label: '待测试' }
})

const credentialLabel = computed(() => {
  if (props.server.authType === 'NONE') return '无需认证'
  if (!props.server.authConfigured) return '未配置'
  return props.server.authType === 'QUERY_PARAM'
    ? `${props.server.authParamName || 'key'} 已配置`
    : '凭证已配置'
})

const durationLabel = computed(() =>
  props.server.lastTestDurationMs === null ? '—' : `${props.server.lastTestDurationMs} ms`,
)

function handleCommand(command: string): void {
  if (command === 'edit') emit('edit', props.server)
  if (command === 'toggle') emit('toggle', props.server)
  if (command === 'delete') emit('delete', props.server)
}

function formatDate(value: string | null): string {
  if (!value) return '尚未测试'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false,
  }).format(new Date(value))
}
</script>

<style scoped>
.mcp-card {
  display: flex;
  min-width: 0;
  min-height: 360px;
  flex-direction: column;
  padding: var(--adw-space-5);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  background: var(--adw-surface);
  box-shadow: var(--adw-shadow-card);
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}
.mcp-card:hover {
  border-color: #b8c8ee;
  box-shadow: 0 8px 24px rgb(16 24 40 / 8%);
  transform: translateY(-2px);
}
.mcp-card__heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: var(--adw-space-3);
}
.mcp-card__icon {
  display: inline-flex;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  color: #fff;
  background: #16a1b8;
  font-size: 21px;
}
.mcp-card__identity {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}
.mcp-card__identity strong,
.mcp-card__identity code,
.mcp-card__endpoint {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mcp-card__identity strong {
  color: var(--adw-text-primary);
  font-size: 17px;
}
.mcp-card__identity code {
  color: var(--adw-text-secondary);
  font-family: inherit;
  font-size: 12px;
}
.mcp-card__more {
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
.mcp-card__more:hover {
  background: var(--adw-surface-muted);
}
.mcp-card__endpoint {
  margin: var(--adw-space-5) 0 var(--adw-space-4);
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.mcp-card__config {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px 20px;
  margin: 0;
  padding-bottom: var(--adw-space-4);
  border-bottom: 1px solid var(--adw-border-color-light);
}
.mcp-card__config div {
  display: flex;
  gap: 8px;
  min-width: 0;
}
.mcp-card__config dt {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.mcp-card__config dd {
  margin: 0;
  color: var(--adw-text-primary);
  font-size: 12px;
}
.mcp-card__config .mcp-card__disabled {
  color: var(--adw-color-danger);
}
.mcp-card__metrics {
  display: flex;
  gap: var(--adw-space-6);
  padding: var(--adw-space-4) 0;
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.mcp-card__metrics span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.mcp-card__test-result {
  min-height: 56px;
  padding: 12px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-sm);
  background: var(--adw-surface-muted);
}
.mcp-card__test-result span {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.mcp-card__test-result p {
  margin: 6px 0 0;
  overflow: hidden;
  color: var(--adw-text-secondary);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mcp-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--adw-space-3);
  margin-top: auto;
  padding-top: var(--adw-space-4);
}
.mcp-card__actions :deep(.el-button) {
  width: 100%;
  margin: 0;
}
.mcp-card--list {
  min-height: 0;
}
@media (min-width: 900px) {
  .mcp-card--list {
    display: grid;
    grid-template-columns: minmax(240px, 1.1fr) minmax(260px, 1.4fr) minmax(250px, 1fr) 240px;
    align-items: center;
    gap: 24px;
  }
  .mcp-card--list .mcp-card__endpoint {
    margin: 0;
  }
  .mcp-card--list .mcp-card__config {
    padding: 0;
    border: 0;
  }
  .mcp-card--list .mcp-card__metrics {
    display: none;
  }
  .mcp-card--list .mcp-card__test-result {
    min-height: 48px;
  }
  .mcp-card--list .mcp-card__actions {
    margin: 0;
    padding: 0;
  }
}
</style>
