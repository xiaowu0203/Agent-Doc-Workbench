<template>
  <slot v-if="allowed" />
  <slot v-else name="fallback" />
</template>

<script setup lang="ts">
import { computed } from 'vue'

import type { SpacePermissionCode } from '@/shared/constants/permissions'
import { useWorkspaceStore } from '@/stores/workspace'

const props = defineProps<{
  permission: SpacePermissionCode
}>()

const workspaceStore = useWorkspaceStore()
const allowed = computed(() => workspaceStore.hasPermission(props.permission))
</script>
