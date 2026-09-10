import { createRouter, createWebHistory } from 'vue-router'

import { installRouterGuards } from './guards'
import { routes } from './routes'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
})

installRouterGuards(router)

export default router
