import type { RouteRecordRaw } from 'vue-router'

import { EVALUATION_SECTIONS, type EvaluationSection } from './navigation'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'

const identityParams: Record<EvaluationSection, string> = {
  evaluators: 'evaluatorId',
  'test-cases': 'testCaseId',
  datasets: 'datasetId',
  runs: 'runId',
  experiments: 'experimentId',
  'online-experiments': 'experimentId',
}

export const evaluationRoute: RouteRecordRaw = {
  path: 'spaces/:spaceId/evaluation',
  component: () => import('./components/EvaluationWorkbenchLayout.vue'),
  meta: { requiresSpace: true, permission: SPACE_PERMISSIONS.EVALUATION_READ },
  children: [
    {
      path: 'online-experiments/:experimentId/review',
      name: 'evaluation-online-original-review',
      component: () => import('@/views/OnlineOriginalReviewView.vue'),
      meta: { evaluationSection: 'online-experiments' },
      props: (route) => ({ experimentId: String(route.params.experimentId) }),
    },
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
          : section.key === 'online-experiments'
            ? () => import('@/views/OnlineExperimentView.vue')
            : section.key === 'runs'
              ? () => import('@/views/EvaluationRunView.vue')
              : () => import('@/views/ExperimentView.vue'),
      meta: { evaluationSection: section.key },
      props: (route) => ({
        section: section.key,
        resourceId: route.params[identityParams[section.key]] || undefined,
      }),
    })),
  ],
}
