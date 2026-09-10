export const MODEL_PROVIDERS = [
  { value: 'openai', label: 'OpenAI' },
  { value: 'anthropic', label: 'Anthropic' },
  { value: 'google-gemini', label: 'Google Gemini' },
  { value: 'deepseek', label: 'DeepSeek' },
  { value: 'zhipu-glm', label: '智谱 GLM' },
  { value: 'alibaba-qwen', label: '通义千问' },
  { value: 'xiaomi-mimo', label: '小米 MiMo' },
  { value: 'openai-compatible', label: 'OpenAI Compatible' },
] as const

export const MODEL_ADAPTERS = [
  { value: 'openai-chat', label: 'OpenAI Chat' },
  { value: 'openai-compatible', label: 'OpenAI Compatible' },
  { value: 'anthropic-messages', label: 'Anthropic Messages' },
  { value: 'google-genai', label: 'Google GenAI' },
] as const

export function getProviderLabel(value: string): string {
  return MODEL_PROVIDERS.find((item) => item.value === value)?.label ?? value
}

export function getAdapterLabel(value: string): string {
  return MODEL_ADAPTERS.find((item) => item.value === value)?.label ?? value
}

export function getDefaultAdapter(provider: string, baseUrl = ''): string {
  if (provider === 'openai') return 'openai-chat'
  if (provider === 'anthropic') return 'anthropic-messages'
  if (provider === 'google-gemini') return 'google-genai'
  if (provider === 'deepseek' && /\/anthropic(?:\/|$)/i.test(baseUrl.trim())) {
    return 'anthropic-messages'
  }
  return 'openai-compatible'
}
