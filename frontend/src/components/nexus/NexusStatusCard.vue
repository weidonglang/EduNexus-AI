<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    status: string
    description?: string
    tone?: 'healthy' | 'degraded' | 'down' | 'neutral'
    meta?: string
  }>(),
  {
    description: '',
    tone: 'neutral',
    meta: '',
  },
)
</script>

<template>
  <article class="nexus-status-card" :class="`is-${tone}`">
    <div class="nexus-status-card__top">
      <h3>{{ title }}</h3>
      <span>{{ status }}</span>
    </div>
    <p v-if="description">{{ description }}</p>
    <small v-if="meta">{{ meta }}</small>
  </article>
</template>

<style scoped>
.nexus-status-card {
  padding: 16px;
  background: var(--nexus-surface);
  border: 1px solid var(--nexus-border);
  border-radius: var(--nexus-radius);
  box-shadow: var(--nexus-shadow-sm);
}

.nexus-status-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.nexus-status-card h3 {
  margin: 0;
  color: var(--nexus-text);
  font-size: 16px;
}

.nexus-status-card span {
  display: inline-flex;
  padding: 4px 9px;
  border-radius: 999px;
  color: #566579;
  background: #f1f3f6;
  font-size: 12px;
  font-weight: 750;
  white-space: nowrap;
}

.nexus-status-card.is-healthy span {
  color: var(--nexus-success);
  background: var(--nexus-success-soft);
}

.nexus-status-card.is-degraded span {
  color: var(--nexus-warning);
  background: var(--nexus-warning-soft);
}

.nexus-status-card.is-down span {
  color: var(--nexus-danger);
  background: var(--nexus-danger-soft);
}

.nexus-status-card p {
  margin: 10px 0 0;
  color: var(--nexus-text-muted);
  line-height: 1.6;
}

.nexus-status-card small {
  display: block;
  margin-top: 10px;
  color: var(--nexus-text-muted);
}
</style>
