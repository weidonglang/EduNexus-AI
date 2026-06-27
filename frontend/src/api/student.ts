import { http, type ApiResponse, type PageResponse } from './http'

// 学生个人信息 API 封装。
// 个人信息页面通过这些函数读取和更新当前登录学生资料，不需要前端传入学号。
export interface StudentProfile {
  studentNo: string
  name: string
  college: string
  major: string
  className: string
  grade: string
  status: string
  phone: string
  email: string
  address: string
}

export function studentProfileApi() {
  return http.get<never, ApiResponse<StudentProfile>>('/students/me/profile')
}

export interface StudentClassMember {
  studentNo: string
  name: string
  status: string
}

export interface StudentClassInfo {
  className: string
  college: string
  major: string
  grade: string
  advisor?: string
  homeroomTeacherName?: string
  studentCount: number
  members: StudentClassMember[]
}

export function studentClassInfoApi() {
  return http.get<never, ApiResponse<StudentClassInfo>>('/students/me/class')
}

export interface UpdateStudentProfileRequest {
  phone: string
  email: string
  address: string
}

export function updateStudentProfileApi(payload: UpdateStudentProfileRequest) {
  return http.put<never, ApiResponse<StudentProfile>>('/students/me/profile', payload)
}

export type StatusChangeType = 'SUSPEND' | 'RESUME' | 'TRANSFER_MAJOR' | 'OTHER'
export type ApplicationStatus = 'SUBMITTED' | 'UNDER_REVIEW' | 'APPROVED' | 'REJECTED' | 'CANCELED'

export interface StatusChangeApplication {
  id: number
  type: StatusChangeType
  reason: string
  status: ApplicationStatus
  submittedAt: string
  reviewedAt?: string
  reviewComment?: string
}

export interface StatusChangeAttachment {
  id: number
  applicationId: number
  originalFilename: string
  contentType: string
  sizeBytes: number
  uploadedAt: string
}

export interface SubmitStatusChangeRequest {
  type: StatusChangeType
  reason: string
}

export function statusChangeApplicationsApi(params?: { page?: number; size?: number }) {
  return http.get<never, ApiResponse<PageResponse<StatusChangeApplication>>>('/students/me/status-changes', { params })
}

export function submitStatusChangeApplicationApi(payload: SubmitStatusChangeRequest) {
  return http.post<never, ApiResponse<StatusChangeApplication>>('/students/me/status-changes', payload)
}

export function statusChangeAttachmentsApi(applicationId: number) {
  return http.get<never, ApiResponse<StatusChangeAttachment[]>>(`/students/me/status-changes/${applicationId}/attachments`)
}

export function uploadStatusChangeAttachmentApi(applicationId: number, file: File) {
  const form = new FormData()
  form.append('file', file)
  return http.post<never, ApiResponse<void>>(`/students/me/status-changes/${applicationId}/attachments`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  })
}

export function statusChangeAttachmentDownloadUrl(applicationId: number, attachmentId: number) {
  return `/api/students/me/status-changes/${applicationId}/attachments/${attachmentId}/download`
}
