import { http, type ApiResponse } from '@/api/http'

// 首页仪表盘 API 封装。
// 登录后 Dashboard 页面会调用该接口获取课程、评价、考试、学分和近期事件摘要。
export interface DashboardEvent {
  type: string
  title: string
  eventTime: string
}

export interface DashboardOverview {
  roleView: 'STUDENT' | 'TEACHER' | 'ADMIN'
  term: string
  scopeLabel: string
  cards: DashboardCard[]
  courseCount: number
  pendingEvaluationCount: number
  examCount: number
  earnedCredits: number
  recentEvents: DashboardEvent[]
}

export interface DashboardCard {
  key: string
  label: string
  value: number
  suffix: string
  scope: string
}

export async function fetchDashboardOverviewApi() {
  const response = await http.get<never, ApiResponse<DashboardOverview>>('/dashboard/me')
  return response.data
}
