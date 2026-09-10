<template>
  <div
    class="brand-mark"
    :class="{ 'brand-mark--compact': compact, 'brand-mark--stacked': stacked }"
  >
    <span class="brand-mark__logo" aria-hidden="true">
      <svg v-if="stacked" viewBox="0 0 24 24" fill="none">
        <path d="M5 3.75h8l4 4v12.5H5z" />
        <path d="M13 3.75v4h4M8 13h6M8 16h4" />
        <path class="brand-mark__logo-accent" d="M18.25 13.25v5M15.75 15.75h5" />
      </svg>
      <template v-else>A</template>
    </span>
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

.brand-mark__logo svg {
  width: 100%;
  height: 100%;
  overflow: visible;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.6;
}

.brand-mark__logo-accent {
  stroke: #70a7ff;
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
  gap: 10px;
}

.brand-mark--stacked .brand-mark__name {
  font-size: 14px;
  font-weight: 400;
  line-height: 1.05;
  white-space: normal;
}

.brand-mark--stacked .brand-mark__logo {
  width: 20px;
  height: 20px;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
  font-size: 0;
}

.brand-mark--stacked .brand-mark__name::first-line {
  font-weight: 700;
}

.brand-mark--compact .brand-mark__logo {
  width: 36px;
  height: 36px;
  border-radius: var(--adw-radius-md);
  font-size: 22px;
}

.brand-mark--compact.brand-mark--stacked .brand-mark__logo {
  width: 20px;
  height: 20px;
  border-radius: 0;
  font-size: 0;
}
</style>
