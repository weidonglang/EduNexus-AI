import { http, type ApiResponse } from './http'
import type { PageResponse } from './notice'

export interface AuditLog {
  id: number
  operator: string
  action: string
  targetType: string
  targetId?: string
  detail?: string
  traceId?: string
  module?: string
  riskLevel?: 'LOW' | 'MEDIUM' | 'HIGH'
  successFlag?: boolean
  failureReason?: string
  createdAt: string
}

export function auditLogsApi(params?: { keyword?: string; riskLevel?: string; module?: string; page?: number; size?: number }) {
  return http.get<never, ApiResponse<PageResponse<AuditLog>>>('/admin/audit-logs', { params })
}
