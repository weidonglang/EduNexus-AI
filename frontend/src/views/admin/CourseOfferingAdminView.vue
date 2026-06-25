<script setup lang="ts">
// 管理端课程与教学班维护页面。
// 课程表示基础课程信息，教学班表示具体学期、教师、容量、时间和教室。
// 修改教学班容量后，后端会清理相关 Redis 库存 Key，避免缓存和数据库容量不一致。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  adminCourseOfferingsApi,
  adminCourseOfferingOptionsApi,
  adminCourseOfferingStudentsApi,
  adminCoursesApi,
  closeAdminCourseOfferingApi,
  createAdminCourseApi,
  createAdminCourseOfferingApi,
  deleteAdminCourseApi,
  deleteAdminCourseOfferingApi,
  publishAdminCourseOfferingApi,
  updateAdminCourseApi,
  updateAdminCourseOfferingApi,
  type AdminCourse,
  type AdminCourseOffering,
  type CourseOfferingPayload,
  type OfferingStudent,
} from '@/api/adminCourse'

const DEFAULT_TERM = '2025-2026-2'

const loading = ref(false)
const saving = ref(false)
const courseDialogVisible = ref(false)
const offeringDialogVisible = ref(false)
const studentsDialogVisible = ref(false)
const editingCourseId = ref<number | null>(null)
const editingOfferingId = ref<number | null>(null)
const termFilter = ref(DEFAULT_TERM)
const keywordFilter = ref('')
const courses = ref<AdminCourse[]>([])
const offerings = ref<AdminCourseOffering[]>([])
const teachers = ref<string[]>([])
const classrooms = ref<string[]>([])
const offeringStudents = ref<OfferingStudent[]>([])
const currentOfferingTitle = ref('')
const coursePage = ref(1)
const coursePageSize = ref(8)
const offeringPage = ref(1)
const offeringPageSize = ref(10)

const courseForm = reactive({
  code: '',
  name: '',
  credit: 3,
  category: '专业必修',
})

const offeringForm = reactive({
  courseId: undefined as number | undefined,
  teacherName: '',
  term: DEFAULT_TERM,
  capacity: 40,
  scheduleText: '',
  classroom: '',
  selectionRange: [] as string[],
})

const selectedTotal = computed(() => offerings.value.reduce((total, item) => total + item.selectedCount, 0))
const filteredCourses = computed(() => courses.value.filter((course) => courseMatchesKeyword(course)))
const filteredOfferings = computed(() => offerings.value.filter((offering) => offeringMatchesKeyword(offering)))
const pagedCourses = computed(() => paginate(filteredCourses.value, coursePage.value, coursePageSize.value))
const pagedOfferings = computed(() => paginate(filteredOfferings.value, offeringPage.value, offeringPageSize.value))

onMounted(loadData)

// 功能：加载课程和教学班管理数据。
// 说明：管理端需要同时维护课程基础信息和教学班开课实例，教学班数据会影响学生选课和 Redis 库存。
async function loadData() {
  loading.value = true
  try {
    const [courseResponse, offeringResponse] = await Promise.all([
      adminCoursesApi(),
      adminCourseOfferingsApi(termFilter.value),
    ])
    courses.value = courseResponse.data
    offerings.value = offeringResponse.data
    const optionsResponse = await adminCourseOfferingOptionsApi()
    teachers.value = optionsResponse.data.teachers
    classrooms.value = optionsResponse.data.classrooms
    coursePage.value = 1
    offeringPage.value = 1
  } finally {
    loading.value = false
  }
}

function paginate<T>(items: T[], page: number, pageSize: number) {
  const start = (page - 1) * pageSize
  return items.slice(start, start + pageSize)
}

function normalizeSearch(value: unknown) {
  return String(value ?? '').trim().toLowerCase()
}

function includesKeyword(values: unknown[]) {
  const keyword = normalizeSearch(keywordFilter.value)
  if (!keyword) {
    return true
  }
  return values.some((value) => normalizeSearch(value).includes(keyword))
}

function courseMatchesKeyword(course: AdminCourse) {
  return includesKeyword([
    course.code,
    course.name,
    course.credit,
    course.category,
  ])
}

function offeringMatchesKeyword(offering: AdminCourseOffering) {
  return includesKeyword([
    offering.term,
    offering.courseCode,
    offering.courseName,
    offering.category,
    offering.teacherName,
    offering.capacity,
    offering.selectedCount,
    `${offering.selectedCount}/${offering.capacity}`,
    offering.scheduleText,
    offering.classroom,
    formatDateTime(offering.selectionStartAt),
    formatDateTime(offering.selectionEndAt),
    `${formatDateTime(offering.selectionStartAt)} - ${formatDateTime(offering.selectionEndAt)}`,
  ])
}

function resetSearchPages() {
  coursePage.value = 1
  offeringPage.value = 1
}

function openCreateCourse() {
  editingCourseId.value = null
  courseForm.code = ''
  courseForm.name = ''
  courseForm.credit = 3
  courseForm.category = '专业必修'
  courseDialogVisible.value = true
}

function openEditCourse(row: AdminCourse) {
  editingCourseId.value = row.courseId
  courseForm.code = row.code
  courseForm.name = row.name
  courseForm.credit = row.credit
  courseForm.category = row.category
  courseDialogVisible.value = true
}

// 功能：保存课程基础信息。
// 说明：新增或修改课程编号、名称、学分和类别，教学班在课程基础上进行具体开设。
async function saveCourse() {
  saving.value = true
  try {
    const payload = {
      code: courseForm.code,
      name: courseForm.name,
      credit: courseForm.credit,
      category: courseForm.category,
    }
    if (editingCourseId.value) {
      await updateAdminCourseApi(editingCourseId.value, payload)
      ElMessage.success('课程已更新')
    } else {
      await createAdminCourseApi(payload)
      ElMessage.success('课程已新增')
    }
    courseDialogVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存课程失败'))
  } finally {
    saving.value = false
  }
}

async function removeCourse(row: AdminCourse) {
  await ElMessageBox.confirm(`确认删除课程 ${row.code} ${row.name} 吗？已有教学班的课程会被后端拒绝删除。`, '删除课程', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  try {
    await deleteAdminCourseApi(row.courseId)
    ElMessage.success('课程已删除')
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除课程失败'))
  }
}

function openCreateOffering() {
  editingOfferingId.value = null
  offeringForm.courseId = courses.value[0]?.courseId
  offeringForm.teacherName = ''
  offeringForm.term = termFilter.value || DEFAULT_TERM
  offeringForm.capacity = 40
  offeringForm.scheduleText = ''
  offeringForm.classroom = ''
  offeringForm.selectionRange = defaultSelectionRange()
  offeringDialogVisible.value = true
}

function openEditOffering(row: AdminCourseOffering) {
  editingOfferingId.value = row.offeringId
  offeringForm.courseId = row.courseId
  offeringForm.teacherName = row.teacherName
  offeringForm.term = row.term
  offeringForm.capacity = row.capacity
  offeringForm.scheduleText = row.scheduleText
  offeringForm.classroom = row.classroom
  offeringForm.selectionRange = [row.selectionStartAt, row.selectionEndAt]
  offeringDialogVisible.value = true
}

// 功能：保存教学班信息。
// 说明：维护任课教师、容量、上课时间、教室和选课窗口；修改容量后后端会清理旧 Redis 库存缓存。
async function saveOffering() {
  const payload = buildOfferingPayload()
  if (!payload) return
  saving.value = true
  try {
    if (editingOfferingId.value) {
      await updateAdminCourseOfferingApi(editingOfferingId.value, payload)
      ElMessage.success('教学班已更新')
    } else {
      await createAdminCourseOfferingApi(payload)
      ElMessage.success('教学班已新增')
    }
    offeringDialogVisible.value = false
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存教学班失败'))
  } finally {
    saving.value = false
  }
}

// 功能：删除教学班。
// 说明：已有学生选课的教学班不能删除，防止影响课表、成绩和考试安排等业务数据。
async function removeOffering(row: AdminCourseOffering) {
  await ElMessageBox.confirm(`确认删除 ${row.courseName} 的教学班吗？`, '删除教学班', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  try {
    await deleteAdminCourseOfferingApi(row.offeringId)
    ElMessage.success('教学班已删除')
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除教学班失败'))
  }
}

async function publishOffering(row: AdminCourseOffering) {
  try {
    await publishAdminCourseOfferingApi(row.offeringId)
    ElMessage.success('教学班已发布到选课')
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '发布教学班失败'))
  }
}

async function closeOffering(row: AdminCourseOffering) {
  try {
    await closeAdminCourseOfferingApi(row.offeringId)
    ElMessage.success('教学班已关闭选课')
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '关闭教学班失败'))
  }
}

async function openOfferingStudents(row: AdminCourseOffering) {
  currentOfferingTitle.value = `${row.courseCode} ${row.courseName}`
  offeringStudents.value = (await adminCourseOfferingStudentsApi(row.offeringId)).data
  studentsDialogVisible.value = true
}

function buildOfferingPayload(): CourseOfferingPayload | null {
  if (!offeringForm.courseId) {
    ElMessage.warning('请选择课程')
    return null
  }
  if (offeringForm.selectionRange.length !== 2) {
    ElMessage.warning('请选择选课开放时间')
    return null
  }
  return {
    courseId: offeringForm.courseId,
    teacherName: offeringForm.teacherName,
    term: offeringForm.term,
    capacity: offeringForm.capacity,
    scheduleText: offeringForm.scheduleText,
    classroom: offeringForm.classroom,
    selectionStartAt: new Date(offeringForm.selectionRange[0]).toISOString(),
    selectionEndAt: new Date(offeringForm.selectionRange[1]).toISOString(),
  }
}

function defaultSelectionRange() {
  const start = new Date()
  start.setMinutes(0, 0, 0)
  const end = new Date(start)
  end.setDate(end.getDate() + 7)
  return [start.toISOString(), end.toISOString()]
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function isOfferingOpen(row: AdminCourseOffering) {
  const now = Date.now()
  return new Date(row.selectionStartAt).getTime() <= now && now <= new Date(row.selectionEndAt).getTime()
}

function resolveErrorMessage(error: unknown, fallback: string) {
  if (
    typeof error === 'object' &&
    error !== null &&
    'response' in error &&
    typeof (error as { response?: { data?: { message?: string } } }).response?.data?.message === 'string'
  ) {
    return (error as { response: { data: { message: string } } }).response.data.message
  }
  return fallback
}
</script>

<template>
  <PageHeader title="课程与教学班" description="维护课程基础信息、教学班容量、上课安排和选课开放时间。" />

  <section class="admin-toolbar course-offering-toolbar">
    <div class="admin-summary">
      <article><span>课程数</span><strong>{{ courses.length }}</strong></article>
      <article><span>教学班</span><strong>{{ offerings.length }}</strong></article>
      <article><span>已选人次</span><strong>{{ selectedTotal }}</strong></article>
    </div>
    <div class="admin-actions">
      <el-input v-model="termFilter" class="term-input" placeholder="学期" clearable />
      <el-input
        v-model="keywordFilter"
        class="course-search-input"
        placeholder="综合搜索课程、教师、地点、容量、时间"
        clearable
        @input="resetSearchPages"
        @clear="resetSearchPages"
      />
      <el-button @click="loadData">查询</el-button>
      <el-button @click="openCreateCourse">新增课程</el-button>
      <el-button type="primary" @click="openCreateOffering">新增教学班</el-button>
    </div>
  </section>

  <section v-loading="loading" class="course-admin-layout">
    <article class="work-panel course-list-panel">
      <div class="section-heading course-panel-heading">
        <div>
          <h2>课程基础信息</h2>
          <p>显示 {{ filteredCourses.length }} / {{ courses.length }} 门课程</p>
        </div>
      </div>
      <el-table :data="pagedCourses" empty-text="暂无课程" class="compact-admin-table">
        <el-table-column prop="code" label="课程编号" width="118" />
        <el-table-column prop="name" label="课程名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="credit" label="学分" width="70" />
        <el-table-column prop="category" label="类别" width="112" show-overflow-tooltip />
        <el-table-column label="操作" width="118" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEditCourse(row)">编辑</el-button>
            <el-button type="danger" link @click="removeCourse(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="coursePage"
        v-model:page-size="coursePageSize"
        class="table-pagination compact-pagination"
        layout="prev, pager, next"
        :total="filteredCourses.length"
      />
    </article>

    <article class="work-panel offering-list-panel">
      <div class="section-heading course-panel-heading">
        <div>
          <h2>教学班维护</h2>
          <p>显示 {{ filteredOfferings.length }} / {{ offerings.length }} 个教学班</p>
        </div>
      </div>
      <el-table :data="pagedOfferings" empty-text="暂无教学班" class="offering-admin-table">
        <el-table-column prop="term" label="学期" width="126" />
        <el-table-column prop="courseCode" label="课程号" width="105" />
        <el-table-column prop="courseName" label="课程名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="category" label="类别" width="112" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="教师" width="105" />
        <el-table-column label="容量" width="104">
          <template #default="{ row }">
            <el-tag :type="row.selectedCount >= row.capacity ? 'danger' : 'info'" effect="plain">
              {{ row.selectedCount }}/{{ row.capacity }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scheduleText" label="上课时间" min-width="130" show-overflow-tooltip />
        <el-table-column prop="classroom" label="地点" width="120" show-overflow-tooltip />
        <el-table-column label="选课开放时间" min-width="210">
          <template #default="{ row }">
            <span class="muted-range">{{ formatDateTime(row.selectionStartAt) }} - {{ formatDateTime(row.selectionEndAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="isOfferingOpen(row) ? 'success' : 'info'">{{ isOfferingOpen(row) ? '开放' : '关闭' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="245" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openOfferingStudents(row)">学生</el-button>
            <el-button v-if="!isOfferingOpen(row)" type="success" link @click="publishOffering(row)">发布</el-button>
            <el-button v-else type="warning" link @click="closeOffering(row)">关闭</el-button>
            <el-button type="primary" link @click="openEditOffering(row)">编辑</el-button>
            <el-button type="danger" link :disabled="row.selectedCount > 0" @click="removeOffering(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="offeringPage"
        v-model:page-size="offeringPageSize"
        class="table-pagination"
        layout="total, sizes, prev, pager, next, jumper"
        :page-sizes="[10, 20, 30, 50]"
        :total="filteredOfferings.length"
      />
    </article>
  </section>

  <el-dialog v-model="courseDialogVisible" :title="editingCourseId ? '编辑课程' : '新增课程'" width="440px">
    <el-form label-width="84px" :model="courseForm">
      <el-form-item label="课程号">
        <el-input v-model="courseForm.code" placeholder="例如 CS401" />
      </el-form-item>
      <el-form-item label="课程名称">
        <el-input v-model="courseForm.name" placeholder="请输入课程名称" />
      </el-form-item>
      <el-form-item label="学分">
        <el-input-number v-model="courseForm.credit" :min="1" :max="10" />
      </el-form-item>
      <el-form-item label="类别">
        <el-select v-model="courseForm.category" class="full-field">
          <el-option label="专业必修" value="专业必修" />
          <el-option label="专业选修" value="专业选修" />
          <el-option label="公共必修" value="公共必修" />
          <el-option label="公共选修" value="公共选修" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="courseDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveCourse">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="offeringDialogVisible" :title="editingOfferingId ? '编辑教学班' : '新增教学班'" width="620px">
    <el-form label-width="110px" :model="offeringForm">
      <el-form-item label="课程">
        <el-select v-model="offeringForm.courseId" filterable class="full-field" placeholder="请选择课程">
          <el-option
            v-for="course in courses"
            :key="course.courseId"
            :label="`${course.code} ${course.name}`"
            :value="course.courseId"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="教师">
        <el-select v-model="offeringForm.teacherName" class="full-field" filterable allow-create default-first-option placeholder="请选择教师">
          <el-option v-for="teacher in teachers" :key="teacher" :label="teacher" :value="teacher" />
        </el-select>
      </el-form-item>
      <el-form-item label="学期">
        <el-input v-model="offeringForm.term" placeholder="例如 2025-2026-2" />
      </el-form-item>
      <el-form-item label="容量">
        <el-input-number v-model="offeringForm.capacity" :min="1" :max="500" />
      </el-form-item>
      <el-form-item label="上课时间">
        <el-input v-model="offeringForm.scheduleText" placeholder="例如 周一 1-2节" />
      </el-form-item>
      <el-form-item label="教室">
        <el-select v-model="offeringForm.classroom" class="full-field" filterable allow-create default-first-option placeholder="请选择教室">
          <el-option v-for="classroom in classrooms" :key="classroom" :label="classroom" :value="classroom" />
        </el-select>
      </el-form-item>
      <el-form-item label="选课时间">
        <el-date-picker
          v-model="offeringForm.selectionRange"
          class="full-field"
          type="datetimerange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DDTHH:mm:ss.SSSZ"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="offeringDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveOffering">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="studentsDialogVisible" :title="`${currentOfferingTitle} 已选学生`" width="760px">
    <el-table :data="offeringStudents" empty-text="暂无选课学生">
      <el-table-column prop="studentNo" label="学号" width="120" />
      <el-table-column prop="studentName" label="姓名" width="100" />
      <el-table-column prop="className" label="班级" min-width="140" />
      <el-table-column prop="major" label="专业" min-width="140" />
      <el-table-column prop="gradeStatus" label="成绩状态" width="100" />
      <el-table-column label="选课时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.selectedAt) }}</template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>
