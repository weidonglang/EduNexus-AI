import { http, type ApiResponse } from './http'

export interface AcademicClass {
  id: number
  college: string
  major: string
  grade: string
  className: string
  advisor?: string
  homeroomTeacherUsername?: string
  homeroomTeacherName?: string
  studentCount: number
}

export interface ClassStudent {
  studentId: number
  studentNo: string
  name: string
  college: string
  major: string
  className: string
  grade: string
  status: string
  phone?: string
  email?: string
}

export interface ClassPayload {
  college: string
  major: string
  grade: string
  className: string
  advisor?: string
  homeroomTeacherUsername?: string
}

export interface ImportStudentPayload {
  studentNo: string
  name: string
  phone?: string
  email?: string
  initialPassword?: string
}

export interface BatchClassStudentResult {
  addedCount: number
  importedCount: number
  errorCount: number
  errors: string[]
}

export function adminClassesApi(keyword?: string) {
  return http.get<never, ApiResponse<AcademicClass[]>>('/admin/classes', {
    params: keyword ? { keyword } : undefined,
  })
}

export function createAdminClassApi(payload: ClassPayload) {
  return http.post<never, ApiResponse<AcademicClass>>('/admin/classes', payload)
}

export function updateAdminClassApi(classId: number, payload: ClassPayload) {
  return http.put<never, ApiResponse<AcademicClass>>(`/admin/classes/${classId}`, payload)
}

export function deleteAdminClassApi(classId: number) {
  return http.delete<never, ApiResponse<void>>(`/admin/classes/${classId}`)
}

export function adminClassStudentsApi(classId: number) {
  return http.get<never, ApiResponse<ClassStudent[]>>(`/admin/classes/${classId}/students`)
}

export function addAdminClassStudentApi(classId: number, studentNo: string) {
  return http.post<never, ApiResponse<ClassStudent>>(`/admin/classes/${classId}/students`, { studentNo })
}

export function batchAdminClassStudentsApi(classId: number, payload: { studentNos?: string[]; students?: ImportStudentPayload[] }) {
  return http.post<never, ApiResponse<BatchClassStudentResult>>(`/admin/classes/${classId}/students/batch`, payload)
}

export function removeAdminClassStudentApi(classId: number, studentId: number) {
  return http.delete<never, ApiResponse<void>>(`/admin/classes/${classId}/students/${studentId}`)
}

export function transferAdminClassStudentApi(classId: number, studentId: number, targetClassId: number) {
  return http.post<never, ApiResponse<ClassStudent>>(`/admin/classes/${classId}/students/transfer`, {
    studentId,
    targetClassId,
  })
}

export function batchTransferAdminClassStudentsApi(classId: number, targetClassId: number, studentIds: number[]) {
  return http.post<never, ApiResponse<{ transferredCount: number; errorCount: number; errors: string[] }>>(
    `/admin/classes/${classId}/students/batch-transfer`,
    { targetClassId, studentIds },
  )
}
