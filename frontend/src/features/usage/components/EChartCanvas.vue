<template>
  <div ref="container" class="echart-canvas"></div>
</template>

<script setup lang="ts">
import { LineChart, PieChart } from 'echarts/charts'
import {
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
} from 'echarts/components'
import { init, use, type EChartsCoreOption, type EChartsType } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{ option: EChartsCoreOption }>()
const container = ref<HTMLElement | null>(null)
let chart: EChartsType | null = null
let resizeObserver: ReturnType<typeof window.ResizeObserver> | null = null

use([
  LineChart,
  PieChart,
  GraphicComponent,
  GridComponent,
  LegendComponent,
  TooltipComponent,
  CanvasRenderer,
])

onMounted(async () => {
  await nextTick()
  if (!container.value) return
  chart = init(container.value)
  chart.setOption(props.option)
  resizeObserver = new window.ResizeObserver(() => chart?.resize())
  resizeObserver.observe(container.value)
})

watch(
  () => props.option,
  (option) => chart?.setOption(option, { notMerge: true }),
  { deep: true },
)

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  chart?.dispose()
})
</script>

<style scoped>
.echart-canvas {
  width: 100%;
  height: 100%;
}
</style>
