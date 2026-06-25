<script setup lang="ts">
// 页面标题组件。
// 各业务页面复用它显示标题、说明和右侧操作按钮，减少重复模板代码。
import NexusPageHeader from '@/components/nexus/NexusPageHeader.vue'

defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <NexusPageHeader :title="title" :description="description">
    <template v-if="$slots.actions" #actions>
      <slot name="actions" />
    </template>
  </NexusPageHeader>
</template>
