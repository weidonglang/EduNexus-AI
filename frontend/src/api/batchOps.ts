import { http, type ApiResponse } from './http'

export interface PageResponse<T> {
  records: T[]
  page: number
  size: number
  total: number
}

export interface BatchTaskRow {
  id: number
  taskType: string
  operator: string
  startedAt: string
  endedAt?: string
  status: 'PENDING' | 'RUNNING' | 'PARTIAL_SUCCESS' | 'SUCCESS' | 'FAILED'
  successCount: number
  failureCount: number
  failureDetail?: string
  reportPath?: string
}

export interface ArchivePreview {
  objectType: string
  term?: string
  affectedCount: number
  dryRun: boolean
  message: string
}

export interface ArchiveRecordRow {
  id: number
  objectType: string
  term?: string
  action: string
  dryRun: boolean
  affectedCount: number
  operator: string
  detail?: string
  createdAt: string
}

export function batchTasksApi(params?: { taskType?: string; status?: string; page?: number; size?: number }) {
  return http.get<never, ApiResponse<PageResponse<BatchTaskRow>>>('/admin/batch-tasks', { params })
}

export function dataArchivePreviewApi(params: { objectType: string; term?: string }) {
  return http.get<never, ApiResponse<ArchivePreview>>('/admin/data-archive/preview', { params })
}

export function dataArchiveArchiveApi(params: { objectType: string; term?: string; dryRun?: boolean }) {
  return http.post<never, ApiResponse<ArchiveRecordRow>>('/admin/data-archive/archive', null, { params })
}

export function dataArchiveCleanupApi(params: { objectType: string; term?: string; dryRun?: boolean }) {
  return http.post<never, ApiResponse<ArchiveRecordRow>>('/admin/data-archive/cleanup', null, { params })
}

export function dataArchiveExportCsvUrl() {
  return '/api/admin/data-archive/export.csv'
}
