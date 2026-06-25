import { http, type ApiResponse } from './http'

export interface AdminCourse {
  courseId: number
  code: string
  name: string
  credit: number
  category: string
}

export interface AdminCourseOffering {
  offeringId: number
  courseId: number
  courseCode: string
  courseName: string
  credit: number
  category: string
  teacherName: string
  term: string
  capacity: number
  selectedCount: number
  scheduleText: string
  classroom: string
  selectionStartAt: string
  selectionEndAt: string
}

export interface OfferingStudent {
  studentId: number
  studentNo: string
  studentName: string
  className: string
  major: string
  selectedAt: string
  gradeStatus: string
}

export interface CourseOfferingOptions {
  courses: AdminCourse[]
  teachers: string[]
  classrooms: string[]
  terms: string[]
}

export interface CourseCreatePayload {
  code: string
  name: string
  credit: number
  category: string
}

export interface CourseOfferingPayload {
  courseId: number
  teacherName: string
  term: string
  capacity: number
  scheduleText: string
  classroom: string
  selectionStartAt: string
  selectionEndAt: string
}

export function adminCoursesApi() {
  return http.get<never, ApiResponse<AdminCourse[]>>('/admin/courses')
}

export function createAdminCourseApi(payload: CourseCreatePayload) {
  return http.post<never, ApiResponse<AdminCourse>>('/admin/courses', payload)
}

export function updateAdminCourseApi(courseId: number, payload: CourseCreatePayload) {
  return http.put<never, ApiResponse<AdminCourse>>(`/admin/courses/${courseId}`, payload)
}

export function deleteAdminCourseApi(courseId: number) {
  return http.delete<never, ApiResponse<void>>(`/admin/courses/${courseId}`)
}

export function adminCourseOfferingsApi(term?: string) {
  return http.get<never, ApiResponse<AdminCourseOffering[]>>('/admin/course-offerings', {
    params: term ? { term } : undefined,
  })
}

export function adminCourseOfferingOptionsApi() {
  return http.get<never, ApiResponse<CourseOfferingOptions>>('/admin/course-offerings/options')
}

export function adminCourseOfferingStudentsApi(offeringId: number) {
  return http.get<never, ApiResponse<OfferingStudent[]>>(`/admin/course-offerings/${offeringId}/students`)
}

export function publishAdminCourseOfferingApi(offeringId: number) {
  return http.post<never, ApiResponse<AdminCourseOffering>>(`/admin/course-offerings/${offeringId}/publish`)
}

export function closeAdminCourseOfferingApi(offeringId: number) {
  return http.post<never, ApiResponse<AdminCourseOffering>>(`/admin/course-offerings/${offeringId}/close`)
}

export function createAdminCourseOfferingApi(payload: CourseOfferingPayload) {
  return http.post<never, ApiResponse<AdminCourseOffering>>('/admin/course-offerings', payload)
}

export function updateAdminCourseOfferingApi(offeringId: number, payload: CourseOfferingPayload) {
  return http.put<never, ApiResponse<AdminCourseOffering>>(`/admin/course-offerings/${offeringId}`, payload)
}

export function deleteAdminCourseOfferingApi(offeringId: number) {
  return http.delete<never, ApiResponse<void>>(`/admin/course-offerings/${offeringId}`)
}
