import type { RouteRecordRaw } from 'vue-router'

import WorkbenchLayout from '@/layouts/WorkbenchLayout.vue'
import HomeView from '@/views/HomeView.vue'

export const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: WorkbenchLayout,
    children: [
      {
        path: '',
        name: 'home',
        component: HomeView,
      },
    ],
  },
]
