<template>
  <div class="skill-package-builder">
    <el-form-item label="版本激活描述" required>
      <el-input
        v-model="activationDescription"
        maxlength="500"
        show-word-limit
        placeholder="说明这个 Skill 适合在什么场景下使用"
      />
    </el-form-item>

    <el-form-item label="Skill 指令" required>
      <el-input
        v-model="instructions"
        type="textarea"
        :rows="8"
        placeholder="输入模型执行该 Skill 时需要遵循的完整指令"
      />
    </el-form-item>

    <el-form-item label="允许工具（每行一个，可选）">
      <el-input
        v-model="allowedToolsText"
        type="textarea"
        :rows="3"
        placeholder="server-key__tool-name"
      />
    </el-form-item>

    <el-form-item label="Skill 目录文件">
      <div class="skill-builder">
        <aside class="skill-builder__tree">
          <div class="skill-builder__tree-header">
            <strong>目录结构</strong>
            <el-button link type="primary" :icon="Plus" @click="addFile()">添加文件</el-button>
          </div>
          <div class="skill-builder__root">
            <el-icon><FolderOpened /></el-icon>
            <code>{{ skillName || 'skill-name' }}/</code>
          </div>
          <button
            type="button"
            class="skill-builder__required-file"
            :class="{ active: skillMarkdownSelected }"
            @click="selectSkillMarkdown"
          >
            <el-icon><Document /></el-icon>
            <span>SKILL.md</span>
            <small>只读</small>
          </button>
          <div v-for="directory in directories" :key="directory.name">
            <button
              type="button"
              class="skill-builder__directory"
              :class="{ active: !skillMarkdownSelected && selectedDirectory === directory.name }"
              @click="selectDirectory(directory.name)"
            >
              <el-icon><FolderOpened /></el-icon>
              <span>{{ directory.name }}</span>
              <el-icon class="skill-builder__directory-add" @click.stop="addFile(directory.name)">
                <Plus />
              </el-icon>
            </button>
            <button
              v-for="file in filesFor(directory.name)"
              :key="file.id"
              type="button"
              class="skill-builder__file"
              :class="{ active: selectedFileId === file.id }"
              @click="selectFile(file)"
            >
              <el-icon><Document /></el-icon>
              <span>{{ relativePath(file.path) }}</span>
              <el-icon class="skill-builder__file-delete" @click.stop="removeFile(file.id)">
                <Delete />
              </el-icon>
            </button>
          </div>
          <p class="skill-builder__hint">
            文件可放在 scripts、references、assets、examples 下；scripts 支持 py、sh、js。
          </p>
        </aside>

        <section v-if="skillMarkdownSelected" class="skill-builder__editor">
          <div class="skill-builder__editor-header">
            <div>
              <span>自动生成文件</span>
              <code>{{ skillName || 'skill-name' }}/SKILL.md</code>
            </div>
            <el-tag type="info" effect="plain" size="small">只读</el-tag>
          </div>
          <el-input
            class="skill-builder__content skill-builder__content--readonly"
            :model-value="skillMarkdownPreview"
            type="textarea"
            :rows="18"
            readonly
            aria-label="SKILL.md 只读内容"
          />
        </section>
        <section v-else-if="selectedFile" class="skill-builder__editor">
          <div class="skill-builder__editor-header">
            <div>
              <span>文件路径</span>
              <code>{{ skillName || 'skill-name' }}/{{ selectedFileDirectory }}/</code>
            </div>
            <el-button link type="danger" @click="removeFile(selectedFile.id)">删除文件</el-button>
          </div>
          <el-input
            :model-value="relativePath(selectedFile.path)"
            aria-label="Skill 文件路径"
            placeholder="输入文件名或相对路径，例如 check.py"
            @update:model-value="updateSelectedFilePath"
          />
          <el-input
            class="skill-builder__content"
            :model-value="selectedFile.content"
            type="textarea"
            :rows="12"
            aria-label="Skill 文件内容"
            placeholder="在这里编写文件内容"
            @update:model-value="updateSelectedFileContent"
          />
        </section>
        <div v-else class="skill-builder__empty">
          <el-icon><FolderOpened /></el-icon>
          <strong>已选择 {{ selectedDirectory }}/</strong>
          <span>点击“添加文件”或文件夹右侧的“+”创建文件</span>
        </div>
      </div>
    </el-form-item>

    <p class="skill-builder__footer-hint">
      提交时会生成 {{ skillName || 'skill-name' }}/ 目录，并将全部内容统一打包为 ZIP。
    </p>
  </div>
</template>

<script setup lang="ts">
import { Delete, Document, FolderOpened, Plus } from '@element-plus/icons-vue'
import { ElButton, ElFormItem, ElIcon, ElInput, ElMessageBox, ElTag } from 'element-plus'
import { computed, ref } from 'vue'

import { buildSkillMarkdown, buildSkillPackage } from '@/features/skill/skill-package'
import type { SkillPackageFile } from '@/features/skill/types'

const props = defineProps<{
  skillName: string
}>()

interface DraftPackageFile extends SkillPackageFile {
  id: string
}

type SkillDirectory = 'scripts' | 'references' | 'assets' | 'examples'

const directories: ReadonlyArray<{ name: SkillDirectory; placeholder: string }> = [
  { name: 'scripts', placeholder: '例如：check.py、processor.js 或 run.sh' },
  { name: 'references', placeholder: '例如：rules.md、config.yaml 或 data.csv' },
  { name: 'assets', placeholder: '例如：template.html、schema.json 或 icon.svg' },
  { name: 'examples', placeholder: '例如：input.json、usage.md 或 sample.txt' },
]

const activationDescription = ref('')
const instructions = ref('')
const allowedToolsText = ref('')
const files = ref<DraftPackageFile[]>([])
const selectedFileId = ref<string | null>(null)
const selectedDirectory = ref<SkillDirectory>('scripts')
const skillMarkdownSelected = ref(true)
let fileSequence = 0
const selectedFile = computed(
  () => files.value.find((file) => file.id === selectedFileId.value) ?? null,
)
const selectedFileDirectory = computed<SkillDirectory>(() =>
  selectedFile.value ? directoryOf(selectedFile.value.path) : selectedDirectory.value,
)
const skillMarkdownPreview = computed(() =>
  buildSkillMarkdown({
    name: props.skillName.trim() || 'skill-name',
    activationDescription: activationDescription.value.trim() || '请填写版本激活描述',
    instructions: instructions.value.trim() || '请填写 Skill 指令',
    allowedTools: allowedToolsText.value
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean),
  }),
)

async function addFile(directory: SkillDirectory = selectedDirectory.value): Promise<void> {
  selectedDirectory.value = directory
  skillMarkdownSelected.value = false
  const config = directories.find((item) => item.name === directory)
  try {
    const result = await ElMessageBox.prompt(
      `文件将创建在 ${directory}/ 目录下，可输入多级相对路径。`,
      `在 ${directory} 中新建文件`,
      {
        confirmButtonText: '创建',
        cancelButtonText: '取消',
        inputPlaceholder: config?.placeholder,
        inputValidator: (value) => validateNewFileName(directory, value),
      },
    )
    const relative = normalizeRelativePath(String(result.value))
    const file: DraftPackageFile = {
      id: `file-${++fileSequence}`,
      path: `${directory}/${relative}`,
      content: '',
    }
    files.value.push(file)
    selectedFileId.value = file.id
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    throw error
  }
}

function removeFile(id: string): void {
  const index = files.value.findIndex((file) => file.id === id)
  if (index < 0) return
  files.value.splice(index, 1)
  if (selectedFileId.value === id) {
    selectedFileId.value = files.value[Math.max(0, index - 1)]?.id ?? null
  }
}

function selectFile(file: DraftPackageFile): void {
  selectedFileId.value = file.id
  selectedDirectory.value = directoryOf(file.path)
  skillMarkdownSelected.value = false
}

function selectDirectory(directory: SkillDirectory): void {
  selectedDirectory.value = directory
  selectedFileId.value = null
  skillMarkdownSelected.value = false
}

function selectSkillMarkdown(): void {
  selectedFileId.value = null
  skillMarkdownSelected.value = true
}

function filesFor(directory: SkillDirectory): DraftPackageFile[] {
  return files.value.filter((file) => directoryOf(file.path) === directory)
}

function directoryOf(path: string): SkillDirectory {
  return path.slice(0, path.indexOf('/')) as SkillDirectory
}

function relativePath(path: string): string {
  return path.slice(path.indexOf('/') + 1)
}

function normalizeRelativePath(value: string): string {
  return value.trim().replaceAll('\\', '/')
}

function validateNewFileName(directory: SkillDirectory, value: string): true | string {
  const relative = normalizeRelativePath(value)
  const segments = relative.split('/')
  if (!relative || segments.some((segment) => !segment || segment === '.' || segment === '..')) {
    return '请输入合法的文件名或相对路径'
  }
  const path = `${directory}/${relative}`
  if (files.value.some((file) => file.path.toLowerCase() === path.toLowerCase())) {
    return '该文件已经存在'
  }
  const extension = relative.includes('.') ? relative.split('.').pop()?.toLowerCase() : ''
  if (directory === 'scripts' && !['py', 'sh', 'js'].includes(extension ?? '')) {
    return 'scripts 目录支持 .py、.sh、.js，请自行选择一种扩展名'
  }
  if (
    (directory === 'references' || directory === 'examples') &&
    !['md', 'txt', 'json', 'yaml', 'yml', 'csv'].includes(extension ?? '')
  ) {
    return '该目录支持 md、txt、json、yaml、yml、csv 文件'
  }
  return true
}

function updateSelectedFilePath(value: string | number): void {
  if (selectedFile.value) {
    selectedFile.value.path = `${selectedFileDirectory.value}/${normalizeRelativePath(String(value))}`
  }
}

function updateSelectedFileContent(value: string | number): void {
  if (selectedFile.value) selectedFile.value.content = String(value)
}

function reset(): void {
  activationDescription.value = ''
  instructions.value = ''
  allowedToolsText.value = ''
  files.value = []
  selectedFileId.value = null
  selectedDirectory.value = 'scripts'
  skillMarkdownSelected.value = true
  fileSequence = 0
}

async function buildPackage(): Promise<File> {
  if (!props.skillName.trim()) throw new Error('请先填写 Skill 技术标识')
  if (!activationDescription.value.trim() || !instructions.value.trim()) {
    throw new Error('请填写版本激活描述和 Skill 指令')
  }
  return buildSkillPackage({
    name: props.skillName.trim(),
    activationDescription: activationDescription.value.trim(),
    instructions: instructions.value.trim(),
    allowedTools: allowedToolsText.value
      .split(/\r?\n/)
      .map((item) => item.trim())
      .filter(Boolean),
    files: files.value.map(({ path, content }) => ({ path, content })),
  })
}

defineExpose({ buildPackage, reset })
</script>

<style scoped>
.skill-package-builder {
  width: 100%;
}
.skill-builder {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  width: 100%;
  min-height: 360px;
  overflow: hidden;
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  background: var(--adw-surface);
}
.skill-builder__tree {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 6px;
  padding: 14px 10px;
  border-right: 1px solid var(--adw-border-color-light);
  background: var(--adw-surface-muted);
}
.skill-builder__tree-header,
.skill-builder__editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.skill-builder__tree-header {
  padding: 0 6px 8px;
}
.skill-builder__tree-header strong {
  color: var(--adw-text-primary);
  font-size: 13px;
}
.skill-builder__root,
.skill-builder__required-file {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 7px 8px;
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.skill-builder__root .el-icon {
  color: var(--adw-color-primary);
}
.skill-builder__required-file {
  width: calc(100% - 16px);
  margin-left: 16px;
  border: 0;
  border-radius: var(--adw-radius-sm);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.skill-builder__required-file:hover,
.skill-builder__required-file.active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.skill-builder__required-file span {
  flex: 1;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.skill-builder__required-file small {
  color: var(--adw-text-tertiary);
  font-size: 10px;
}
.skill-builder__directory {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 7px;
  padding: 8px;
  border: 0;
  border-radius: var(--adw-radius-sm);
  color: var(--adw-text-secondary);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.skill-builder__directory:hover,
.skill-builder__directory.active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.skill-builder__directory span {
  flex: 1;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  font-weight: 600;
}
.skill-builder__directory-add {
  opacity: 0;
}
.skill-builder__directory:hover .skill-builder__directory-add,
.skill-builder__directory.active .skill-builder__directory-add {
  opacity: 1;
}
.skill-builder__file {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 7px;
  width: calc(100% - 24px);
  margin-left: 24px;
  padding: 8px;
  border: 0;
  border-radius: var(--adw-radius-sm);
  color: var(--adw-text-secondary);
  background: transparent;
  text-align: left;
  cursor: pointer;
}
.skill-builder__file:hover,
.skill-builder__file.active {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}
.skill-builder__file span {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.skill-builder__file-delete {
  flex: 0 0 auto;
  color: var(--adw-text-tertiary);
}
.skill-builder__file-delete:hover {
  color: var(--adw-color-danger);
}
.skill-builder__hint {
  margin: auto 6px 0;
  color: var(--adw-text-tertiary);
  font-size: 11px;
  line-height: 1.5;
}
.skill-builder__editor {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
}
.skill-builder__editor-header {
  align-items: flex-start;
}
.skill-builder__editor-header > div {
  display: grid;
  gap: 3px;
}
.skill-builder__editor-header span {
  color: var(--adw-text-secondary);
  font-size: 12px;
}
.skill-builder__editor-header code {
  color: var(--adw-text-tertiary);
  font-size: 11px;
}
.skill-builder__content {
  flex: 1;
}
.skill-builder__content :deep(textarea) {
  min-height: 220px;
  resize: vertical;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  line-height: 1.55;
}
.skill-builder__content--readonly :deep(textarea) {
  color: var(--adw-text-secondary);
  background: var(--adw-surface-muted);
  cursor: text;
}
.skill-builder__empty {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  color: var(--adw-text-tertiary);
  text-align: center;
}
.skill-builder__empty .el-icon {
  color: var(--adw-color-primary);
  font-size: 30px;
}
.skill-builder__empty strong {
  color: var(--adw-text-secondary);
  font-size: 14px;
}
.skill-builder__empty span,
.skill-builder__footer-hint {
  color: var(--adw-text-tertiary);
  font-size: 12px;
}
.skill-builder__footer-hint {
  margin: 10px 0 0;
}
@media (max-width: 720px) {
  .skill-builder {
    grid-template-columns: 1fr;
  }
  .skill-builder__tree {
    max-height: 190px;
    border-right: 0;
    border-bottom: 1px solid var(--adw-border-color-light);
  }
  .skill-builder__hint {
    display: none;
  }
}
</style>
