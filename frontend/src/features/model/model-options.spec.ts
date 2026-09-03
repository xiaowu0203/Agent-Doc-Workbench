import { describe, expect, it } from 'vitest'

import { getDefaultAdapter } from './model-options'

describe('getDefaultAdapter', () => {
  it.each([
    ['openai', 'openai-chat'],
    ['anthropic', 'anthropic-messages'],
    ['google-gemini', 'google-genai'],
    ['deepseek', 'openai-compatible'],
    ['zhipu-glm', 'openai-compatible'],
    ['alibaba-qwen', 'openai-compatible'],
    ['xiaomi-mimo', 'openai-compatible'],
    ['openai-compatible', 'openai-compatible'],
  ])('maps %s to %s', (provider, adapter) => {
    expect(getDefaultAdapter(provider)).toBe(adapter)
  })

  it('uses the Anthropic adapter for the DeepSeek Anthropic endpoint', () => {
    expect(getDefaultAdapter('deepseek', 'https://api.deepseek.com/anthropic')).toBe(
      'anthropic-messages',
    )
  })
})
