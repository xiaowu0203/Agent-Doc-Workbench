import { mount } from '@vue/test-utils'
import { defineComponent, h, reactive, ref } from 'vue'
import { beforeEach, expect, it, vi } from 'vitest'
import { createTask } from './api/task-api'
import { useTaskCreation } from './use-task-creation'
import type { CreateTaskRequest } from './types'

vi.mock('./api/task-api', () => ({ createTask: vi.fn() }))
beforeEach(() => vi.clearAllMocks())

function setup() {
  const payload = reactive<Omit<CreateTaskRequest, 'clientRequestKey'>>({
    spaceId: '7',
    agentId: '3',
    documentId: '101',
    name: '任务',
    instruction: '核对原始文档',
    tokenBudget: null,
    readScope: 'FULL',
    focusRegions: [],
  })
  let creation!: ReturnType<typeof useTaskCreation>
  const allowed = ref(true)
  const wrapper = mount(
    defineComponent({
      setup() {
        creation = useTaskCreation(
          () => payload,
          () => allowed.value,
        )
        return () => h('div')
      },
    }),
  )
  return { payload, creation, wrapper, allowed }
}

it('reuses the same request key after a lost response without accepting a client variant', async () => {
  vi.mocked(createTask)
    .mockRejectedValueOnce(new Error('响应丢失'))
    .mockResolvedValueOnce({ id: '501', taskNo: 'T-501' })
  const { creation, wrapper } = setup()
  await expect(creation.create()).rejects.toThrow('响应丢失')
  expect(creation.responseMissing.value).toBe(true)
  expect(await creation.create()).toEqual({ id: '501', taskNo: 'T-501' })
  const [first, second] = vi.mocked(createTask).mock.calls
  expect(first![0]).toEqual(second![0])
  expect(first![0].clientRequestKey).toMatch(/^[A-Za-z0-9._:-]{1,64}$/)
  expect(first![0]).not.toHaveProperty('variant')
  expect(first![0]).not.toHaveProperty('templateId')
  expect(creation.responseMissing.value).toBe(false)
  wrapper.unmount()
})

it('uses a fresh key after edits even when the form is restored to its former contents', async () => {
  vi.mocked(createTask).mockRejectedValue(new Error('暂未确认'))
  const { payload, creation, wrapper } = setup()
  await expect(creation.create()).rejects.toThrow()
  payload.instruction = '新的任务'
  payload.instruction = '核对原始文档'
  await expect(creation.create()).rejects.toThrow()
  expect(vi.mocked(createTask).mock.calls[1]![0].clientRequestKey).not.toBe(
    vi.mocked(createTask).mock.calls[0]![0].clientRequestKey,
  )
  // 编辑不会隐藏之前可能已经创建任务的提醒。
  expect(creation.responseMissing.value).toBe(true)
  wrapper.unmount()
})

it('suppresses overlapping submissions and ignores a response after unmount', async () => {
  let resolve!: (task: { id: string; taskNo: string }) => void
  vi.mocked(createTask).mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done
      }),
  )
  const { creation, wrapper } = setup()
  const pending = creation.create()
  expect(await creation.create()).toBeNull()
  expect(createTask).toHaveBeenCalledTimes(1)
  wrapper.unmount()
  expect(vi.mocked(createTask).mock.calls[0]![1]!.aborted).toBe(true)
  resolve({ id: '501', taskNo: 'T-501' })
  expect(await pending).toBeNull()
})

it('aborts an old scope and isolates the submitted focus regions from later edits', async () => {
  let resolve!: (task: { id: string; taskNo: string }) => void
  vi.mocked(createTask).mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done
      }),
  )
  const { payload, creation, wrapper } = setup()
  payload.focusRegions.push({ start: 0, length: 1, textPreview: '原', instruction: '核验' })
  const pending = creation.create()
  const submitted = vi.mocked(createTask).mock.calls[0]![0]
  payload.focusRegions[0]!.instruction = '已修改'
  payload.spaceId = '8'
  expect(submitted.focusRegions[0]!.instruction).toBe('核验')
  expect(vi.mocked(createTask).mock.calls[0]![1]!.aborted).toBe(true)
  resolve({ id: '501', taskNo: 'T-501' })
  expect(await pending).toBeNull()
  wrapper.unmount()
})

it('refuses creation after permission loss and ignores a response already in flight', async () => {
  let resolve!: (task: { id: string; taskNo: string }) => void
  vi.mocked(createTask).mockImplementation(
    () =>
      new Promise((done) => {
        resolve = done
      }),
  )
  const { creation, wrapper, allowed } = setup()
  const pending = creation.create()
  allowed.value = false
  expect(await creation.create()).toBeNull()
  expect(createTask).toHaveBeenCalledTimes(1)
  expect(vi.mocked(createTask).mock.calls[0]![1]!.aborted).toBe(true)
  resolve({ id: '501', taskNo: 'T-501' })
  expect(await pending).toBeNull()
  wrapper.unmount()
})
