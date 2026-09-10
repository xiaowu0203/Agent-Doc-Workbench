<template>
  <article class="skill-card" :class="{ 'skill-card--list': layout === 'list' }">
    <div class="skill-card__heading">
      <span class="skill-card__icon" :class="`skill-card__icon--${tone}`">
        <el-icon><Collection /></el-icon>
      </span>
      <div class="skill-card__identity">
        <strong>{{ skill.displayName }}</strong>
        <code>{{ skill.name }}</code>
      </div>
      <el-tag
        v-if="skill.latestVersion"
        :type="skill.latestVersion.status === 'PUBLISHED' ? 'success' : 'warning'"
        effect="plain"
        size="small"
      >
        v{{ skill.latestVersion.versionNo }}
        {{ skill.latestVersion.status === 'PUBLISHED' ? '已发布' : '草稿' }}
      </el-tag>
      <el-tag v-else type="info" effect="plain" size="small">暂无版本</el-tag>
      <el-dropdown v-if="canManage" trigger="click" @command="handleCommand">
        <button class="skill-card__more" type="button" aria-label="Skill 操作">
          <el-icon><MoreFilled /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="edit">编辑元数据</el-dropdown-item>
            <el-dropdown-item command="upload" :disabled="skill.status === 'DISABLED'">
              上传新版本
            </el-dropdown-item>
            <el-dropdown-item command="toggle" divided>
              {{ skill.status === 'ACTIVE' ? '停用 Skill' : '启用 Skill' }}
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <p class="skill-card__description">{{ skill.description }}</p>

    <div class="skill-card__metrics">
      <span
        ><el-icon><Clock /></el-icon>{{ skill.versionCount }} 版本</span
      >
      <span
        ><el-icon><User /></el-icon>{{ skill.boundAgentCount }} Agent</span
      >
      <span>
        <el-icon><Operation /></el-icon>{{ skill.latestVersion?.allowedToolCount ?? 0 }} 工具
      </span>
      <span v-if="skill.status === 'DISABLED'" class="skill-card__disabled">已停用</span>
    </div>

    <div class="skill-card__latest">
      <template v-if="skill.latestVersion">
        <strong>最新版本&nbsp; v{{ skill.latestVersion.versionNo }}</strong>
        <span>{{ skill.latestVersion.activationDescription }}</span>
        <small>
          {{ skill.latestVersion.status === 'PUBLISHED' ? '发布时间' : '上传时间' }}：
          {{ formatDate(skill.latestVersion.publishedAt || skill.latestVersion.createdAt) }}
        </small>
      </template>
      <template v-else>
        <strong>还没有可用版本</strong>
        <span>上传 ZIP 或在线创建首个草稿版本</span>
      </template>
    </div>

    <footer class="skill-card__actions">
      <el-button :icon="Clock" @click="$emit('versions', skill)">版本记录</el-button>
      <el-button type="primary" @click="$emit('detail', skill)">查看详情</el-button>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { Clock, Collection, MoreFilled, Operation, User } from '@element-plus/icons-vue'
import { ElButton, ElDropdown, ElDropdownItem, ElDropdownMenu, ElIcon, ElTag } from 'element-plus'
import { computed } from 'vue'

import type { Skill } from '@/features/skill/types'

const props = defineProps<{
  skill: Skill
  layout: 'grid' | 'list'
  canManage: boolean
}>()

const emit = defineEmits<{
  detail: [skill: Skill]
  versions: [skill: Skill]
  edit: [skill: Skill]
  upload: [skill: Skill]
  toggle: [skill: Skill]
}>()

const tones = ['primary', 'violet', 'orange', 'teal'] as const
const tone = computed(() => tones[Number(String(props.skill.id).slice(-1)) % tones.length])

function handleCommand(command: string): void {
  if (command === 'edit') emit('edit', props.skill)
  if (command === 'upload') emit('upload', props.skill)
  if (command === 'toggle') emit('toggle', props.skill)
}

function formatDate(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}
</script>

<style scoped>
.skill-card {
  display: flex;
  min-width: 0;
  min-height: 330px;
  flex-direction: column;
  padding: var(--adw-space-5);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  background: var(--adw-surface);
  box-shadow: var(--adw-shadow-card);
  transition:
    border-color 160ms ease,
    transform 160ms ease,
    box-shadow 160ms ease;
}

.skill-card:hover {
  border-color: #b8c8ee;
  box-shadow: 0 8px 24px rgb(16 24 40 / 8%);
  transform: translateY(-2px);
}

.skill-card__heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: var(--adw-space-3);
}

.skill-card__icon {
  display: inline-flex;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 9px;
  color: #fff;
  font-size: 21px;
}

.skill-card__icon--primary {
  background: #245bdb;
}

.skill-card__icon--violet {
  background: #6f4bd8;
}

.skill-card__icon--orange {
  background: #ee8b17;
}

.skill-card__icon--teal {
  background: #16a1a5;
}

.skill-card__identity {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 4px;
}

.skill-card__identity strong {
  overflow: hidden;
  color: var(--adw-text-primary);
  font-size: 17px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-card__identity code {
  overflow: hidden;
  color: var(--adw-text-secondary);
  font-family: inherit;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.skill-card__more {
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

.skill-card__more:hover {
  background: var(--adw-surface-muted);
}

.skill-card__description {
  display: -webkit-box;
  min-height: 44px;
  margin: var(--adw-space-5) 0 var(--adw-space-4);
  overflow: hidden;
  color: var(--adw-text-secondary);
  line-height: 1.6;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.skill-card__metrics {
  display: flex;
  flex-wrap: wrap;
  gap: var(--adw-space-4);
  padding-bottom: var(--adw-space-4);
  border-bottom: 1px solid var(--adw-border-color-light);
  color: var(--adw-text-secondary);
  font-size: 12px;
}

.skill-card__metrics span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.skill-card__metrics .skill-card__disabled {
  margin-left: auto;
  color: var(--adw-color-danger);
}

.skill-card__latest {
  display: grid;
  min-height: 92px;
  gap: 5px;
  padding: var(--adw-space-4) 0;
}

.skill-card__latest strong {
  color: var(--adw-text-primary);
  font-size: 13px;
}

.skill-card__latest span {
  display: -webkit-box;
  overflow: hidden;
  color: var(--adw-text-secondary);
  font-size: 13px;
  line-height: 1.45;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.skill-card__latest small {
  color: var(--adw-text-tertiary);
}

.skill-card__actions {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--adw-space-3);
  margin-top: auto;
}

.skill-card__actions :deep(.el-button) {
  width: 100%;
  margin: 0;
}

.skill-card--list {
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(230px, 1.1fr) minmax(220px, 1.35fr) minmax(210px, 1fr) minmax(250px, 1.15fr);
  grid-template-rows: minmax(76px, auto) auto;
  column-gap: clamp(20px, 2.4vw, 36px);
  row-gap: 0;
  padding: 20px 24px 0;
}

.skill-card--list .skill-card__heading {
  grid-column: 1;
  grid-row: 1;
  align-self: center;
}

.skill-card--list .skill-card__description {
  grid-column: 2;
  grid-row: 1;
  align-self: center;
  min-height: 0;
  max-height: 48px;
  margin: 0;
  line-height: 1.55;
}

.skill-card--list .skill-card__metrics {
  display: grid;
  grid-column: 3;
  grid-row: 1;
  grid-template-columns: repeat(3, max-content);
  align-items: center;
  align-self: center;
  gap: 12px;
  padding: 0;
  border: 0;
}

.skill-card--list .skill-card__metrics .skill-card__disabled {
  grid-column: 1 / -1;
  margin: 2px 0 0;
}

.skill-card--list .skill-card__latest {
  display: grid;
  grid-column: 4;
  grid-row: 1;
  align-self: stretch;
  min-height: 0;
  gap: 4px;
  margin: 0;
  padding: 12px 16px;
  border: 1px solid var(--adw-border-color-light);
  border-radius: var(--adw-radius-md);
  background: var(--adw-surface-muted);
}

.skill-card--list .skill-card__latest span {
  -webkit-line-clamp: 1;
}

.skill-card--list .skill-card__actions {
  display: flex;
  grid-column: 1 / -1;
  grid-row: 2;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin: 16px 0 0;
  padding: 14px 0 20px;
  border-top: 1px solid var(--adw-border-color-light);
}

.skill-card--list .skill-card__actions :deep(.el-button) {
  width: auto;
  min-width: 118px;
}

@media (max-width: 1240px) {
  .skill-card--list {
    grid-template-columns: minmax(210px, 1fr) minmax(220px, 1.2fr) minmax(230px, 1fr);
  }

  .skill-card--list .skill-card__latest {
    grid-column: 3;
  }
}

@media (max-width: 980px) {
  .skill-card--list {
    grid-template-columns: minmax(220px, 1fr) minmax(220px, 1fr);
    grid-template-rows: auto auto auto;
    row-gap: 14px;
  }

  .skill-card--list .skill-card__heading {
    grid-column: 1;
    grid-row: 1;
  }

  .skill-card--list .skill-card__description {
    grid-column: 2;
    grid-row: 1;
  }

  .skill-card--list .skill-card__metrics {
    grid-column: 1;
    grid-row: 2;
    justify-self: start;
  }

  .skill-card--list .skill-card__latest {
    grid-column: 2;
    grid-row: 2;
  }

  .skill-card--list .skill-card__actions {
    grid-row: 3;
  }
}

@media (max-width: 620px) {
  .skill-card--list {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 14px;
    padding: 18px;
  }

  .skill-card--list .skill-card__description,
  .skill-card--list .skill-card__metrics,
  .skill-card--list .skill-card__latest,
  .skill-card--list .skill-card__actions {
    width: 100%;
  }

  .skill-card--list .skill-card__actions {
    justify-content: stretch;
    margin-top: 0;
    padding: 14px 0 0;
  }

  .skill-card--list .skill-card__actions :deep(.el-button) {
    flex: 1;
  }
}

/* Keep the list view intentionally denser than the showcase card view. */
.skill-card--list .skill-card__more {
  margin-left: 2px;
}

</style>
