import { http, type ApiResponse } from './http'

export interface PageResponse<T> {
  records: T[]
  page: number
  size: number
  total: number
}

export interface DatabaseTableInfo {
  tableName: string
  module: string
  comment: string
  rowCount: number
  createTime?: string
  updateTime?: string
}

export interface DatabaseConnectionInfo {
  name: string
  host: string
  port: number
  databaseName: string
  type: string
  remark: string
}

export interface DatabaseColumnInfo {
  columnName: string
  columnType: string
  dataType: string
  nullable: string
  columnKey: string
  defaultValue?: string
  extra: string
  comment: string
  ordinalPosition: number
}

export interface DatabaseIndexInfo {
  indexName: string
  columnName: string
  uniqueIndex: boolean
  sequence: number
  indexType: string
}

export interface DatabaseForeignKeyInfo {
  constraintName: string
  columnName: string
  referencedTableName: string
  referencedColumnName: string
  updateRule: string
  deleteRule: string
}

export type DatabasePreviewRow = Record<string, unknown>

export interface DatabaseTree {
  databaseName: string
  tables: Array<{
    tableName: string
    module: string
    columns: Array<{ columnName: string; columnType: string; primaryKey: boolean }>
    indexes: Array<{ indexName: string; columnName: string; uniqueIndex: boolean }>
  }>
  views: string[]
}

export interface DatabaseErGraph {
  nodes: Array<{
    tableName: string
    module: string
    columns: Array<{ columnName: string; columnType: string; primaryKey: boolean; foreignKey: boolean }>
  }>
  relations: Array<{ sourceTable: string; sourceColumn: string; targetTable: string; targetColumn: string; label: string }>
}

export interface DatabaseHistoryRow {
  id: number
  operator: string
  action: string
  targetType: string
  targetId?: string
  detail?: string
  createdAt: string
}

export interface DatabaseNameValue {
  name: string
  value: number
}

export interface DatabaseDashboardData {
  stats: {
    tableCount: number
    fieldCount: number
    indexCount: number
    foreignKeyCount: number
    totalRows: number
    recentSqlCount: number
    sqlSuccessRate: number
  }
  tableRows: DatabaseNameValue[]
  fieldTypes: DatabaseNameValue[]
  sqlTrend: DatabaseNameValue[]
  sqlStatus: DatabaseNameValue[]
  actionRanking: DatabaseNameValue[]
  importQuality: DatabaseNameValue[]
}

export interface DatabaseQueryTemplate {
  code: string
  title: string
  module: string
  parameters: string[]
}

export function databaseConnectionApi() {
  return http.get<never, ApiResponse<DatabaseConnectionInfo>>('/admin/database-browser/connection')
}

export function databaseDashboardApi() {
  return http.get<never, ApiResponse<DatabaseDashboardData>>('/admin/database-browser/dashboard')
}

export function databaseTablesApi(params?: { module?: string; keyword?: string }) {
  return http.get<never, ApiResponse<DatabaseTableInfo[]>>('/admin/database-browser/tables', { params })
}

export function databaseTreeApi(params?: { keyword?: string }) {
  return http.get<never, ApiResponse<DatabaseTree>>('/admin/database-browser/tree', { params })
}

export function databaseColumnsApi(tableName: string) {
  return http.get<never, ApiResponse<DatabaseColumnInfo[]>>(`/admin/database-browser/tables/${tableName}/columns`)
}

export function databaseIndexesApi(tableName: string) {
  return http.get<never, ApiResponse<DatabaseIndexInfo[]>>(`/admin/database-browser/tables/${tableName}/indexes`)
}

export function databaseForeignKeysApi(tableName: string) {
  return http.get<never, ApiResponse<DatabaseForeignKeyInfo[]>>(`/admin/database-browser/tables/${tableName}/foreign-keys`)
}

export function databasePreviewApi(tableName: string, params?: { page?: number; size?: number; keyword?: string; sortBy?: string; sortDir?: string }) {
  return http.get<never, ApiResponse<PageResponse<DatabasePreviewRow>>>(
    `/admin/database-browser/tables/${tableName}/preview`,
    { params },
  )
}

export function databaseErGraphApi() {
  return http.get<never, ApiResponse<DatabaseErGraph>>('/admin/database-browser/er')
}

export function databaseHistoryApi(params?: { page?: number; size?: number }) {
  return http.get<never, ApiResponse<PageResponse<DatabaseHistoryRow>>>('/admin/database-browser/history', { params })
}

export function databaseExportCsvUrl(tableName: string, params?: { keyword?: string; sortBy?: string; sortDir?: string }) {
  const search = new URLSearchParams()
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value) search.set(key, value)
  })
  return `/api/admin/database-browser/tables/${encodeURIComponent(tableName)}/export.csv${search.size ? `?${search}` : ''}`
}

export function databaseTemplatesApi() {
  return http.get<never, ApiResponse<DatabaseQueryTemplate[]>>('/admin/database-browser/templates')
}

export function databaseRunTemplateApi(templateCode: string, params?: { keyword?: string; term?: string; page?: number; size?: number }) {
  return http.get<never, ApiResponse<PageResponse<DatabasePreviewRow>>>(
    `/admin/database-browser/templates/${templateCode}/run`,
    { params },
  )
}

export function databaseTemplateExportCsvUrl(templateCode: string, params?: { keyword?: string; term?: string }) {
  const search = new URLSearchParams()
  Object.entries(params ?? {}).forEach(([key, value]) => {
    if (value) search.set(key, value)
  })
  return `/api/admin/database-browser/templates/${encodeURIComponent(templateCode)}/export.csv${search.size ? `?${search}` : ''}`
}
