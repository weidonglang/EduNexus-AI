<script setup lang="ts">
// 登录后的主布局组件。
// 学生、教师、管理员共用这一套布局：左侧菜单来自后端 /api/menus，顶部展示当前用户信息，
// 中间区域通过 RouterView 切换具体业务页面。
import { computed, onMounted, ref } from 'vue'
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
const searchKeyword = ref('')
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

const searchableRoutes = computed(() =>
  flattenMenus(menu.items)
    .filter((item) => item.path && item.path !== '/dashboard')
    .map((item) => ({
      value: item.title,
      title: item.title,
      path: item.path,
      keywords: routeKeywords(item),
    })),
)

async function logout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  await router.push('/login')
}

function hasChildren(item: MenuItem) {
  return item.children && item.children.length > 0
}

function flattenMenus(items: MenuItem[]): MenuItem[] {
  return items.flatMap((item) => (item.children?.length ? flattenMenus(item.children) : [item]))
}

function routeKeywords(item: MenuItem) {
  const aliases: Record<string, string[]> = {
    '/course/selection': ['课程', '选课', '抢课'],
    '/grade/query': ['成绩', '绩点', '分数'],
    '/exam/query': ['考试', '考场'],
    '/schedule/personal': ['课表', '日程'],
    '/evaluation': ['评价', '教学评价'],
    '/admin/users': ['用户', '账号', '学生', '教师'],
    '/admin/course-offerings': ['课程管理', '教学班', '开课'],
    '/admin/courses': ['课程管理', '课程'],
    '/admin/exams': ['考试管理', '考试安排'],
    '/admin/notices': ['通知', '公告'],
    '/admin/system-health': ['系统健康', '健康巡检'],
    '/teacher/offerings': ['教学班', '任课'],
    '/teacher/grades': ['成绩录入', '成绩'],
    '/teacher/exams': ['考试安排', '考试'],
    '/teacher/evaluations': ['评价统计', '评价'],
  }
  return [item.title, item.path, ...(aliases[item.path] ?? [])].join(' ').toLowerCase()
}

function querySearch(query: string, callback: (items: Array<{ value: string; title: string; path: string }>) => void) {
  const normalized = query.trim().toLowerCase()
  if (!normalized) {
    callback(searchableRoutes.value.slice(0, 8))
    return
  }
  const results = searchableRoutes.value
    .filter((item) => item.keywords.includes(normalized))
    .slice(0, 8)
  callback(results.length ? results : [{ value: '未找到相关功能', title: '未找到相关功能', path: '', keywords: '' }])
}

function selectSearchResult(item: { path: string }) {
  if (!item.path) return
  searchKeyword.value = ''
  router.push(item.path)
}

function submitSearch() {
  const normalized = searchKeyword.value.trim().toLowerCase()
  if (!normalized) return
  const match = searchableRoutes.value.find((item) => item.keywords.includes(normalized))
  if (!match) {
    ElMessage.warning('未找到相关功能')
    return
  }
  selectSearchResult(match)
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
      <el-autocomplete
        v-model="searchKeyword"
        class="zf-search"
        :fetch-suggestions="querySearch"
        placeholder="搜索课程、成绩、考试"
        clearable
        @select="selectSearchResult"
        @keyup.enter="submitSearch"
      >
        <template #prefix>
          <Search :size="15" />
        </template>
        <template #default="{ item }">
          <div class="search-suggestion">
            <span>{{ item.title }}</span>
            <small>{{ item.path }}</small>
          </div>
        </template>
      </el-autocomplete>
    </nav>

    <main class="zf-content">
      <RouterView />
    </main>

    <footer class="zf-footer">
      版权所有 Copyright 2004-2026 魏语石　教学综合信息服务平台　版本 V-9.0
    </footer>
  </div>
</template>
