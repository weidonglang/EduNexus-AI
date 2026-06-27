import { http, type ApiResponse, type PageResponse } from './http'
import type { AiModelRecord } from './aiModel'

const AI_REQUEST_TIMEOUT_MS = 90000
const AI_LONG_REQUEST_TIMEOUT_MS = 180000

export interface AiSourceDocument {
  id: string
  title: string
  type: string
  content: string
  score: number
}

export interface AiAssistantResponse {
  answer: string
  sources: AiSourceDocument[]
  serviceMode: string
  answerType: string
  refusalReason?: string
}

export interface AiChatResponse {
  answer: string
  serviceMode: string
  modelName: string
  searchUsed: boolean
  searchSources: Array<{ title: string; link: string; summary: string; searchedAt: string }>
  searchMessage: string
}

export interface AiServiceStatusResponse {
  aiServiceOnline: boolean
  ollamaEnabled: boolean
  ollamaReachable: boolean
  chatModel: string
  sqlModel: string
  currentMode: string
  lastLatencyMs: number
  lastError?: string
  serviceName: string
  discoveryEnabled: boolean
  baseUrl: string
  defaultChatModel: string
  defaultRagModel: string
  defaultSqlModel: string
  searchEnabled: boolean
  searchProvider: string
  searchStatus?: string
  checkedAt: string
}

export interface SqlColumnInfo {
  columnName: string
  dataType: string
}

export interface SqlTableSchema {
  tableName: string
  columns: SqlColumnInfo[]
}

export interface NaturalSqlGenerateResponse {
  sql: string
  explanation: string
  warnings: string[]
  allowedTables: string[]
  serviceMode: string
}

export interface NaturalSqlExecuteResponse {
  sql: string
  columns: string[]
  rows: Record<string, unknown>[]
  rowCount: number
  warnings: string[]
}

export interface AcademicProfileResponse {
  studentNo: string
  studentName: string
  college: string
  major: string
  className: string
  grade: string
  status: string
  earnedCredits: number
  plannedCredits: number
  remainingCredits: number
  failedCourseCount: number
  retakeCourseCount: number
  directionStatus: string
  graduationRiskLevel: string
  aiSuggestion: string
  failedCourses: Array<{ courseCode: string; courseName: string; credit: number; score: number; term: string }>
  progress: Array<{ courseType: string; courseCount: number; totalCredits: number; passedCredits: number; averageScore: number }>
  graduationAudits: Array<{ auditItem: string; requiredValue: string; currentValue: string; passed: boolean; remark: string }>
  serviceMode: string
}

export interface LoadTestAnalysisResponse {
  conclusion: string
  bottlenecks: string[]
  suggestions: string[]
  riskLevel: string
  serviceMode: string
}

export interface AiCallLogRow {
  id: number
  username: string
  roleCodes: string
  functionType: string
  promptSummary: string
  modelName: string
  serviceMode?: string
  durationMs: number
  success: boolean
  level: 'INFO' | 'WARN' | 'ERROR'
  errorMessage?: string
  traceId?: string
  sessionId?: number
  modelId?: number
  createdAt: string
}

export function askAiAssistantApi(question: string) {
  return http.post<never, ApiResponse<AiAssistantResponse>>('/ai/assistant/ask', { question }, { timeout: AI_REQUEST_TIMEOUT_MS })
}

export interface AiChatSession {
  id: number
  title: string
  modelId?: number
  modelName?: string
  createdAt: string
  updatedAt: string
}

export interface AiChatMessage {
  id: number
  sessionId: number
  role: 'user' | 'assistant'
  content: string
  serviceMode?: string
  modelName?: string
  searchUsed: boolean
  createdAt: string
}

export interface AiChatSendResponse {
  response: AiChatResponse
  messages: AiChatMessage[]
  session: AiChatSession
}

export function aiChatApi(message: string, modelId?: number) {
  return http.post<never, ApiResponse<AiChatResponse>>('/ai/chat', { message, modelId }, { timeout: AI_REQUEST_TIMEOUT_MS })
}

export function aiStatusApi() {
  return http.get<never, ApiResponse<AiServiceStatusResponse>>('/ai/status')
}

export function academicProfileApi() {
  return http.get<never, ApiResponse<AcademicProfileResponse>>('/ai/academic-profile')
}

export function aiSqlSchemaApi() {
  return http.get<never, ApiResponse<SqlTableSchema[]>>('/admin/ai/sql/schema')
}

export function generateNaturalSqlApi(question: string) {
  return http.post<never, ApiResponse<NaturalSqlGenerateResponse>>('/admin/ai/sql/generate', { question }, { timeout: AI_REQUEST_TIMEOUT_MS })
}

export function executeNaturalSqlApi(sql: string) {
  return http.post<never, ApiResponse<NaturalSqlExecuteResponse>>('/admin/ai/sql/execute', { sql })
}

export function analyzeLoadTestReportApi(jsonName: string) {
  return http.post<never, ApiResponse<LoadTestAnalysisResponse>>(
    '/admin/ai/load-test/analyze',
    { jsonName },
    { timeout: AI_LONG_REQUEST_TIMEOUT_MS },
  )
}

export function aiChatModelsApi() {
  return http.get<never, ApiResponse<AiModelRecord[]>>('/ai/chat/models')
}

export function aiChatSessionsApi() {
  return http.get<never, ApiResponse<AiChatSession[]>>('/ai/chat/sessions')
}

export function createAiChatSessionApi(payload: { title?: string; modelId?: number }) {
  return http.post<never, ApiResponse<AiChatSession>>('/ai/chat/sessions', payload)
}

export function updateAiChatSessionApi(sessionId: number, payload: { title?: string; modelId?: number }) {
  return http.put<never, ApiResponse<AiChatSession>>(`/ai/chat/sessions/${sessionId}`, payload)
}

export function deleteAiChatSessionApi(sessionId: number) {
  return http.delete<never, ApiResponse<void>>(`/ai/chat/sessions/${sessionId}`)
}

export function aiChatMessagesApi(sessionId: number) {
  return http.get<never, ApiResponse<AiChatMessage[]>>(`/ai/chat/sessions/${sessionId}/messages`)
}

export function sendAiChatMessageApi(sessionId: number, payload: { message: string; modelId?: number }) {
  return http.post<never, ApiResponse<AiChatSendResponse>>(`/ai/chat/sessions/${sessionId}/messages`, payload, {
    timeout: AI_REQUEST_TIMEOUT_MS,
  })
}

export function aiCallLogsApi(params?: {
  keyword?: string
  username?: string
  functionType?: string
  success?: boolean | ''
  level?: string
  startAt?: string
  endAt?: string
  page?: number
  size?: number
}) {
  return http.get<never, ApiResponse<PageResponse<AiCallLogRow>>>('/admin/ai/call-logs', { params })
}
