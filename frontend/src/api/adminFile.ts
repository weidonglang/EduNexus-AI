import { http, type ApiResponse } from './http'

export interface AdminAttachment {
  id: number
  applicationId: number
  originalFilename: string
  storedPath: string
  contentType?: string
  fileTypeLabel?: string
  sizeBytes: number
  uploadedAt: string
  studentNo: string
  studentName: string
  changeType: string
  applicationStatus: string
}

export function adminFilesApi() {
  return http.get<never, ApiResponse<AdminAttachment[]>>('/admin/files')
}

export function deleteAdminFileApi(attachmentId: number) {
  return http.delete<never, ApiResponse<void>>(`/admin/files/${attachmentId}`)
}

export function downloadAdminFileApi(attachmentId: number) {
  return http.get<never, Blob>(`/admin/files/${attachmentId}/download`, {
    responseType: 'blob',
  })
}
