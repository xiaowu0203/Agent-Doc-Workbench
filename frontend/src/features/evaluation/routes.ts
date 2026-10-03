import type { RouteRecordRaw } from 'vue-router'

import { EVALUATION_SECTIONS, type EvaluationSection } from './navigation'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'

const identityParams: Record<EvaluationSection, string> = {
  evaluators: 'evaluatorId',
  'test-cases': 'testCaseId',
  datasets: 'datasetId',
  runs: 'runId',
  experiments: 'experimentId',
}

export const evaluationRoute: RouteRecordRaw = {
  path: 'spaces/:spaceId/evaluation',
  component: () => import('./components/EvaluationWorkbenchLayout.vue'),
  meta: { requiresSpace: true, permission: SPACE_PERMISSIONS.EVALUATION_READ },
  children: [
    {
      path: '',
      redirect: (to) => `/spaces/${to.params.spaceId}/evaluation/test-cases`,
    },
    ...EVALUATION_SECTIONS.map((section): RouteRecordRaw => ({
      path: `${section.key}/:${identityParams[section.key]}?`,
      name: `evaluation-${section.key}`,
      component:
        section.group === 'catalog'
          ? () => import('@/views/EvaluationCatalogView.vue')
          : section.key === 'runs'
            ? () => import('@/views/EvaluationRunView.vue')
            : () => import('@/views/EvaluationEntryView.vue'),
      meta: { evaluationSection: section.key },
      props: (route) => ({
        section: section.key,
        resourceId: route.params[identityParams[section.key]] || undefined,
      }),
    })),
  ],
}
