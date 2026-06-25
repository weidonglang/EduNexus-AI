<script setup lang="ts">
withDefaults(
  defineProps<{
    label: string
    value: string | number
    suffix?: string
    tone?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
    hint?: string
  }>(),
  {
    suffix: '',
    tone: 'primary',
    hint: '',
  },
)
</script>

<template>
  <article class="nexus-metric-card" :class="`is-${tone}`">
    <span>{{ label }}</span>
    <strong>{{ value }}<small v-if="suffix">{{ suffix }}</small></strong>
    <p v-if="hint">{{ hint }}</p>
  </article>
</template>

<style scoped>
.nexus-metric-card {
  min-height: 104px;
  padding: 18px;
  position: relative;
  overflow: hidden;
  background: linear-gradient(180deg, var(--nexus-surface), #fbfcfd);
  border: 1px solid var(--nexus-border);
  border-radius: var(--nexus-radius);
  box-shadow: var(--nexus-shadow-sm);
}

.nexus-metric-card::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 4px;
  background: var(--nexus-primary);
}

.nexus-metric-card.is-success::before {
  background: var(--nexus-success);
}

.nexus-metric-card.is-warning::before {
  background: var(--nexus-warning);
}

.nexus-metric-card.is-danger::before {
  background: var(--nexus-danger);
}

.nexus-metric-card.is-info::before {
  background: var(--nexus-info);
}

.nexus-metric-card span {
  display: block;
  color: var(--nexus-text-muted);
  font-size: 13px;
}

.nexus-metric-card strong {
  display: block;
  margin-top: 11px;
  color: var(--nexus-text);
  font-size: 29px;
  line-height: 1.05;
  letter-spacing: 0;
}

.nexus-metric-card small {
  margin-left: 4px;
  color: var(--nexus-text-muted);
  font-size: 14px;
  font-weight: 500;
}

.nexus-metric-card p {
  margin: 8px 0 0;
  color: var(--nexus-text-muted);
  font-size: 12px;
}
</style>
