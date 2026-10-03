import { beforeEach, expect, it, vi } from 'vitest'
import { request } from '@/api/client'
import { getReplayEligibility, getTaskArtifacts, getTaskTrace, searchTasks } from './task-api'
import { getEvaluationTaskLink } from '@/features/evaluation/api/evaluation-api'
vi.mock('@/api/client', () => ({ request: vi.fn() }))
beforeEach(() => vi.clearAllMocks())
it('queries diagnostic data by business task identity and passes cancellation', async () => {
  const id = '2104855879319314433',
    signal = new AbortController().signal
  await getTaskTrace(id, signal)
  await getTaskArtifacts(id, signal)
  await getReplayEligibility(id, signal)
  expect(vi.mocked(request).mock.calls.map(([config]) => config)).toEqual(
    ['trace-view', 'execution-artifacts', 'replay-eligibility'].map((endpoint) => ({
      method: 'GET',
      url: `/task/tasks/${id}/${endpoint}`,
      signal,
    })),
  )
})
it('uses explicit space on formal evaluation links and a LIVE filter for source searches', async () => {
  await getEvaluationTaskLink('9', '100')
  expect(request).toHaveBeenLastCalledWith({
    method: 'GET',
    url: '/evaluation/task-links/100',
    params: { spaceId: '9' },
    signal: undefined,
  })
  const query = {
    spaceId: '9',
    pageNum: 1,
    pageSize: 10,
    status: 'COMPLETED' as const,
    executionMode: 'LIVE' as const,
  }
  await searchTasks(query)
  expect(request).toHaveBeenLastCalledWith({
    method: 'POST',
    url: '/task/tasks/search',
    data: query,
    signal: undefined,
  })
})
