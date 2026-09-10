import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import ModelManagementView from './ModelManagementView.vue'

import * as modelApi from '@/features/model/api/model-api'
import type { ModelConfig } from '@/features/model/types'

vi.mock('@/features/model/api/model-api', () => ({
  createModel: vi.fn(),
  searchModels: vi.fn(),
  testModelInput: vi.fn(),
  testSavedModel: vi.fn(),
  updateModel: vi.fn(),
  updateModelStatus: vi.fn(),
}))

const model: ModelConfig = {
  id: 9,
  provider: 'openai',
  adapterType: 'openai-chat',
  modelKey: 'gpt-5.2',
  displayName: 'GPT-5.2',
  officialUrl: 'https://example.com/docs',
  baseUrl: 'https://example.com/v1',
  apiKeyConfigured: true,
  optionsJson: null,
  configVersion: 3,
  contextWindow: 128000,
  maxOutputTokens: 16000,
  inputPricePerMillion: 10,
  outputPricePerMillion: 30,
  status: 'ENABLED',
  agentCount: 2,
  description: '通用文档模型',
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(modelApi.searchModels).mockResolvedValue({
    records: [model],
    total: 1,
    pageNum: 1,
    pageSize: 8,
  })
  vi.mocked(modelApi.testSavedModel).mockResolvedValue({
    connected: true,
    provider: 'openai',
    errorType: null,
    statusCode: null,
    retryable: false,
    message: '模型连接成功',
  })
})

describe('ModelManagementView', () => {
  it('renders platform model configuration summaries', async () => {
    const wrapper = mount(ModelManagementView)
    await flushPromises()

    expect(wrapper.text()).toContain('模型配置为平台级能力')
    expect(wrapper.text()).toContain('GPT-5.2')
    expect(wrapper.text()).toContain('OpenAI Chat')
    expect(wrapper.text()).toContain('128K')
    expect(wrapper.text()).toContain('v3')
    expect(wrapper.text()).toContain('关联 Agent')
    expect(wrapper.text()).toContain('2 个')
    expect(modelApi.searchModels).toHaveBeenCalledWith(
      expect.objectContaining({ pageNum: 1, pageSize: 8 }),
    )
  })

  it('tests the saved configuration from the card action', async () => {
    const wrapper = mount(ModelManagementView)
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((button) => button.text() === '测试连接')
      ?.trigger('click')
    await flushPromises()

    expect(modelApi.testSavedModel).toHaveBeenCalledWith(9)
  })
})
