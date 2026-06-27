import { defineStore } from 'pinia'
import { menusApi, type MenuItem } from '@/api/menu'
import { useAuthStore } from '@/stores/auth'

interface MenuState {
  items: MenuItem[]
  loaded: boolean
  loading: boolean
  error: string
}

export const useMenuStore = defineStore('menu', {
  state: (): MenuState => ({
    items: [],
    loaded: false,
    loading: false,
    error: '',
  }),
  actions: {
    // 功能：根据后端返回的菜单权限动态生成侧边栏菜单。
    // 说明：用户登录后调用 /api/menus 获取角色对应菜单；如果菜单接口暂时不可用，
    // 则根据用户角色使用本地演示菜单兜底，保证答辩时页面可继续展示。
    async loadMenus(force = false) {
      if (this.loaded && !force) return
      this.loading = true
      this.error = ''
      try {
        const response = await menusApi()
        this.items = response.data
        this.loaded = true
      } catch {
        const auth = useAuthStore()
        this.items = fallbackMenus(auth.user?.roles ?? [])
        this.loaded = true
        this.error = this.items.length ? '菜单接口暂时不可用，已使用本地演示菜单' : '菜单加载失败，请重新登录'
      } finally {
        this.loading = false
      }
    },
    // 功能：清空菜单状态。
    // 说明：退出登录或切换用户时调用，避免下一个用户看到上一个用户的菜单。
    reset() {
      this.items = []
      this.loaded = false
      this.loading = false
      this.error = ''
    },
  },
})

// 功能：菜单接口异常时生成本地兜底菜单。
// 说明：兜底菜单只影响前端展示，真正的接口权限仍由后端 Spring Security 和权限注解控制。
function fallbackMenus(roles: string[]): MenuItem[] {
  const normalized = new Set(roles)
  const common: MenuItem[] = [
    item('dashboard', '首页', '/dashboard', 'LayoutDashboard'),
    item('ai-assistant', '智能教务助手', '/ai/assistant', 'Bot'),
    item('ai-chat', 'AI 聊天', '/ai/chat', 'MessagesSquare'),
  ]
  const student: MenuItem[] = [
    tree('student', '学生信息', '/student', 'UserRound', [
      item('student-profile', '个人信息', '/student/profile', 'IdCard'),
      item('student-class', '我的班级', '/student/class', 'UsersRound'),
      item('student-status-change', '学籍异动申请', '/student/status-change', 'FilePenLine'),
    ]),
    tree('registration', '报名申请', '/registration', 'FilePenLine', [
      item('registration-minor', '微专业报名', '/registration/minor', 'BadgePlus'),
      item('registration-retake', '重修报名', '/registration/retake', 'RefreshCw'),
      item('registration-credit-internal', '校内学分节点替代申请', '/registration/credit-internal', 'ArrowLeftRight'),
      item('registration-credit-external', '校外课程学分节点替代申请', '/registration/credit-external', 'ArrowLeftRight'),
      item('registration-score-bonus', '成绩加分申请', '/registration/score-bonus', 'CirclePlus'),
      item('registration-stream-confirm', '分流专业确认', '/registration/stream-confirm', 'CheckCheck'),
      item('registration-direction-confirm', '专业方向确认', '/registration/direction-confirm', 'CheckCheck'),
    ]),
    tree('course', '选课课表', '/course', 'CalendarDays', [
      item('course-selection', '自主选课', '/course/selection', 'ListChecks'),
      item('schedule-personal', '个人课表', '/schedule/personal', 'Calendar'),
      item('classroom-free', '空闲教室', '/classroom/free', 'School'),
    ]),
    tree('info-query', '信息查询', '/information', 'Search', [
      item('info-warning', '学籍预警查询', '/information/academic-warning', 'TriangleAlert'),
      item('info-graduation-audit', '毕业审核结果核查', '/information/graduation-audit', 'BadgeCheck'),
      item('info-class-schedule', '班级课表查询', '/information/class-schedule', 'CalendarRange'),
      item('info-roster', '选课名单查询', '/information/course-roster', 'ListChecks'),
      item('info-academic-progress', '学业情况查询', '/information/academic-progress', 'GraduationCap'),
      item('ai-academic-profile', '学业画像', '/ai/academic-profile', 'Radar'),
      item('info-teaching-plan', '教学执行计划', '/information/teaching-plan', 'BookOpenText'),
      item('teaching-feedback', '教学信息反馈', '/information/feedback', 'MessageCircle'),
    ]),
    tree('grade', '成绩考试', '/grade', 'ClipboardList', [
      item('grade-query', '成绩查询', '/grade/query', 'ChartNoAxesColumn'),
      item('exam-query', '考试安排', '/exam/query', 'NotebookTabs'),
    ]),
    item('evaluation', '教学评价', '/evaluation', 'MessageSquareText'),
    tree('graduation-design', '毕设论文', '/graduation-design', 'FileText', [
      item('thesis-grade', '论文成绩查看', '/information/thesis-grade', 'Award'),
    ]),
  ]
  const teacher: MenuItem[] = [
    tree('teacher', '教师工作台', '/teacher', 'Presentation', [
      item('teacher-offerings', '任课课程', '/teacher/courses', 'BookOpen'),
      item('teacher-homeroom-classes', '班主任班级', '/teacher/classes', 'UsersRound'),
      item('teacher-grades', '成绩录入', '/teacher/grades', 'SquarePen'),
      item('teacher-exams', '考试安排', '/teacher/exams', 'CalendarClock'),
      item('teacher-evaluations', '评价结果', '/teacher/evaluations', 'ChartColumn'),
    ]),
  ]
  const admin: MenuItem[] = [
    tree('admin', '教务管理', '/admin', 'Settings', [
      item('admin-classes', '班级与学生', '/admin/classes', 'UsersRound'),
      item('admin-course-offerings', '课程与教学班', '/admin/course-offerings', 'BookOpenCheck'),
      item('admin-status-changes', '学籍异动审核', '/admin/status-changes', 'FileCheck2'),
      item('admin-registration-applications', '报名申请审核', '/admin/registration-applications', 'FileCheck2'),
      item('admin-role-permissions', '角色权限管理', '/admin/role-permissions', 'ShieldCheck'),
      item('admin-users', '用户与角色', '/admin/users', 'UsersRound'),
      item('admin-evaluations', '教学评价统计', '/admin/evaluations', 'ChartColumn'),
      item('admin-grades', '成绩管理', '/admin/grades', 'FileSpreadsheet'),
      item('admin-exams', '考试管理', '/admin/exams', 'ClipboardCheck'),
      item('admin-notices', '通知公告', '/admin/notices', 'Megaphone'),
      item('admin-files', '文件管理', '/admin/files', 'FolderOpen'),
      item('admin-audit-logs', '操作审计', '/admin/audit-logs', 'ScrollText'),
      item('admin-redis-monitor', 'Redis状态监控', '/admin/redis-monitor', 'DatabaseZap'),
      item('admin-load-test-reports', '压测历史报告', '/admin/load-test-reports', 'ChartColumnBig'),
      item('admin-database-browser', '数据库只读浏览', '/admin/database-browser', 'TableProperties'),
      item('admin-ai-sql', '自然语言查库', '/admin/ai-sql', 'Sparkles'),
      item('admin-ai-logs', 'AI调用日志', '/admin/ai-logs', 'ScrollText'),
    ]),
  ]

  if (normalized.has('ADMIN') && normalized.has('TEACHER') && normalized.has('STUDENT')) {
    return [...common, ...student, ...teacher, ...admin]
  }
  if (normalized.has('ADMIN')) return [...common, ...admin]
  if (normalized.has('TEACHER')) return [...common, ...teacher]
  if (normalized.has('STUDENT')) return [...common, ...student]
  return common
}

function item(code: string, title: string, path: string, icon: string): MenuItem {
  return { code, title, path, icon, children: [] }
}

function tree(code: string, title: string, path: string, icon: string, children: MenuItem[]): MenuItem {
  return { code, title, path, icon, children }
}
