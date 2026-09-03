<template>
  <el-drawer
    :model-value="open"
    append-to-body
    size="min(680px, 94vw)"
    :title="model ? '编辑模型配置' : '添加模型'"
    destroy-on-close
    @update:model-value="$emit('update:open', $event)"
  >
    <div class="model-drawer">
      <el-alert
        v-if="model"
        title="配置保存后版本号将递增，正在运行的任务继续使用原执行快照。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-form label-position="top">
        <div class="model-drawer__row">
          <el-form-item label="模型展示名称" required>
            <el-input v-model="form.displayName" maxlength="100" placeholder="例如：GPT-5.2" />
          </el-form-item>
          <el-form-item label="模型标识" required>
            <el-input v-model="form.modelKey" maxlength="128" placeholder="例如：gpt-5.2" />
          </el-form-item>
        </div>

        <div class="model-drawer__row">
          <el-form-item label="供应商" required>
            <el-select v-model="form.provider" filterable>
              <el-option
                v-for="provider in MODEL_PROVIDERS"
                :key="provider.value"
                :label="provider.label"
                :value="provider.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="适配器类型" required>
            <el-input :model-value="getAdapterLabel(resolvedAdapterType)" disabled />
            <span class="model-drawer__hint">由模型供应商和服务地址自动确定，无需手动选择。</span>
          </el-form-item>
        </div>

        <el-form-item label="模型服务基础地址">
          <el-input v-model="form.baseUrl" maxlength="500" placeholder="留空时使用供应商默认地址" />
        </el-form-item>

        <el-form-item label="API Key" :required="!model">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            maxlength="4096"
            autocomplete="new-password"
            :placeholder="model?.apiKeyConfigured ? '留空表示保留现有密钥' : '请输入模型 API Key'"
          />
          <span class="model-drawer__hint">密钥只写不回显，并由服务端加密保存。</span>
        </el-form-item>

        <div class="model-drawer__row">
          <el-form-item label="上下文窗口">
            <el-input-number
              v-model="form.contextWindow"
              :min="1"
              :controls="false"
              placeholder="例如：128000"
            />
          </el-form-item>
          <el-form-item label="最大输出 Token">
            <el-input-number
              v-model="form.maxOutputTokens"
              :min="1"
              :controls="false"
              placeholder="例如：16384"
            />
          </el-form-item>
        </div>

        <div class="model-drawer__row">
          <el-form-item label="输入价格 / 百万 Token">
            <el-input-number
              v-model="form.inputPricePerMillion"
              :min="0"
              :precision="6"
              :controls="false"
            />
          </el-form-item>
          <el-form-item label="输出价格 / 百万 Token">
            <el-input-number
              v-model="form.outputPricePerMillion"
              :min="0"
              :precision="6"
              :controls="false"
            />
          </el-form-item>
        </div>

        <el-form-item label="官方文档地址">
          <el-input v-model="form.officialUrl" maxlength="500" placeholder="https://..." />
        </el-form-item>

        <el-form-item label="模型描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>

        <el-collapse>
          <el-collapse-item title="高级配置" name="advanced">
            <div class="model-drawer__sampling-switch">
              <div>
                <div class="model-drawer__setting-title">自定义采样参数</div>
                <div class="model-drawer__hint">关闭时使用模型服务的默认采样策略。</div>
              </div>
              <el-switch v-model="form.customSamplingEnabled" />
            </div>

            <div
              :class="['model-drawer__sampling', { 'is-disabled': !form.customSamplingEnabled }]"
            >
              <div class="model-drawer__setting">
                <div class="model-drawer__setting-copy">
                  <div class="model-drawer__setting-title">温度</div>
                  <div class="model-drawer__hint">值越高，回答越有创造性；值越低，结果越稳定。</div>
                </div>
                <el-slider
                  v-model="form.temperature"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  :disabled="!form.customSamplingEnabled"
                  :show-tooltip="false"
                />
                <el-input-number
                  v-model="form.temperature"
                  :min="0"
                  :max="2"
                  :step="0.1"
                  :precision="1"
                  :disabled="!form.customSamplingEnabled"
                />
              </div>

              <div class="model-drawer__setting">
                <div class="model-drawer__setting-copy">
                  <div class="model-drawer__setting-title">Top-P 采样率</div>
                  <div class="model-drawer__hint">限制候选词的累计概率范围，通常保持为 1。</div>
                </div>
                <el-slider
                  v-model="form.topP"
                  :min="0"
                  :max="1"
                  :step="0.05"
                  :disabled="!form.customSamplingEnabled"
                  :show-tooltip="false"
                />
                <el-input-number
                  v-model="form.topP"
                  :min="0"
                  :max="1"
                  :step="0.05"
                  :precision="2"
                  :disabled="!form.customSamplingEnabled"
                />
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="$emit('update:open', false)">取消</el-button>
      <el-button v-if="!model" :loading="testing" @click="testDraft">测试连接</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import {
  ElAlert,
  ElButton,
  ElCollapse,
  ElCollapseItem,
  ElDrawer,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElSlider,
  ElSwitch,
} from 'element-plus'
import { computed, reactive, ref, watch } from 'vue'

import { normalizeApiError } from '@/api/errors'
import { createModel, testModelInput, updateModel } from '@/features/model/api/model-api'
import { getAdapterLabel, getDefaultAdapter, MODEL_PROVIDERS } from '@/features/model/model-options'
import type { ModelConfig, ModelConnectionTest, ModelInput } from '@/features/model/types'

const props = defineProps<{ open: boolean; model: ModelConfig | null }>()
const emit = defineEmits<{ 'update:open': [open: boolean]; saved: [] }>()

interface ModelForm {
  provider: string
  modelKey: string
  displayName: string
  officialUrl: string
  baseUrl: string
  apiKey: string
  customSamplingEnabled: boolean
  temperature: number
  topP: number
  preservedOptions: Record<string, unknown>
  contextWindow: number | null
  maxOutputTokens: number | null
  inputPricePerMillion: number | null
  outputPricePerMillion: number | null
  description: string
}

const form = reactive<ModelForm>(emptyForm())
const saving = ref(false)
const testing = ref(false)
const resolvedAdapterType = computed(() => getDefaultAdapter(form.provider, form.baseUrl))

watch(
  () => [props.open, props.model] as const,
  ([open, model]) => {
    if (!open) return
    Object.assign(form, model ? formFromModel(model) : emptyForm())
  },
  { immediate: true },
)

function emptyForm(): ModelForm {
  return {
    provider: 'openai',
    modelKey: '',
    displayName: '',
    officialUrl: '',
    baseUrl: '',
    apiKey: '',
    customSamplingEnabled: false,
    temperature: 0.7,
    topP: 1,
    preservedOptions: {},
    contextWindow: null,
    maxOutputTokens: null,
    inputPricePerMillion: null,
    outputPricePerMillion: null,
    description: '',
  }
}

function formFromModel(model: ModelConfig): ModelForm {
  const sampling = parseSamplingOptions(model.optionsJson)
  return {
    provider: model.provider,
    modelKey: model.modelKey,
    displayName: model.displayName,
    officialUrl: model.officialUrl || '',
    baseUrl: model.baseUrl || '',
    apiKey: '',
    customSamplingEnabled: sampling.enabled,
    temperature: sampling.temperature,
    topP: sampling.topP,
    preservedOptions: sampling.preserved,
    contextWindow: model.contextWindow,
    maxOutputTokens: model.maxOutputTokens,
    inputPricePerMillion: model.inputPricePerMillion,
    outputPricePerMillion: model.outputPricePerMillion,
    description: model.description || '',
  }
}

function validate(requireApiKey: boolean): boolean {
  if (!form.displayName.trim() || !form.modelKey.trim() || !form.provider) {
    ElMessage.warning('请填写模型名称、模型标识和供应商')
    return false
  }
  if (requireApiKey && !form.apiKey.trim()) {
    ElMessage.warning('请填写 API Key')
    return false
  }
  return true
}

function payload(): ModelInput {
  return {
    provider: form.provider,
    adapterType: resolvedAdapterType.value,
    modelKey: form.modelKey.trim(),
    displayName: form.displayName.trim(),
    officialUrl: form.officialUrl.trim() || undefined,
    baseUrl: form.baseUrl.trim() || undefined,
    apiKey: form.apiKey.trim() || undefined,
    optionsJson: serializeOptions(),
    contextWindow: form.contextWindow ?? undefined,
    maxOutputTokens: form.maxOutputTokens ?? undefined,
    inputPricePerMillion: form.inputPricePerMillion ?? undefined,
    outputPricePerMillion: form.outputPricePerMillion ?? undefined,
    description: form.description.trim() || undefined,
  }
}

function parseSamplingOptions(optionsJson: string | null): {
  enabled: boolean
  temperature: number
  topP: number
  preserved: Record<string, unknown>
} {
  if (!optionsJson) {
    return { enabled: false, temperature: 0.7, topP: 1, preserved: {} }
  }
  try {
    const parsed = JSON.parse(optionsJson)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      return { enabled: false, temperature: 0.7, topP: 1, preserved: {} }
    }
    const preserved = { ...parsed } as Record<string, unknown>
    const temperature = validNumber(preserved.temperature, 0, 2) ? preserved.temperature : 0.7
    const topP = validNumber(preserved.topP, 0, 1) ? preserved.topP : 1
    const enabled = validNumber(preserved.temperature, 0, 2) || validNumber(preserved.topP, 0, 1)
    if (validNumber(preserved.temperature, 0, 2)) delete preserved.temperature
    if (validNumber(preserved.topP, 0, 1)) delete preserved.topP
    return { enabled, temperature, topP, preserved }
  } catch {
    return { enabled: false, temperature: 0.7, topP: 1, preserved: {} }
  }
}

function validNumber(value: unknown, min: number, max: number): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= min && value <= max
}

function serializeOptions(): string | undefined {
  const options = { ...form.preservedOptions }
  if (form.customSamplingEnabled) {
    options.temperature = form.temperature
    options.topP = form.topP
  }
  return Object.keys(options).length ? JSON.stringify(options) : undefined
}

async function testDraft(): Promise<void> {
  if (!validate(true)) return
  testing.value = true
  try {
    showConnectionResult(await testModelInput(payload()))
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    testing.value = false
  }
}

async function save(): Promise<void> {
  if (!validate(!props.model)) return
  saving.value = true
  try {
    const modelPayload = payload()
    if (props.model) {
      await updateModel(props.model.id, modelPayload)
    } else {
      const connectionResult = await testModelInput(modelPayload)
      if (!connectionResult.connected) {
        showConnectionResult(connectionResult)
        return
      }
      await createModel(modelPayload)
    }
    ElMessage.success(props.model ? '模型配置已更新' : '模型连接成功，模型已添加')
    emit('update:open', false)
    emit('saved')
  } catch (error) {
    ElMessage.error(normalizeApiError(error).message)
  } finally {
    saving.value = false
  }
}

function showConnectionResult(result: ModelConnectionTest): void {
  ElMessage({
    type: result.connected ? 'success' : 'error',
    message: result.message || (result.connected ? '模型连接成功' : '模型连接失败'),
    duration: 3500,
    showClose: true,
  })
}
</script>

<style scoped>
.model-drawer {
  display: grid;
  gap: var(--adw-space-5);
}
.model-drawer__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--adw-space-4);
}
.model-drawer__row :deep(.el-input-number),
.model-drawer__row :deep(.el-select) {
  width: 100%;
}
.model-drawer__hint {
  margin-top: 6px;
  color: var(--adw-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}
.model-drawer__sampling-switch,
.model-drawer__setting {
  display: grid;
  align-items: center;
  gap: var(--adw-space-4);
}
.model-drawer__sampling-switch {
  grid-template-columns: 1fr auto;
  padding: 4px 0 var(--adw-space-4);
}
.model-drawer__sampling {
  display: grid;
  gap: var(--adw-space-5);
  padding: var(--adw-space-4);
  border: 1px solid var(--adw-border-color);
  border-radius: var(--adw-radius-md);
  background: var(--adw-surface-muted);
}
.model-drawer__sampling.is-disabled {
  opacity: 0.65;
}
.model-drawer__setting {
  grid-template-columns: minmax(170px, 0.9fr) minmax(160px, 1.5fr) 150px;
}
.model-drawer__setting-title {
  color: var(--adw-text-primary);
  font-size: 14px;
  font-weight: 600;
}
.model-drawer__setting-copy .model-drawer__hint {
  display: block;
}
.model-drawer__setting :deep(.el-input-number) {
  width: 150px;
}
@media (max-width: 640px) {
  .model-drawer__row {
    grid-template-columns: 1fr;
    gap: 0;
  }
  .model-drawer__setting {
    grid-template-columns: 1fr;
  }
  .model-drawer__setting :deep(.el-input-number) {
    width: 100%;
  }
}
</style>
