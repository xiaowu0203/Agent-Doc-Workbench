<template>
  <article class="metric-card surface-card" :class="`metric-card--${tone}`">
    <span class="metric-card__icon"><slot /></span>
    <div>
      <span class="metric-card__title">{{ title }}</span>
      <strong>{{ value }}</strong>
      <small class="metric-card__comparison">
        <span>较上一周期</span>
        <span class="metric-card__change" :class="`metric-card__change--${change.tone}`">
          <span v-if="change.direction !== 'none'" aria-hidden="true">{{ change.arrow }}</span>
          {{ change.label }}
        </span>
      </small>
    </div>
  </article>
</template>

<script setup lang="ts">
export interface MetricChange {
  label: string
  direction: 'up' | 'down' | 'flat' | 'none'
  arrow: '↑' | '↓' | '→' | ''
  tone: 'positive' | 'negative' | 'neutral' | 'muted'
}

defineProps<{
  title: string
  value: string
  change: MetricChange
  tone: 'blue' | 'green' | 'purple' | 'orange'
}>()
</script>

<style scoped>
.metric-card {
  display: flex;
  align-items: center;
  gap: var(--adw-space-4);
  min-height: 108px;
  padding: var(--adw-space-5);
}

.metric-card__icon {
  display: inline-flex;
  width: 48px;
  height: 48px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  font-size: 24px;
}

.metric-card--blue .metric-card__icon {
  color: var(--adw-color-primary);
  background: var(--adw-color-primary-soft);
}

.metric-card--green .metric-card__icon {
  color: var(--adw-color-success);
  background: var(--adw-color-success-soft);
}

.metric-card--purple .metric-card__icon {
  color: var(--adw-color-accent);
  background: #f0ecff;
}

.metric-card--orange .metric-card__icon {
  color: #ed7a16;
  background: #fff1e4;
}

.metric-card > div {
  display: grid;
  gap: 2px;
}

.metric-card__title {
  color: var(--adw-text-secondary);
  font-size: 13px;
}

.metric-card strong {
  font-size: 27px;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.metric-card small {
  color: var(--adw-text-tertiary);
}

.metric-card__comparison {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}

.metric-card__change {
  font-weight: 600;
}

.metric-card__change--positive {
  color: #ef4444;
}

.metric-card__change--negative {
  color: #10a981;
}

.metric-card__change--neutral,
.metric-card__change--muted {
  color: var(--adw-text-tertiary);
  font-weight: 500;
}
</style>
