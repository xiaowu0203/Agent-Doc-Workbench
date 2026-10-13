import { onBeforeUnmount, ref, watch } from 'vue'
import { createTask } from './api/task-api'
import type { CreateTaskRequest } from './types'

/** 两个正常创建入口复用同一请求身份；草稿保存不使用此键。 */
export function useTaskCreation(
  payload: () => Omit<CreateTaskRequest, 'clientRequestKey'>,
  allowed: () => boolean,
) {
  const pending = ref(false)
  const responseMissing = ref(false)
  let prepared: CreateTaskRequest | null = null
  let controller: AbortController | null = null

  watch(
    () => JSON.stringify([payload(), allowed()]),
    () => {
      prepared = null
      controller?.abort()
      pending.value = false
    },
    { flush: 'sync' },
  )
  onBeforeUnmount(() => controller?.abort())

  async function create() {
    if (pending.value || !allowed()) return null
    prepared ??= {
      ...payload(),
      focusRegions: payload().focusRegions.map((region) => ({ ...region })),
      clientRequestKey: crypto.randomUUID(),
    }
    const current = new AbortController()
    controller = current
    pending.value = true
    try {
      const result = await createTask(prepared, current.signal)
      if (current.signal.aborted) return null
      responseMissing.value = false
      return result
    } catch (error) {
      if (current.signal.aborted) return null
      responseMissing.value = true
      throw error
    } finally {
      if (controller === current) pending.value = false
    }
  }

  return { pending, responseMissing, create }
}
