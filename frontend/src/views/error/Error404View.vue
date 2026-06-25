<script setup lang="ts">
import { useRouter } from 'vue-router'
import { SearchX } from 'lucide-vue-next'
import { NexusEmptyState, NexusPageHeader } from '@/components/nexus'

const router = useRouter()
</script>

<template>
  <NexusPageHeader title="页面不存在" description="请求的页面没有匹配到系统路由。" eyebrow="404" />
  <NexusEmptyState title="没有找到页面" description="请从首页菜单重新进入业务页面。">
    <template #actions>
      <el-button :icon="SearchX" type="primary" @click="router.push('/dashboard')">返回首页</el-button>
    </template>
  </NexusEmptyState>
</template>
