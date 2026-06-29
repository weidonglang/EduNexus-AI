<script setup lang="ts">
// 系统首页仪表盘。
// 进入系统后根据当前用户角色展示课程、通知、公告、课表和快捷入口。
// 学生会额外加载个人课表，管理员和教师则更多展示管理/教学相关入口。
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Bell,
  BookOpen,
  CalendarClock,
  ClipboardList,
  FilePenLine,
  GraduationCap,
  Megaphone,
  Settings,
  UserRound,
} from 'lucide-vue-next'
import { fetchDashboardOverviewApi, type DashboardOverview } from '@/api/dashboard'
import { homeNoticesApi, markNotificationReadApi, myNotificationsApi, type Notice, type Notification } from '@/api/notice'
import { personalScheduleApi, type ScheduleEntry } from '@/api/schedule'
import { NexusMetricCard } from '@/components/nexus'
import { useAuthStore } from '@/stores/auth'
import { useMenuStore } from '@/stores/menu'
import type { MenuItem } from '@/api/menu'

const router = useRouter()
const auth = useAuthStore()
const menu = useMenuStore()
const loading = ref(false)
const overview = ref<DashboardOverview>({
  roleView: 'STUDENT',
  term: '',
  scopeLabel: '个人',
  cards: [],
  courseCount: 0,
  pendingEvaluationCount: 0,
  examCount: 0,
  earnedCredits: 0,
  recentEvents: [],
})
const notices = ref<Notice[]>([])
const notifications = ref<Notification[]>([])
const schedules = ref<ScheduleEntry[]>([])
const quickAppSettingsVisible = ref(false)
const selectedQuickAppCodes = ref<string[]>([])
const quickAppRevision = ref(0)
const isStudent = computed(() => auth.user?.roles.includes('STUDENT') ?? false)
const isAdmin = computed(() => auth.user?.roles.includes('ADMIN') ?? false)
const isTeacher = computed(() => auth.user?.roles.includes('TEACHER') ?? false)

const days = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
]
const slots = ['1-2', '3-4', '5-6', '7-8']

const roleStatCards = computed(() => [
  ...(overview.value.cards.length
    ? overview.value.cards
    : [
        { key: 'courses', label: '本学期课程', value: overview.value.courseCount, suffix: '门', scope: '当前用户' },
        { key: 'pending', label: '待完成评价', value: overview.value.pendingEvaluationCount, suffix: '项', scope: '当前用户' },
        { key: 'exams', label: '考试安排', value: overview.value.examCount, suffix: '场', scope: '当前用户' },
        { key: 'credits', label: '已获学分', value: overview.value.earnedCredits, suffix: '分', scope: '当前用户' },
      ]),
])

const quickAppStorageKey = computed(() => `academic-nexus:quick-apps:${auth.user?.username ?? 'anonymous'}`)

const allQuickAppOptions = computed(() => flattenMenus(menu.items).filter((item) => item.path !== '/dashboard'))

const defaultQuickApps = computed(() => {
  const leafs = flattenMenus(menu.items).filter((item) => item.path !== '/dashboard')
  const priority = [
    '/student/profile',
    '/course/selection',
    '/schedule/personal',
    '/information/weekly-schedule',
    '/grade/query',
    '/exam/query',
    '/evaluation',
    '/information/feedback',
  ]
  return leafs
    .sort((a, b) => priorityScore(a.path, priority) - priorityScore(b.path, priority))
    .slice(0, 8)
})

const quickApps = computed(() => {
  quickAppRevision.value
  const storedCodes = loadQuickAppCodes()
  if (!storedCodes.length) return defaultQuickApps.value
  const byCode = new Map(allQuickAppOptions.value.map((item) => [item.code, item]))
  return storedCodes.map((code) => byCode.get(code)).filter((item): item is MenuItem => Boolean(item)).slice(0, 8)
})

const userRoleText = computed(() => {
  const roles = auth.user?.roles ?? []
  if (roles.includes('ADMIN')) return '管理员'
  if (roles.includes('TEACHER')) return '教师'
  return '学生'
})

onMounted(async () => {
  loading.value = true
  try {
    await menu.loadMenus()
    const [dashboard, noticeResponse, notificationResponse] = await Promise.all([
      fetchDashboardOverviewApi(),
      homeNoticesApi({ page: 1, size: 6 }),
      myNotificationsApi({ read: false, page: 1, size: 6 }),
    ])
    overview.value = dashboard
    notices.value = noticeResponse.data.records
    notifications.value = notificationResponse.data.records
    schedules.value = isStudent.value ? (await personalScheduleApi()).data : []
    selectedQuickAppCodes.value = quickApps.value.map((item) => item.code)
  } finally {
    loading.value = false
  }
})

async function markRead(row: Notification) {
  await markNotificationReadApi(row.id)
  notifications.value = notifications.value.filter((item) => item.id !== row.id)
}

function go(path: string) {
  router.push(path)
}

function coursesAt(dayOfWeek: number, slot: string) {
  return schedules.value.filter((item) => item.dayOfWeek === dayOfWeek && item.slot === slot)
}

function flattenMenus(items: MenuItem[]): MenuItem[] {
  return items.flatMap((item) => (item.children?.length ? flattenMenus(item.children) : [item]))
}

function priorityScore(path: string, priority: string[]) {
  const index = priority.indexOf(path)
  return index === -1 ? 1000 : index
}

function loadQuickAppCodes() {
  try {
    const raw = localStorage.getItem(quickAppStorageKey.value)
    const parsed = raw ? JSON.parse(raw) : []
    return Array.isArray(parsed) ? parsed.filter((item) => typeof item === 'string') : []
  } catch {
    return []
  }
}

function openQuickAppSettings() {
  selectedQuickAppCodes.value = quickApps.value.map((item) => item.code)
  quickAppSettingsVisible.value = true
}

function saveQuickAppSettings() {
  const codes = selectedQuickAppCodes.value.slice(0, 8)
  localStorage.setItem(quickAppStorageKey.value, JSON.stringify(codes))
  selectedQuickAppCodes.value = codes
  quickAppRevision.value += 1
  quickAppSettingsVisible.value = false
  ElMessage.success('快捷应用已保存')
}

function resetQuickAppSettings() {
  localStorage.removeItem(quickAppStorageKey.value)
  selectedQuickAppCodes.value = defaultQuickApps.value.map((item) => item.code)
  quickAppRevision.value += 1
  ElMessage.success('已恢复默认快捷应用')
}

function handleQuickAppCheck(value: string[]) {
  if (value.length > 8) {
    selectedQuickAppCodes.value = value.slice(0, 8)
    ElMessage.warning('最多选择 8 个快捷应用')
  }
}
</script>

<template>
  <section class="portal-home" v-loading="loading">
    <aside class="portal-apps">
      <div class="portal-panel-title">
        <h2>我的应用</h2>
        <el-button text circle @click="openQuickAppSettings">
          <Settings :size="18" />
        </el-button>
      </div>
      <div class="app-list">
        <button v-for="app in quickApps" :key="app.code" class="app-link" type="button" @click="go(app.path)">
          <BookOpen v-if="app.path.includes('course')" :size="18" />
          <CalendarClock v-else-if="app.path.includes('schedule') || app.path.includes('exam')" :size="18" />
          <ClipboardList v-else-if="app.path.includes('grade')" :size="18" />
          <FilePenLine v-else-if="app.path.includes('status') || app.path.includes('registration')" :size="18" />
          <Megaphone v-else-if="app.path.includes('notice')" :size="18" />
          <UserRound v-else :size="18" />
          <span>{{ app.title }}</span>
        </button>
      </div>
    </aside>

    <section class="portal-main-grid">
      <article class="profile-strip">
        <div class="avatar-circle">
          <UserRound :size="58" />
        </div>
        <div>
          <h1>{{ auth.user?.displayName ?? '学生用户' }} <span>{{ userRoleText }}</span></h1>
          <p>{{ auth.user?.username ?? '未登录用户' }}　信息科学与工程学院 2023级软件工程</p>
        </div>
      </article>

      <section class="summary-strip">
        <NexusMetricCard
          v-for="(item, index) in roleStatCards"
          :key="item.label"
          :label="item.label"
          :value="item.value"
          :suffix="item.suffix"
          :hint="item.scope"
          :tone="index === 1 ? 'warning' : index === 2 ? 'info' : index === 3 ? 'success' : 'primary'"
        />
      </section>

      <article class="portal-card wide">
        <div class="portal-panel-title">
          <h2>本周课表</h2>
          <CalendarClock :size="18" />
        </div>
        <div class="home-schedule">
          <div class="home-schedule-head">节次</div>
          <div v-for="day in days" :key="day.value" class="home-schedule-head">{{ day.label }}</div>
          <template v-for="slot in slots" :key="slot">
            <div class="home-schedule-slot">{{ slot }}节</div>
            <div v-for="day in days" :key="`${day.value}-${slot}`" class="home-schedule-cell">
              <article v-for="course in coursesAt(day.value, slot)" :key="`${course.courseCode}-${course.classroom}`">
                <strong>{{ course.courseName }}</strong>
                <span>{{ course.teacherName }} · {{ course.classroom }}</span>
              </article>
            </div>
          </template>
        </div>
      </article>

      <article class="portal-card">
        <div class="portal-panel-title">
          <h2>首页公告</h2>
          <Megaphone :size="18" />
        </div>
        <div class="portal-list">
          <button v-for="notice in notices" :key="notice.id" type="button" class="portal-list-row">
            <span>{{ notice.title }}</span>
            <small>{{ notice.category }}</small>
          </button>
          <el-empty v-if="!notices.length" description="暂无公告" :image-size="70" />
        </div>
      </article>

      <article class="portal-card">
        <div class="portal-panel-title">
          <h2>未读通知</h2>
          <Bell :size="18" />
        </div>
        <div class="portal-list">
          <button v-for="item in notifications" :key="item.id" type="button" class="portal-list-row action-row">
            <span>{{ item.title }}</span>
            <el-button type="primary" link @click.stop="markRead(item)">已读</el-button>
          </button>
          <el-empty v-if="!notifications.length" description="暂无未读通知" :image-size="70" />
        </div>
      </article>

      <article class="portal-card wide">
        <div class="portal-panel-title">
          <h2>近期事项</h2>
          <GraduationCap :size="18" />
        </div>
        <el-table :data="overview.recentEvents" empty-text="暂无近期事项">
          <el-table-column prop="type" label="类型" width="120" />
          <el-table-column prop="title" label="事项" />
          <el-table-column prop="eventTime" label="日期" width="180" />
        </el-table>
      </article>
    </section>

    <el-dialog v-model="quickAppSettingsVisible" title="我的应用设置" width="520px">
      <el-checkbox-group v-model="selectedQuickAppCodes" class="quick-app-settings" @change="handleQuickAppCheck">
        <el-checkbox
          v-for="app in allQuickAppOptions"
          :key="app.code"
          :label="app.code"
          :disabled="selectedQuickAppCodes.length >= 8 && !selectedQuickAppCodes.includes(app.code)"
        >
          {{ app.title }}
          <small>{{ app.path }}</small>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="resetQuickAppSettings">恢复默认</el-button>
        <el-button @click="quickAppSettingsVisible = false">取消</el-button>
        <el-button type="primary" @click="saveQuickAppSettings">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.quick-app-settings {
  display: grid;
  gap: 10px;
  max-height: 420px;
  overflow-y: auto;
}

.quick-app-settings small {
  margin-left: 8px;
  color: #6b7280;
}
</style>
