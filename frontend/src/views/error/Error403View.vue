<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ShieldAlert } from 'lucide-vue-next'
import { NexusPageHeader, NexusStatusCard } from '@/components/nexus'

const router = useRouter()
</script>

<template>
  <NexusPageHeader title="无权限访问" description="当前账号没有访问该页面或接口的权限。" eyebrow="403">
    <template #actions>
      <el-button type="primary" @click="router.push('/dashboard')">返回首页</el-button>
    </template>
  </NexusPageHeader>
  <NexusStatusCard
    title="权限边界已生效"
    status="已拦截"
    tone="degraded"
    description="如果你需要访问该功能，请联系管理员调整角色、菜单或接口权限。"
    meta="前端隐藏不是安全边界，后端仍会再次校验。"
  />
  <div class="error-visual">
    <ShieldAlert :size="58" />
  </div>
</template>

<style scoped>
.error-visual {
  min-height: 260px;
  display: grid;
  place-items: center;
  margin-top: 18px;
  color: var(--nexus-warning);
  background: linear-gradient(180deg, #ffffff, #fbfcfd);
  border: 1px solid var(--nexus-border);
  border-radius: var(--nexus-radius);
  box-shadow: var(--nexus-shadow-sm);
}
</style>
