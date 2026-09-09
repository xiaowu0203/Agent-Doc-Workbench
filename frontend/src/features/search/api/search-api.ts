import { request } from '@/api/client'
import type { EntityId } from '@/features/workspace/types'

import type { WorkbenchSearchResult } from '../types'

export function searchWorkbench(
  spaceId: EntityId,
  keyword: string,
  signal?: AbortSignal,
): Promise<WorkbenchSearchResult> {
  return request<WorkbenchSearchResult>({
    method: 'POST',
    url: '/workbench/search',
    data: { spaceId, keyword, limitPerType: 5 },
    signal,
  })
}
