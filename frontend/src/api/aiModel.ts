import { http, type ApiResponse } from './http'

export interface AiModelRecord {
  id: number
  name: string
  provider: string
  modelName: string
  baseUrl: string
  apiKeyRef: string
  modelType: string
  purpose: string
  enabled: boolean
  defaultModel: boolean
  description: string
  lastStatus?: string
  lastLatencyMs?: number
  lastError?: string
  lastCheckedAt?: string
  createdAt: string
  updatedAt: string
}

export interface AiModelRequest {
  name: string
  provider: string
  modelName: string
  baseUrl: string
  apiKeyRef: string
  modelType: string
  purpose: string
  enabled: boolean
  defaultModel: boolean
  description: string
}

export interface ModelTestResponse {
  success: boolean
  status: string
  latencyMs?: number
  message: string
}

export interface SearchConfig {
  id: number
  enabled: boolean
  provider: string
  baseUrl: string
  apiKeyEnv: string
  allowedScenes: string
  safetyPolicy: string
  lastStatus?: string
  lastLatencyMs?: number
  lastError?: string
  lastTestedAt?: string
  updatedAt: string
}

export interface SearchResult {
  title: string
  link: string
  summary: string
  searchedAt: string
}

export interface SearchTestResponse {
  allowed: boolean
  searchUsed: boolean
  provider: string
  message: string
  results: SearchResult[]
  latencyMs?: number
}

export interface AiSafetyConfig {
  id: number
  scene: string
  enabled: boolean
  strategy: string
  description?: string
  updatedAt: string
}

export function aiModelsApi() {
  return http.get<never, ApiResponse<AiModelRecord[]>>('/admin/ai/models')
}

export function createAiModelApi(payload: AiModelRequest) {
  return http.post<never, ApiResponse<AiModelRecord>>('/admin/ai/models', payload)
}

export function updateAiModelApi(id: number, payload: AiModelRequest) {
  return http.put<never, ApiResponse<AiModelRecord>>(`/admin/ai/models/${id}`, payload)
}

export function enableAiModelApi(id: number) {
  return http.post<never, ApiResponse<void>>(`/admin/ai/models/${id}/enable`)
}

export function disableAiModelApi(id: number) {
  return http.post<never, ApiResponse<void>>(`/admin/ai/models/${id}/disable`)
}

export function setDefaultAiModelApi(id: number) {
  return http.post<never, ApiResponse<void>>(`/admin/ai/models/${id}/set-default`)
}

export function testAiModelApi(id: number) {
  return http.post<never, ApiResponse<ModelTestResponse>>(`/admin/ai/models/${id}/test`)
}

export function aiSearchConfigApi() {
  return http.get<never, ApiResponse<SearchConfig>>('/admin/ai/search/config')
}

export function updateAiSearchConfigApi(payload: Omit<SearchConfig, 'id' | 'lastStatus' | 'lastLatencyMs' | 'lastError' | 'lastTestedAt' | 'updatedAt'>) {
  return http.put<never, ApiResponse<SearchConfig>>('/admin/ai/search/config', payload)
}

export function testAiSearchApi(query: string, scene = 'ADMIN_TEST') {
  return http.post<never, ApiResponse<SearchTestResponse>>('/admin/ai/search/test', { query, scene })
}

export function aiSafetyConfigsApi() {
  return http.get<never, ApiResponse<AiSafetyConfig[]>>('/admin/ai/safety/config')
}

export function updateAiSafetyConfigsApi(configs: Array<Pick<AiSafetyConfig, 'scene' | 'enabled' | 'strategy' | 'description'>>) {
  return http.put<never, ApiResponse<AiSafetyConfig[]>>('/admin/ai/safety/config', { configs })
}
