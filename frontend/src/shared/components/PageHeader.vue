<template>
  <header class="page-header">
    <div class="page-header__copy">
      <slot name="breadcrumb" />
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="description" class="page-header__description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  description?: string
}>()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--adw-space-6);
}

.page-header__copy {
  min-width: 0;
}

.page-header__title {
  margin: 0;
  color: var(--adw-text-primary);
  font-size: var(--adw-font-size-title);
  line-height: 1.25;
}

.page-header__description {
  margin: var(--adw-space-2) 0 0;
  color: var(--adw-text-secondary);
  font-size: var(--adw-font-size-body);
  line-height: 1.6;
}

.page-header__actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: var(--adw-space-3);
}

@media (max-width: 720px) {
  .page-header {
    flex-direction: column;
  }
}
</style>
