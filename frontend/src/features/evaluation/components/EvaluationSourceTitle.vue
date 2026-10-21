<template>
  <span :title="name ? '当前目录名称；冻结版本身份保持不变' : '来源名称暂不可用'">
    {{ name ? `${name} · v${versionNo}` : fallback }}
  </span>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import * as api from '../api/evaluation-api'
const props = defineProps<{
  spaceId: string
  kind: 'datasets' | 'test-cases'
  versionId: string
  fallback: string
}>()
const name = ref(''),
  versionNo = ref<number>()
let pending: AbortController | undefined
watch(
  () => [props.spaceId, props.kind, props.versionId],
  async () => {
    pending?.abort()
    const controller = new AbortController()
    pending = controller
    name.value = ''
    versionNo.value = undefined
    const { spaceId, kind, versionId } = props
    if (!versionId) return
    try {
      const version =
        kind === 'datasets'
          ? await api.getDatasetVersion(versionId, controller.signal)
          : await api.getTestCaseVersion(versionId, controller.signal)
      if (controller.signal.aborted) return
      if (String(version.id) !== versionId || String(version.spaceId) !== spaceId) return
      const parentId = 'datasetId' in version ? version.datasetId : version.testCaseId
      if (!parentId) return
      const parent =
        kind === 'datasets'
          ? await api.getDataset(parentId, controller.signal)
          : await api.getTestCase(parentId, controller.signal)
      if (
        controller.signal.aborted ||
        String(parent.id) !== String(parentId) ||
        String(parent.spaceId) !== spaceId
      )
        return
      name.value = parent.name
      versionNo.value = version.versionNo
    } catch {
      // 名称仅辅助识别；目录读取失败时保留可核对的运行/实验身份。
    }
  },
  { immediate: true },
)
onBeforeUnmount(() => pending?.abort())
</script>
