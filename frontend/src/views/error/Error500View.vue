<script setup lang="ts">
import { useRouter } from 'vue-router'
import { RotateCcw } from 'lucide-vue-next'
import { NexusPageHeader, NexusStatusCard } from '@/components/nexus'

const router = useRouter()
</script>

<template>
  <NexusPageHeader title="系统异常" description="当前页面遇到异常，请稍后重试或携带 traceId 联系管理员。" eyebrow="500">
    <template #actions>
      <el-button :icon="RotateCcw" type="primary" @click="router.go(0)">重新加载</el-button>
    </template>
  </NexusPageHeader>
  <NexusStatusCard
    title="异常处理"
    status="待排查"
    tone="down"
    description="后端异常会统一返回错误码和 traceId，便于审计日志与服务日志定位。"
  />
</template>
