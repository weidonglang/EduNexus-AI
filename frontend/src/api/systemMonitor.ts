import { http, type ApiResponse, type PageResponse } from './http'

export interface RedisKeyRow {
  key: string
  ttlSeconds: number
  value: string
}

export interface RedisMonitorResponse {
  reachable: boolean
  ping: string
  pattern: string
  dbSize: number
  displayedKeyCount: number
  elapsedMs: number
  keys: RedisKeyRow[]
  stockChecks: RedisStockCheckRow[]
  error?: string
}

export interface RedisStockCheckRow {
  offeringId: number
  key: string
  redisRemaining?: number | null
  databaseCapacity: number
  databaseSelected: number
  databaseRemaining: number
  oversold: boolean
}

export interface PrewarmStockResponse {
  count: number
  items: Array<{
    offeringId: number
    key: string
    remaining: number
  }>
}

export interface LoadTestReportRow {
  jsonName: string
  htmlName?: string
  startedAt: string
  modifiedAt: string
  requestCount: number
  successCount: number
  fullCount: number
  throughput: number
  avgLatency: number
  p95: number
  smartMode: string
  concurrency: number
  redisReachable: boolean
}

// 功能：查询 Redis 运行状态和缓存 key。
// 说明：管理端 Redis 监控页使用该接口展示 PING、key 数量、TTL、库存 key 和是否超卖。
export function redisMonitorApi(params?: { pattern?: string; limit?: number }) {
  return http.get<never, ApiResponse<RedisMonitorResponse>>('/admin/redis-monitor', { params })
}

// 功能：预热 Redis 教学班库存。
// 说明：后端根据数据库容量和已选人数生成 selection:offering:{offeringId}:remaining，
// 便于答辩时直接看到魏语石负责的真实库存 key。
export function prewarmRedisStockApi(limit = 20) {
  return http.post<never, ApiResponse<PrewarmStockResponse>>('/admin/redis-monitor/prewarm-stock', null, {
    params: { limit },
  })
}

// 功能：查询压测历史报告列表。
// 说明：读取 reports 目录中的 JSON/HTML 报告，展示吞吐量、平均延迟、P95 和 Redis 状态。
export function loadTestReportsApi(params?: { page?: number; size?: number }) {
  return http.get<never, ApiResponse<PageResponse<LoadTestReportRow>>>('/admin/load-test-reports', { params })
}

export function loadTestReportHtmlApi(fileName: string) {
  return http.get<never, string>(`/admin/load-test-reports/${encodeURIComponent(fileName)}/html`, {
    responseType: 'text',
  })
}
