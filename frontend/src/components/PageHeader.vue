<script setup lang="ts">
// 页面标题组件。
// 各业务页面复用它显示标题、说明和右侧操作按钮，减少重复模板代码。
defineProps<{
  title: string
  description?: string
}>()
</script>

<template>
  <header class="page-header">
    <h1>{{ title }}</h1>
    <p v-if="description">{{ description }}</p>
  </header>
</template>
