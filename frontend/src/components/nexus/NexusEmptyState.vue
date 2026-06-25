<script setup lang="ts">
import { Inbox } from 'lucide-vue-next'

withDefaults(
  defineProps<{
    title?: string
    description?: string
  }>(),
  {
    title: '暂无数据',
    description: '当前条件下没有可展示的记录。',
  },
)
</script>

<template>
  <div class="nexus-empty-state">
    <Inbox :size="30" />
    <strong>{{ title }}</strong>
    <span>{{ description }}</span>
    <div v-if="$slots.actions" class="nexus-empty-state__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.nexus-empty-state {
  min-height: 180px;
  display: grid;
  place-items: center;
  align-content: center;
  gap: 8px;
  padding: 26px;
  color: var(--nexus-text-muted);
  background: var(--nexus-surface-soft);
  border: 1px dashed #cbd5e1;
  border-radius: var(--nexus-radius);
  text-align: center;
}

.nexus-empty-state svg {
  color: var(--nexus-primary);
}

.nexus-empty-state strong {
  color: var(--nexus-text);
}

.nexus-empty-state span {
  max-width: 360px;
  line-height: 1.6;
}

.nexus-empty-state__actions {
  margin-top: 8px;
}
</style>
