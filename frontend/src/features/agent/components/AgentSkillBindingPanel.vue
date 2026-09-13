<template>
  <section class="binding-panel">
    <header>
      <strong>{{ scope === 'template' ? '固定系统 Skill 版本' : '绑定不可变 Skill 版本' }}</strong>
      <span>{{
        scope === 'template'
          ? '模板发布后将固定引用的系统 Skill 版本。'
          : '工具统计包含这里所有 Skill 声明的工具。'
      }}</span>
    </header>
    <div v-if="canBind && canRead" class="binding-add">
      <el-select
        v-model="selectedId"
        filterable
        :placeholder="scope === 'template' ? '选择系统 Skill' : '选择一个已启用 Skill'"
      >
        <el-option
          v-for="skill in availableSkills"
          :key="String(skill.id)"
          :label="`${skill.displayName}（${skill.name}）`"
          :value="skill.id"
        >
          <span>{{ skill.displayName }}（{{ skill.name }}）</span>
        </el-option>
      </el-select>
      <el-button :disabled="!selectedId" :loading="adding" @click="emit('add', selectedId!)"
        >添加绑定</el-button
      >
    </div>
    <el-empty
      v-if="!rows.length"
      :description="scope === 'template' ? '尚未引用系统 Skill' : '尚未绑定 Skill'"
      :image-size="76"
    />
    <div v-else class="binding-list">
      <article v-for="row in rows" :key="String(row.skillId)" class="binding-row">
        <div>
          <strong>{{ row.skillDisplayName }}</strong
          ><code>{{ row.skillName }}</code>
        </div>
        <el-select
          v-model="row.skillVersionId"
          :loading="row.loadingVersions"
          :disabled="!canBind"
          placeholder="选择已发布版本"
          @visible-change="(visible: boolean) => visible && emit('load-versions', row)"
        >
          <el-option
            v-for="version in row.versions"
            :key="String(version.id)"
            :label="'v' + version.versionNo"
            :value="version.id"
          />
        </el-select>
        <el-button v-if="canBind" type="danger" link @click="emit('remove', row.skillId)"
          >移除</el-button
        >
      </article>
    </div>
    <el-alert
      v-if="canBind && !canRead"
      title="当前账号可以修改绑定，但缺少 skill:read，无法选择新的 Skill。"
      type="warning"
      :closable="false"
    />
  </section>
</template>
<script setup lang="ts">
import { ElAlert, ElButton, ElEmpty, ElOption, ElSelect } from 'element-plus'
import { ref } from 'vue'
import type { EntityId } from '@/features/workspace/types'
export interface SkillBindingVersion {
  id: EntityId
  versionNo: number
  status: number | string
}
export interface SkillBindingRow {
  skillId: EntityId
  skillName: string
  skillDisplayName: string
  skillVersionId: EntityId | null
  versions: SkillBindingVersion[]
  loadingVersions: boolean
}
export interface SkillBindingOption {
  id: EntityId
  name: string
  displayName: string
}
defineProps<{
  scope: 'space' | 'template'
  rows: SkillBindingRow[]
  availableSkills: SkillBindingOption[]
  canBind: boolean
  canRead: boolean
  adding?: boolean
}>()
const emit = defineEmits<{
  add: [skillId: EntityId]
  remove: [skillId: EntityId]
  'load-versions': [row: SkillBindingRow]
}>()
const selectedId = ref<EntityId | null>(null)
</script>
<style scoped>
.binding-panel {
  display: grid;
  gap: var(--adw-space-3);
}
.binding-panel > header {
  display: grid;
  gap: 4px;
}
.binding-panel > header span {
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
.binding-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px auto;
  gap: var(--adw-space-3);
  align-items: center;
  padding: var(--adw-space-3);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
}
.binding-row > div {
  display: grid;
  gap: 4px;
}
.binding-row code,
.binding-row small {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
@media (max-width: 640px) {
  .binding-row {
    grid-template-columns: 1fr;
  }
}
</style>
