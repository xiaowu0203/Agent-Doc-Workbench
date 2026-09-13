<template>
  <section class="binding-panel">
    <header class="binding-heading">
      <div>
        <strong>{{ scope === 'template' ? '固定系统 MCP 模板版本' : '外部 MCP' }}</strong
        ><span>{{
          scope === 'template'
            ? '模板发布后将固定引用的 MCP 模板版本。'
            : '总开关关闭时保留绑定配置，但执行时不会连接外部服务。'
        }}</span>
      </div>
      <el-switch v-if="scope === 'space'" v-model="externalMcpEnabled" :disabled="!canManage" />
    </header>
    <div v-if="canBind && canRead" class="binding-add">
      <el-select
        v-model="selectedId"
        filterable
        :placeholder="scope === 'template' ? '选择系统 MCP 模板' : '选择一个已启用 MCP 服务'"
      >
        <el-option
          v-for="item in availableItems"
          :key="String(item.id)"
          :label="`${item.displayName}（${item.name}）`"
          :value="item.id"
        >
          <span>{{ item.displayName }}（{{ item.name }}）</span>
        </el-option>
      </el-select>
      <el-button :disabled="!selectedId" @click="emit('add', selectedId!)">添加绑定</el-button>
    </div>
    <el-empty
      v-if="!rows.length"
      :description="scope === 'template' ? '尚未引用系统 MCP 模板' : '尚未绑定外部 MCP'"
      :image-size="76"
    />
    <div v-else class="binding-list">
      <article
        v-for="row in rows"
        :key="String(scope === 'template' ? row.mcpTemplateId : row.mcpServerId)"
        class="mcp-row"
      >
        <header>
          <div>
            <strong>{{ row.displayName }}</strong
            ><code>{{ row.serverKey }}</code>
          </div>
          <el-button
            v-if="canBind"
            type="danger"
            link
            @click="emit('remove', (scope === 'template' ? row.mcpTemplateId : row.mcpServerId)!)"
            >移除</el-button
          >
        </header>
        <el-select
          v-if="scope === 'template'"
          v-model="row.mcpTemplateVersionId"
          :loading="row.loadingVersions"
          placeholder="选择已发布版本"
          @visible-change="(visible: boolean) => visible && emit('load-versions', row)"
        >
          <el-option
            v-for="version in row.versions || []"
            :key="String(version.id)"
            :label="'v' + version.versionNo"
            :value="version.id"
          />
        </el-select>
        <el-input
          v-if="scope === 'template'"
          v-model="row.toolWhitelistText"
          type="textarea"
          :rows="2"
          placeholder="工具白名单，每行一个；留空表示不额外限制"
        />
        <template v-else>
          <div class="mcp-policy">
            <span>工具白名单</span
            ><el-radio-group
              v-model="row.mode"
              size="small"
              :disabled="!canBind"
              @change="emit('mode-change', row)"
              ><el-radio-button value="ALL">全部发现工具</el-radio-button
              ><el-radio-button value="CUSTOM">指定工具</el-radio-button
              ><el-radio-button value="NONE">禁用全部</el-radio-button></el-radio-group
            >
          </div>
          <el-select
            v-if="row.mode === 'CUSTOM'"
            v-model="row.toolWhitelist"
            class="full-width"
            multiple
            filterable
            :loading="row.loadingTools"
            :disabled="!canBind"
            placeholder="选择该服务允许调用的工具"
            @visible-change="(visible: boolean) => visible && emit('load-tools', row)"
          >
            <el-option
              v-for="tool in row.tools || []"
              :key="tool.name"
              :label="tool.name"
              :value="tool.name"
              ><span>{{ tool.name }}</span
              ><small>{{ tool.description || '无描述' }}</small></el-option
            >
          </el-select>
        </template>
      </article>
    </div>
    <el-alert
      v-if="canBind && !canRead"
      :title="
        scope === 'template'
          ? '当前账号无法读取系统能力。'
          : '当前账号可以修改绑定，但缺少 mcp:read，无法选择新的 MCP 服务或工具。'
      "
      type="warning"
      :closable="false"
    />
  </section>
</template>
<script setup lang="ts">
import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElInput,
  ElOption,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElSwitch,
} from 'element-plus'
import { ref } from 'vue'
import type { McpTool } from '@/features/mcp/types'
import type { EntityId } from '@/features/workspace/types'
import type { CapabilityVersion } from '@/features/system-capability/types'
export type McpBindingMode = 'ALL' | 'CUSTOM' | 'NONE'
export interface McpBindingRow {
  mcpServerId?: EntityId
  mcpTemplateId?: EntityId
  mcpTemplateVersionId?: EntityId | null
  serverKey: string
  displayName: string
  mode?: McpBindingMode
  toolWhitelist?: string[]
  toolWhitelistText?: string
  tools?: McpTool[]
  versions?: CapabilityVersion[]
  loadingTools?: boolean
  loadingVersions?: boolean
}
export interface McpBindingOption {
  id: EntityId
  name: string
  displayName: string
}
defineProps<{
  scope: 'space' | 'template'
  rows: McpBindingRow[]
  availableItems: McpBindingOption[]
  canManage: boolean
  canBind: boolean
  canRead: boolean
}>()
const emit = defineEmits<{
  add: [id: EntityId]
  remove: [id: EntityId]
  'load-versions': [row: McpBindingRow]
  'load-tools': [row: McpBindingRow]
  'mode-change': [row: McpBindingRow]
}>()
const selectedId = ref<EntityId | null>(null)
const externalMcpEnabled = defineModel<boolean>('externalMcpEnabled', { default: false })
</script>
<style scoped>
.binding-panel {
  display: grid;
  gap: var(--adw-space-3);
}
.binding-heading {
  display: flex;
  justify-content: space-between;
  gap: var(--adw-space-3);
}
.binding-heading > div {
  display: grid;
  gap: 4px;
}
.binding-heading span {
  color: var(--adw-text-secondary);
  font-size: 13px;
}
.binding-add {
  display: flex;
  gap: var(--adw-space-2);
}
.binding-add .el-select {
  flex: 1;
}
.binding-list {
  display: grid;
  gap: var(--adw-space-2);
}
.mcp-row {
  display: grid;
  gap: var(--adw-space-2);
  padding: var(--adw-space-3);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
}
.mcp-row > header {
  display: flex;
  justify-content: space-between;
}
.mcp-row > header div {
  display: grid;
  gap: 4px;
}
.mcp-row code,
.mcp-row small {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.mcp-policy {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--adw-space-2);
}
.full-width {
  width: 100%;
}
@media (max-width: 640px) {
  .mcp-policy {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
