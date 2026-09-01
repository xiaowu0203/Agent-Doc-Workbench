import type { RouteRecordRaw } from 'vue-router'

import WorkbenchLayout from '@/layouts/WorkbenchLayout.vue'
import { SPACE_PERMISSIONS } from '@/shared/constants/permissions'
import ForbiddenView from '@/views/ForbiddenView.vue'
import HomeView from '@/views/HomeView.vue'
import LoginView from '@/views/LoginView.vue'
import SpaceOverviewView from '@/views/SpaceOverviewView.vue'
import AccessControlView from '@/views/AccessControlView.vue'

export const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { guestOnly: true },
  },
  {
    path: '/',
    component: WorkbenchLayout,
    meta: { requiresAuth: true },
    children: [
      {
        path: '',
        name: 'home',
        component: HomeView,
      },
      {
        path: 'spaces/:spaceId/overview',
        name: 'space-overview',
        component: SpaceOverviewView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.SPACE_READ,
        },
      },
      {
        path: 'spaces/:spaceId/access/roles',
        name: 'space-access-roles',
        component: AccessControlView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.ROLE_READ,
        },
      },
      {
        path: 'spaces/:spaceId/access/members',
        name: 'space-access-members',
        component: AccessControlView,
        meta: {
          requiresSpace: true,
          permission: SPACE_PERMISSIONS.MEMBER_READ,
        },
      },
      {
        path: 'forbidden',
        name: 'forbidden',
        component: ForbiddenView,
      },
    ],
  },
]
