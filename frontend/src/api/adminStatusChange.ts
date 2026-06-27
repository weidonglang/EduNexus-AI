import { http, type ApiResponse, type PageResponse } from './http'
import type { ApplicationStatus, StatusChangeType } from './student'

export type ReviewDecision = 'APPROVE' | 'REJECT'

export interface AdminStatusChangeApplication {
  id: number
  studentId: number
  studentNo: string
  studentName: string
  college: string
  major: string
  className: string
  studentStatus: string
  type: StatusChangeType
  reason: string
  status: ApplicationStatus
  submittedAt: string
  reviewedAt?: string
  reviewComment?: string
}

export interface ReviewStatusChangePayload {
  decision: ReviewDecision
  comment: string
}

export interface AdminStatusChangeAttachment {
  id: number
  applicationId: number
  originalFilename: string
  contentType?: string
  fileTypeLabel: string
  sizeBytes: number
  uploadedAt: string
  previewable: boolean
}

export function adminStatusChangesApi(params?: { status?: ApplicationStatus | ''; keyword?: string; page?: number; size?: number }) {
  return http.get<never, ApiResponse<PageResponse<AdminStatusChangeApplication>>>('/admin/status-changes', {
    params,
  })
}

export function reviewStatusChangeApi(applicationId: number, payload: ReviewStatusChangePayload) {
  return http.post<never, ApiResponse<AdminStatusChangeApplication>>(
    `/admin/status-changes/${applicationId}/review`,
    payload,
  )
}

export function adminStatusChangeAttachmentsApi(applicationId: number) {
  return http.get<never, ApiResponse<AdminStatusChangeAttachment[]>>(`/admin/status-changes/${applicationId}/attachments`)
}

export function adminStatusChangeAttachmentPreviewUrl(applicationId: number, attachmentId: number) {
  return `/api/admin/status-changes/${applicationId}/attachments/${attachmentId}/preview`
}

export function adminStatusChangeAttachmentDownloadUrl(applicationId: number, attachmentId: number) {
  return `/api/admin/status-changes/${applicationId}/attachments/${attachmentId}/download`
}
