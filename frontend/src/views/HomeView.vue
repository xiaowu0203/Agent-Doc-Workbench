<template>
  <section class="workspace-entry surface-card">
    <DataState
      :loading="loading"
      :error="errorMessage"
      :empty="!loading && !errorMessage && workspaceStore.spaces.length === 0"
      loading-text="正在进入工作台"
      empty-text="当前账号还没有可访问的空间"
      @retry="enterWorkspace"
    >
      <span>正在打开空间总览…</span>
    </DataState>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { normalizeApiError } from '@/api/errors'
import DataState from '@/shared/components/DataState.vue'
import { useWorkspaceStore } from '@/stores/workspace'

const router = useRouter()
const workspaceStore = useWorkspaceStore()
const loading = ref(true)
const errorMessage = ref('')

async function enterWorkspace(): Promise<void> {
  loading.value = true
  errorMessage.value = ''
  try {
    const spaces = await workspaceStore.loadSpaces()
    if (spaces.length > 0) {
      await router.replace(`/spaces/${spaces[0].id}/overview`)
    }
  } catch (error) {
    errorMessage.value = normalizeApiError(error).message
  } finally {
    loading.value = false
  }
}

onMounted(enterWorkspace)
</script>

<style scoped>
.workspace-entry {
  min-height: 300px;
}
</style>
