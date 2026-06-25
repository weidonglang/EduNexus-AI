<script setup lang="ts">
withDefaults(
  defineProps<{
    level: 'low' | 'medium' | 'high' | 'none'
  }>(),
  {
    level: 'none',
  },
)

const labels = {
  none: '普通',
  low: '低风险',
  medium: '中风险',
  high: '高风险',
}
</script>

<template>
  <span class="nexus-risk-badge" :class="`is-${level}`">{{ labels[level] }}</span>
</template>

<style scoped>
.nexus-risk-badge {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 9px;
  border-radius: 999px;
  color: #566579;
  background: #f1f3f6;
  border: 1px solid #e1e7ef;
  font-size: 12px;
  font-weight: 750;
}

.nexus-risk-badge.is-low {
  color: var(--nexus-info);
  background: var(--nexus-info-soft);
  border-color: #cce7ef;
}

.nexus-risk-badge.is-medium {
  color: var(--nexus-warning);
  background: var(--nexus-warning-soft);
  border-color: #efd9bd;
}

.nexus-risk-badge.is-high {
  color: var(--nexus-danger);
  background: var(--nexus-danger-soft);
  border-color: #f0c8c8;
}
</style>
