<script setup lang="ts">
defineProps<{
  title: string
  description?: string
  eyebrow?: string
}>()
</script>

<template>
  <header class="nexus-page-header">
    <div>
      <span v-if="eyebrow" class="nexus-page-header__eyebrow">{{ eyebrow }}</span>
      <h1>{{ title }}</h1>
      <p v-if="description">{{ description }}</p>
    </div>
    <div v-if="$slots.actions" class="nexus-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>

<style scoped>
.nexus-page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  margin-bottom: 18px;
}

.nexus-page-header__eyebrow {
  display: inline-flex;
  margin-bottom: 8px;
  color: var(--nexus-primary-strong);
  font-size: 13px;
  font-weight: 750;
}

.nexus-page-header h1 {
  margin: 0;
  color: var(--nexus-text);
  font-size: 25px;
  line-height: 1.25;
  letter-spacing: 0;
}

.nexus-page-header p {
  max-width: 760px;
  margin: 8px 0 0;
  color: var(--nexus-text-muted);
  line-height: 1.7;
}

.nexus-page-header__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 760px) {
  .nexus-page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .nexus-page-header__actions {
    justify-content: flex-start;
  }
}
</style>
