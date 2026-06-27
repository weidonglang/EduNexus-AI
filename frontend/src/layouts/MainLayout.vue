<script setup lang="ts">
// 登录后的主布局组件。
// 学生、教师、管理员共用这一套布局：左侧菜单来自后端 /api/menus，顶部展示当前用户信息，
// 中间区域通过 RouterView 切换具体业务页面。
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { LogOut, Search, UserRound } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import type { MenuItem } from '@/api/menu'

const auth = useAuthStore()
const menu = useMenuStore()
const router = useRouter()
const route = useRoute()

const activePath = computed(() => route.path)
const roleLabel = computed(() => {
  const roles = auth.user?.roles ?? []
  if (roles.includes('ADMIN')) return '管理员'
  if (roles.includes('TEACHER')) return '教师'
  if (roles.includes('STUDENT')) return '学生'
  return '访客'
})

onMounted(async () => {
  await menu.loadMenus(true)
})

async function logout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  await router.push('/login')
}

function hasChildren(item: MenuItem) {
  return item.children && item.children.length > 0
}
</script>

<template>
  <div class="zf-shell">
    <header class="zf-brandbar">
      <div class="zf-brand">
        <div class="zf-logo">
          <UserRound :size="24" />
        </div>
        <span>教学综合信息服务平台</span>
      </div>
      <div class="zf-user">
        <span>{{ auth.user?.displayName ?? '未登录用户' }}</span>
        <small>{{ auth.user?.username ?? 'dev-student' }} · {{ roleLabel }}</small>
        <el-button :icon="LogOut" text @click="logout">退出</el-button>
      </div>
    </header>

    <nav class="zf-menubar">
      <el-menu :default-active="activePath" router mode="horizontal" class="zf-menu">
        <template v-for="item in menu.items" :key="item.code">
          <el-sub-menu v-if="hasChildren(item)" :index="item.path">
            <template #title>{{ item.title }}</template>
            <el-menu-item v-for="child in item.children" :key="child.code" :index="child.path">
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">{{ item.title }}</el-menu-item>
        </template>
      </el-menu>
      <div class="zf-search">
        <Search :size="15" />
        <span>搜索课程、成绩、考试</span>
      </div>
    </nav>

    <main class="zf-content">
      <RouterView />
    </main>

    <footer class="zf-footer">
      版权所有 Copyright 2004-2026 魏语石　教学综合信息服务平台　版本 V-9.0
    </footer>
  </div>
</template>
