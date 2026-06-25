<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  studentProfileApi,
  statusChangeApplicationsApi,
  updateStudentProfileApi,
  type ApplicationStatus,
  type StatusChangeApplication,
  type StatusChangeType,
  type StudentProfile,
  type UpdateStudentProfileRequest,
} from '@/api/student'
import { fetchGradesApi, type GradeRecord } from '@/api/academic'
import { selectedCoursesApi, type CourseSelection } from '@/api/courseSelection'
import { teachingPlanApi, type TeachingPlan } from '@/api/information'

const loading = ref(false)
const saving = ref(false)
const activeTab = ref('basic')
const profile = ref<StudentProfile | null>(null)
const grades = ref<GradeRecord[]>([])
const selectedCourses = ref<CourseSelection[]>([])
const teachingPlan = ref<TeachingPlan[]>([])
const statusChanges = ref<StatusChangeApplication[]>([])
const gradePage = ref(1)
const gradeSize = ref(10)
const selectionPage = ref(1)
const selectionSize = ref(10)
const planPage = ref(1)
const planSize = ref(10)
const statusPage = ref(1)
const statusSize = ref(10)

const form = reactive<UpdateStudentProfileRequest>({
  phone: '',
  email: '',
  address: '',
})

const typeText: Record<StatusChangeType, string> = {
  SUSPEND: '休学',
  RESUME: '复学',
  TRANSFER_MAJOR: '转专业',
  OTHER: '其他',
}

const statusText: Record<ApplicationStatus, string> = {
  SUBMITTED: '已提交',
  UNDER_REVIEW: '审核中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELED: '已取消',
}

const profileDetails = computed(() => {
  const studentNo = profile.value?.studentNo ?? ''
  return {
    pinyin: 'WEI YU SHI',
    englishName: 'Wei Yushi',
    usedName: '',
    gender: studentNo === '23111141' ? '男' : '未维护',
    idType: '居民身份证',
    idNumber: studentNo === '23111141' ? '120106198705143210' : maskIdByStudentNo(studentNo),
    birthDate: studentNo === '23111141' ? '2004-11-17' : '2005-09-01',
    nation: '汉族',
    politicalStatus: '群众',
    politicalJoinDate: '',
    enrollmentDate: '2023-09-03',
    nativePlace: '天津市',
    household: '天津市',
    birthPlace: '天津市',
    bloodType: '',
    studentType: '普通本科生',
    nationality: '中国',
    overseas: '否',
  }
})

const familyMembers = computed(() => [
  { relation: '父亲', name: '魏先生', phone: '138****0101', workplace: '天津市' },
  { relation: '母亲', name: '李女士', phone: '139****0202', workplace: '天津市' },
])

const rewards = computed(() => [
  { term: '2023-2024-2', type: '奖励', name: '校级优秀学生', result: '已认定' },
  { term: '2024-2025-1', type: '奖励', name: '学习进步奖', result: '已认定' },
])

const studyResume = computed(() => [
  { startDate: '2020-09', endDate: '2023-06', school: '天津市示范高中', role: '学生' },
  { startDate: '2023-09', endDate: '至今', school: '天津天狮学院', role: '本科生' },
])

const gradeSummary = computed(() => {
  const credit = grades.value.reduce((total, item) => total + item.credit, 0)
  const weightedPoint = grades.value.reduce((total, item) => total + Number(item.gradePoint) * item.credit, 0)
  return {
    credit,
    averagePoint: credit ? (weightedPoint / credit).toFixed(2) : '-',
  }
})
const pagedGrades = computed(() => grades.value.slice((gradePage.value - 1) * gradeSize.value, gradePage.value * gradeSize.value))
const pagedSelectedCourses = computed(() => selectedCourses.value.slice((selectionPage.value - 1) * selectionSize.value, selectionPage.value * selectionSize.value))
const pagedTeachingPlan = computed(() => teachingPlan.value.slice((planPage.value - 1) * planSize.value, planPage.value * planSize.value))
const pagedStatusChanges = computed(() => statusChanges.value.slice((statusPage.value - 1) * statusSize.value, statusPage.value * statusSize.value))
const unassignedClass = computed(() => !profile.value?.className || profile.value.className === '未分班')

onMounted(loadProfile)

async function loadProfile() {
  loading.value = true
  try {
    const [profileResponse, gradeResponse, selectedResponse, statusResponse] = await Promise.all([
      studentProfileApi(),
      fetchGradesApi({ page: 1, size: 100 }),
      selectedCoursesApi({ page: 1, size: 100 }),
      statusChangeApplicationsApi({ page: 1, size: 100 }),
    ])
    profile.value = profileResponse.data
    form.phone = profileResponse.data.phone ?? ''
    form.email = profileResponse.data.email ?? ''
    form.address = profileResponse.data.address ?? ''
    grades.value = gradeResponse.records
    selectedCourses.value = selectedResponse.data.records
    statusChanges.value = statusResponse.data.records
    teachingPlan.value = (await teachingPlanApi({
      major: profileResponse.data.major,
      grade: profileResponse.data.grade,
    })).data
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  saving.value = true
  try {
    const response = await updateStudentProfileApi(form)
    profile.value = response.data
    ElMessage.success('联系方式已保存')
  } finally {
    saving.value = false
  }
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return new Date(value).toLocaleString('zh-CN')
}

function maskIdByStudentNo(studentNo: string) {
  if (!studentNo) return ''
  return `120102${studentNo.slice(-6).padStart(8, '0')}****`
}

function handleGradeSizeChange() {
  gradePage.value = 1
}

function handleSelectionSizeChange() {
  selectionPage.value = 1
}

function handlePlanSizeChange() {
  planPage.value = 1
}

function handleStatusSizeChange() {
  statusPage.value = 1
}
</script>

<template>
  <section v-loading="loading" class="student-profile-page">
    <header class="student-profile-title">查询个人信息</header>

    <section v-if="profile" class="student-profile-hero">
      <div class="identity-line">
        <div><strong>学号：</strong><span>{{ profile.studentNo }}</span></div>
        <div><strong>姓名：</strong><span>{{ profile.name }}</span></div>
        <div class="qr-block">
          <strong>学号二维码：</strong>
          <div class="fake-qr" aria-label="学号二维码">
            <span v-for="index in 49" :key="index" :class="{ dark: (index + profile.studentNo.length) % 3 !== 0 }" />
          </div>
        </div>
      </div>
    </section>

    <el-tabs v-if="profile" v-model="activeTab" class="student-profile-tabs">
      <el-tab-pane label="基本信息" name="basic">
        <section class="profile-basic-grid">
          <div class="info-fields three-columns">
            <div><strong>学号：</strong><span>{{ profile.studentNo }}</span></div>
            <div><strong>姓名：</strong><span>{{ profile.name }}</span></div>
            <div><strong>姓名拼音：</strong><span>{{ profileDetails.pinyin }}</span></div>
            <div><strong>英文姓名：</strong><span>{{ profileDetails.englishName }}</span></div>
            <div><strong>曾用名：</strong><span>{{ profileDetails.usedName || '-' }}</span></div>
            <div><strong>性别：</strong><span>{{ profileDetails.gender }}</span></div>
            <div><strong>证件类型：</strong><span>{{ profileDetails.idType }}</span></div>
            <div><strong>证件号码：</strong><span>{{ profileDetails.idNumber }}</span></div>
            <div><strong>出生日期：</strong><span>{{ profileDetails.birthDate }}</span></div>
            <div><strong>民族：</strong><span>{{ profileDetails.nation }}</span></div>
            <div><strong>籍贯：</strong><span>{{ profileDetails.household || '-' }}</span></div>
            <div><strong>生源地：</strong><span>{{ profileDetails.nativePlace }}</span></div>
            <div><strong>政治面貌：</strong><span>{{ profileDetails.politicalStatus }}</span></div>
            <div><strong>政治面貌加入时间：</strong><span>{{ profileDetails.politicalJoinDate || '-' }}</span></div>
            <div><strong>出生地：</strong><span>{{ profileDetails.birthPlace || '-' }}</span></div>
            <div><strong>血型名称：</strong><span>{{ profileDetails.bloodType || '-' }}</span></div>
            <div><strong>学生类型：</strong><span>{{ profileDetails.studentType }}</span></div>
            <div><strong>港澳台侨外：</strong><span>{{ profileDetails.overseas }}</span></div>
            <div><strong>国籍/地区：</strong><span>{{ profileDetails.nationality }}</span></div>
            <div><strong>入学日期：</strong><span>{{ profileDetails.enrollmentDate }}</span></div>
          </div>
          <aside class="photo-column">
            <div class="student-photo"><span>头像</span></div>
            <span>入学前</span>
            <div class="student-photo"><span>头像</span></div>
            <span>入学后</span>
          </aside>
        </section>
      </el-tab-pane>

      <el-tab-pane label="学籍信息" name="academic">
        <el-alert
          v-if="unassignedClass"
          class="profile-alert"
          type="warning"
          title="你尚未被分配班级，请联系管理员。"
          show-icon
          :closable="false"
        />
        <div class="info-fields three-columns">
          <div><strong>学院：</strong><span>{{ profile.college }}</span></div>
          <div><strong>专业：</strong><span>{{ profile.major }}</span></div>
          <div><strong>班级：</strong><span>{{ unassignedClass ? '未分配' : profile.className }}</span></div>
          <div><strong>年级：</strong><span>{{ profile.grade }}</span></div>
          <div><strong>学籍状态：</strong><span>{{ profile.status }}</span></div>
          <div><strong>培养层次：</strong><span>本科</span></div>
          <div><strong>学习形式：</strong><span>普通全日制</span></div>
          <div><strong>校区：</strong><span>主校区</span></div>
          <div><strong>预计毕业日期：</strong><span>2027-06-30</span></div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="其他信息" name="other">
        <div class="info-fields three-columns">
          <div><strong>健康状况：</strong><span>良好</span></div>
          <div><strong>住宿状态：</strong><span>在校住宿</span></div>
          <div><strong>宿舍号：</strong><span>学生公寓 3-502</span></div>
          <div><strong>银行卡状态：</strong><span>未维护</span></div>
          <div><strong>照片状态：</strong><span>已采集</span></div>
          <div><strong>档案状态：</strong><span>在校</span></div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="联系方式" name="contact">
        <section class="contact-grid">
          <article>
            <h3>当前联系方式</h3>
            <div class="info-fields">
              <div><strong>手机号码：</strong><span>{{ profile.phone || '-' }}</span></div>
              <div><strong>电子邮箱：</strong><span>{{ profile.email || '-' }}</span></div>
              <div><strong>通讯地址：</strong><span>{{ profile.address || '-' }}</span></div>
            </div>
          </article>
          <article>
            <h3>联系方式维护</h3>
            <el-form label-width="88px">
              <el-form-item label="手机号">
                <el-input v-model="form.phone" placeholder="请输入手机号" />
              </el-form-item>
              <el-form-item label="邮箱">
                <el-input v-model="form.email" placeholder="请输入邮箱" />
              </el-form-item>
              <el-form-item label="地址">
                <el-input v-model="form.address" type="textarea" :rows="4" placeholder="请输入常住地址" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
              </el-form-item>
            </el-form>
          </article>
        </section>
      </el-tab-pane>

      <el-tab-pane label="家庭成员" name="family">
        <el-table :data="familyMembers" empty-text="暂无家庭成员">
          <el-table-column prop="relation" label="关系" width="120" />
          <el-table-column prop="name" label="姓名" width="140" />
          <el-table-column prop="phone" label="联系电话" width="160" />
          <el-table-column prop="workplace" label="工作单位/住址" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="奖惩信息" name="reward">
        <el-table :data="rewards" empty-text="暂无奖惩信息">
          <el-table-column prop="term" label="学期" width="140" />
          <el-table-column prop="type" label="类型" width="100" />
          <el-table-column prop="name" label="名称" />
          <el-table-column prop="result" label="状态" width="120" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="成绩信息" name="grade">
        <section class="mini-summary">
          <span>已获学分：{{ gradeSummary.credit }}</span>
          <span>平均绩点：{{ gradeSummary.averagePoint }}</span>
        </section>
        <el-table :data="pagedGrades" empty-text="暂无成绩">
          <el-table-column prop="term" label="学期" width="140" />
          <el-table-column prop="courseCode" label="课程号" width="110" />
          <el-table-column prop="courseName" label="课程名称" min-width="160" />
          <el-table-column prop="courseType" label="课程性质" width="120" />
          <el-table-column prop="credit" label="学分" width="80" />
          <el-table-column prop="score" label="成绩" width="80" />
          <el-table-column prop="gradePoint" label="绩点" width="80" />
          <el-table-column prop="examType" label="考试类型" width="120" />
        </el-table>
        <el-pagination
          v-model:current-page="gradePage"
          v-model:page-size="gradeSize"
          class="table-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="grades.length"
          @size-change="handleGradeSizeChange"
        />
      </el-tab-pane>

      <el-tab-pane label="选课信息" name="selection">
        <el-table :data="pagedSelectedCourses" empty-text="暂无选课">
          <el-table-column prop="courseCode" label="课程号" width="110" />
          <el-table-column prop="courseName" label="课程名称" min-width="160" />
          <el-table-column prop="credit" label="学分" width="80" />
          <el-table-column prop="teacherName" label="教师" width="110" />
          <el-table-column prop="scheduleText" label="上课时间" width="130" />
          <el-table-column prop="classroom" label="教室" width="130" />
          <el-table-column label="选课时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.selectedAt) }}</template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-model:current-page="selectionPage"
          v-model:page-size="selectionSize"
          class="table-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="selectedCourses.length"
          @size-change="handleSelectionSizeChange"
        />
      </el-tab-pane>

      <el-tab-pane label="培养方案" name="plan">
        <el-table :data="pagedTeachingPlan" empty-text="暂无培养方案">
          <el-table-column prop="term" label="学期" width="140" />
          <el-table-column prop="courseCode" label="课程号" width="110" />
          <el-table-column prop="courseName" label="课程名称" min-width="160" />
          <el-table-column prop="credit" label="学分" width="80" />
          <el-table-column prop="courseType" label="课程类别" width="130" />
          <el-table-column prop="assessmentType" label="考核方式" width="120" />
        </el-table>
        <el-pagination
          v-model:current-page="planPage"
          v-model:page-size="planSize"
          class="table-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="teachingPlan.length"
          @size-change="handlePlanSizeChange"
        />
      </el-tab-pane>

      <el-tab-pane label="学籍异动" name="status">
        <el-table :data="pagedStatusChanges" empty-text="暂无学籍异动">
          <el-table-column label="类型" width="120">
            <template #default="{ row }">{{ typeText[row.type as StatusChangeType] }}</template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" min-width="180" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">{{ statusText[row.status as ApplicationStatus] }}</template>
          </el-table-column>
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
          </el-table-column>
          <el-table-column prop="reviewComment" label="审核意见" min-width="160" />
        </el-table>
        <el-pagination
          v-model:current-page="statusPage"
          v-model:page-size="statusSize"
          class="table-pagination"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="statusChanges.length"
          @size-change="handleStatusSizeChange"
        />
      </el-tab-pane>

      <el-tab-pane label="学习简历" name="resume">
        <el-table :data="studyResume" empty-text="暂无学习简历">
          <el-table-column prop="startDate" label="开始年月" width="130" />
          <el-table-column prop="endDate" label="结束年月" width="130" />
          <el-table-column prop="school" label="学校/单位" />
          <el-table-column prop="role" label="身份" width="120" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>
