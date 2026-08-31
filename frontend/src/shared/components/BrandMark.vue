<template>
  <div
    class="brand-mark"
    :class="{ 'brand-mark--compact': compact, 'brand-mark--stacked': stacked }"
  >
    <span class="brand-mark__logo" aria-hidden="true">A</span>
    <span v-if="!compact" class="brand-mark__name">
      <template v-if="stacked">Agent Doc<br />Workbench</template>
      <template v-else>Agent Doc Workbench</template>
    </span>
  </div>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    compact?: boolean
    stacked?: boolean
  }>(),
  {
    compact: false,
    stacked: false,
  },
)
</script>

<style scoped>
.brand-mark {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: var(--adw-space-4);
  color: inherit;
}

.brand-mark__logo {
  display: inline-flex;
  width: 44px;
  height: 44px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border: 1px solid rgb(255 255 255 / 12%);
  border-radius: 10px;
  color: #ffffff;
  background: linear-gradient(145deg, #3f72cf, #1f4e9f);
  box-shadow: 0 10px 28px rgb(0 25 77 / 24%);
  font-size: 25px;
  font-weight: 800;
}

.brand-mark__name {
  overflow: hidden;
  font-size: 22px;
  font-weight: 700;
  line-height: 1.15;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.brand-mark--stacked {
  gap: var(--adw-space-3);
}

.brand-mark--stacked .brand-mark__name {
  font-size: 17px;
  line-height: 1.2;
  white-space: normal;
}

.brand-mark--compact .brand-mark__logo {
  width: 36px;
  height: 36px;
  border-radius: var(--adw-radius-md);
  font-size: 22px;
}
</style>
