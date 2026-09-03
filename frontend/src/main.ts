import { createApp } from 'vue'
import 'element-plus/dist/index.css'

import { configureAuthSession } from './api/auth-session'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import { useAuthStore } from './stores/auth'
import { useWorkspaceStore } from './stores/workspace'
import './styles/main.css'

const app = createApp(App)
const authStore = useAuthStore(pinia)
const workspaceStore = useWorkspaceStore(pinia)

configureAuthSession({
  getAccessToken: () => authStore.accessToken,
  refreshAccessToken: () => authStore.refreshAccessToken(),
  clearSession: () => {
    authStore.clearSession()
    workspaceStore.clearWorkspace()
  },
  onSessionExpired: async () => {
    const currentRoute = router.currentRoute.value
    if (currentRoute.name === 'login') return
    const redirect = currentRoute.fullPath.startsWith('/') ? currentRoute.fullPath : '/'
    await router.replace({ path: '/login', query: { redirect } })
  },
})

async function bootstrap(): Promise<void> {
  authStore.restoreSession()
  app.use(pinia)
  app.use(router)
  await router.isReady()
  app.mount('#app')
}

void bootstrap()
