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
})

async function bootstrap(): Promise<void> {
  authStore.restoreSession()
  app.use(pinia)
  app.use(router)
  await router.isReady()
  app.mount('#app')
}

void bootstrap()
