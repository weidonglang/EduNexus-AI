import { http, type ApiResponse } from './http'
import type { EvaluationSummary } from './evaluation'
import type { AdminExam, AdminExamPayload } from './academicAdmin'

// 教师端 API 封装。
// 包含任课教学班、成绩录入分页、考试安排维护和评价结果查看等接口。
// 页面层通过这些函数调用后端，保证教师只能操作自己负责的教学班。
export interface PageResponse<T> {
  records: T[]
  page: number
  size: number
  total: number
}

export interface TeacherOffering {
  offeringId: number
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
}

export function teacherOfferingsApi(term?: string) {
  return http.get<never, ApiResponse<TeacherOffering[]>>('/teacher/offerings', { params: term ? { term } : undefined })
}

export interface TeacherGradeEntry {
  gradeId?: number
  offeringId: number
  studentNo: string
  studentName: string
  courseId: number
  courseCode: string
  courseName: string
  term: string
  score?: number
  gradePoint?: number
  examType: string
  gradeStatus: string
  locked: boolean
}

export interface TeacherGradePayload {
  gradeId?: number
  offeringId: number
  studentNo: string
  term: string
  score: number
  examType: string
  gradeStatus: string
  reason?: string
}

export function teacherGradesApi(params?: { term?: string; offeringId?: number; keyword?: string; page?: number; size?: number }) {
  return http.get<never, ApiResponse<PageResponse<TeacherGradeEntry>>>('/teacher/grades', { params })
}

export function saveTeacherGradeApi(payload: TeacherGradePayload) {
  return http.post<never, ApiResponse<void>>('/teacher/grades', payload)
}

export function teacherExamsApi(term?: string) {
  return http.get<never, ApiResponse<AdminExam[]>>('/teacher/exams', { params: term ? { term } : undefined })
}

export function createTeacherExamApi(payload: AdminExamPayload) {
  return http.post<never, ApiResponse<void>>('/teacher/exams', payload)
}

export function updateTeacherExamApi(examId: number, payload: AdminExamPayload) {
  return http.put<never, ApiResponse<void>>(`/teacher/exams/${examId}`, payload)
}

export function deleteTeacherExamApi(examId: number) {
  return http.delete<never, ApiResponse<void>>(`/teacher/exams/${examId}`)
}

export function teacherEvaluationsApi(term?: string) {
  return http.get<never, ApiResponse<EvaluationSummary[]>>('/teacher/evaluations', { params: term ? { term } : undefined })
}
