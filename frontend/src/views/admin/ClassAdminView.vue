<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  addAdminClassStudentApi,
  adminClassesApi,
  adminClassStudentsApi,
  batchAdminClassStudentsApi,
  createAdminClassApi,
  deleteAdminClassApi,
  removeAdminClassStudentApi,
  transferAdminClassStudentApi,
  updateAdminClassApi,
  type AcademicClass,
  type ClassStudent,
  type ImportStudentPayload,
} from '@/api/adminClass'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const studentLoading = ref(false)
const saving = ref(false)
const classDialogVisible = ref(false)
const transferDialogVisible = ref(false)
const keyword = ref('')
const addStudentNo = ref('')
const batchText = ref('')
const importText = ref('')
const classes = ref<AcademicClass[]>([])
const students = ref<ClassStudent[]>([])
const selectedClassId = ref<number>()
const editingClassId = ref<number | null>(null)
const transferringStudent = ref<ClassStudent | null>(null)
const targetClassId = ref<number>()

const classForm = reactive({
  college: '',
  major: '',
  grade: '2023',
  className: '',
  advisor: '',
})

const selectedClass = computed(() => classes.value.find((item) => item.id === selectedClassId.value))
const targetClassOptions = computed(() => classes.value.filter((item) => item.id !== selectedClassId.value))

onMounted(loadClasses)

async function loadClasses() {
  loading.value = true
  try {
    classes.value = (await adminClassesApi(keyword.value.trim() || undefined)).data
    const routeId = Number(route.params.id)
    if (routeId && classes.value.some((item) => item.id === routeId)) {
      selectedClassId.value = routeId
    } else if (!selectedClassId.value || !classes.value.some((item) => item.id === selectedClassId.value)) {
      selectedClassId.value = classes.value[0]?.id
    }
    if (selectedClassId.value) {
      await loadStudents()
    } else {
      students.value = []
    }
  } finally {
    loading.value = false
  }
}

async function selectClass(row: AcademicClass) {
  selectedClassId.value = row.id
  await router.replace(`/admin/classes/${row.id}/students`)
  await loadStudents()
}

async function loadStudents() {
  if (!selectedClassId.value) return
  studentLoading.value = true
  try {
    students.value = (await adminClassStudentsApi(selectedClassId.value)).data
  } finally {
    studentLoading.value = false
  }
}

function openCreateClass() {
  editingClassId.value = null
  classForm.college = ''
  classForm.major = ''
  classForm.grade = '2023'
  classForm.className = ''
  classForm.advisor = ''
  classDialogVisible.value = true
}

function openEditClass(row: AcademicClass) {
  editingClassId.value = row.id
  classForm.college = row.college
  classForm.major = row.major
  classForm.grade = row.grade
  classForm.className = row.className
  classForm.advisor = row.advisor || ''
  classDialogVisible.value = true
}

async function saveClass() {
  saving.value = true
  try {
    const payload = { ...classForm }
    if (editingClassId.value) {
      await updateAdminClassApi(editingClassId.value, payload)
      ElMessage.success('班级已更新')
    } else {
      await createAdminClassApi(payload)
      ElMessage.success('班级已创建')
    }
    classDialogVisible.value = false
    await loadClasses()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存班级失败'))
  } finally {
    saving.value = false
  }
}

async function removeClass(row: AcademicClass) {
  await ElMessageBox.confirm(`确认删除 ${row.className} 吗？班级内有学生时后端会拒绝删除。`, '删除班级', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  try {
    await deleteAdminClassApi(row.id)
    ElMessage.success('班级已删除')
    await loadClasses()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除班级失败'))
  }
}

async function addStudent() {
  if (!selectedClassId.value || !addStudentNo.value.trim()) return
  try {
    await addAdminClassStudentApi(selectedClassId.value, addStudentNo.value.trim())
    ElMessage.success('学生已加入班级')
    addStudentNo.value = ''
    await loadClasses()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '添加学生失败'))
  }
}

async function batchAddStudents() {
  if (!selectedClassId.value) return
  const studentNos = batchText.value.split(/[\s,，;；]+/).map((item) => item.trim()).filter(Boolean)
  if (studentNos.length === 0) {
    ElMessage.warning('请输入学号')
    return
  }
  const result = (await batchAdminClassStudentsApi(selectedClassId.value, { studentNos })).data
  ElMessage.success(`批量处理完成：加入 ${result.addedCount} 人，失败 ${result.errorCount} 条`)
  batchText.value = ''
  await loadClasses()
}

async function importStudents() {
  if (!selectedClassId.value) return
  const rows: ImportStudentPayload[] = importText.value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [studentNo, name, phone, email] = line.split(/[,，\t]/).map((item) => item?.trim())
      return { studentNo, name, phone, email, initialPassword: '123456' }
    })
    .filter((row) => row.studentNo && row.name)
  if (rows.length === 0) {
    ElMessage.warning('请输入“学号,姓名,手机号,邮箱”格式的数据')
    return
  }
  const result = (await batchAdminClassStudentsApi(selectedClassId.value, { students: rows })).data
  ElMessage.success(`导入完成：新增 ${result.importedCount} 人，失败 ${result.errorCount} 条`)
  importText.value = ''
  await loadClasses()
}

async function removeStudent(row: ClassStudent) {
  if (!selectedClassId.value) return
  await ElMessageBox.confirm(`确认将 ${row.name} 移出当前班级吗？`, '移出学生', {
    type: 'warning',
    confirmButtonText: '移出',
    cancelButtonText: '取消',
  })
  await removeAdminClassStudentApi(selectedClassId.value, row.studentId)
  ElMessage.success('学生已移出班级')
  await loadClasses()
}

function openTransfer(row: ClassStudent) {
  transferringStudent.value = row
  targetClassId.value = targetClassOptions.value[0]?.id
  transferDialogVisible.value = true
}

async function transferStudent() {
  if (!selectedClassId.value || !transferringStudent.value || !targetClassId.value) return
  await transferAdminClassStudentApi(selectedClassId.value, transferringStudent.value.studentId, targetClassId.value)
  ElMessage.success('学生已转班')
  transferDialogVisible.value = false
  await loadClasses()
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
  <PageHeader title="班级与学生" description="维护班级主数据、学生分班、转班和批量导入。" />

  <section class="admin-toolbar">
    <div class="admin-summary">
      <article><span>班级数</span><strong>{{ classes.length }}</strong></article>
      <article><span>当前班级人数</span><strong>{{ selectedClass?.studentCount ?? 0 }}</strong></article>
      <article><span>学生列表</span><strong>{{ students.length }}</strong></article>
    </div>
    <div class="admin-actions">
      <el-input v-model="keyword" class="keyword-input" placeholder="学院、专业或班级" clearable @keyup.enter="loadClasses" />
      <el-button @click="loadClasses">查询</el-button>
      <el-button type="primary" @click="openCreateClass">新增班级</el-button>
    </div>
  </section>

  <section v-loading="loading" class="class-admin-layout">
    <article class="work-panel class-list-panel">
      <el-table :data="classes" highlight-current-row empty-text="暂无班级" @row-click="selectClass">
        <el-table-column prop="className" label="班级" min-width="150" />
        <el-table-column prop="major" label="专业" min-width="140" />
        <el-table-column prop="studentCount" label="人数" width="70" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button type="primary" link @click.stop="openEditClass(row)">编辑</el-button>
            <el-button type="danger" link :disabled="row.studentCount > 0" @click.stop="removeClass(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <article class="work-panel class-student-panel">
      <div class="section-heading">
        <div>
          <h2>{{ selectedClass?.className || '请选择班级' }}</h2>
          <p>{{ selectedClass ? `${selectedClass.college} / ${selectedClass.major} / ${selectedClass.grade}` : '暂无班级数据' }}</p>
        </div>
      </div>

      <div class="class-student-actions">
        <el-input v-model="addStudentNo" placeholder="输入已有学生学号" clearable />
        <el-button type="primary" :disabled="!selectedClassId" @click="addStudent">加入班级</el-button>
      </div>
      <div class="class-student-actions">
        <el-input v-model="batchText" type="textarea" :rows="2" placeholder="批量加入已有学生：多个学号可用换行、逗号或空格分隔" />
        <el-button :disabled="!selectedClassId" @click="batchAddStudents">批量加入</el-button>
      </div>
      <div class="class-student-actions">
        <el-input v-model="importText" type="textarea" :rows="3" placeholder="批量导入新学生：每行 学号,姓名,手机号,邮箱；默认密码 123456" />
        <el-button :disabled="!selectedClassId" @click="importStudents">批量导入</el-button>
      </div>

      <el-table v-loading="studentLoading" :data="students" empty-text="暂无班级学生">
        <el-table-column prop="studentNo" label="学号" width="120" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="major" label="专业" min-width="140" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="email" label="邮箱" min-width="170" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openTransfer(row)">转班</el-button>
            <el-button type="danger" link @click="removeStudent(row)">移出</el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>
  </section>

  <el-dialog v-model="classDialogVisible" :title="editingClassId ? '编辑班级' : '新增班级'" width="520px">
    <el-form :model="classForm" label-width="84px">
      <el-form-item label="学院"><el-input v-model="classForm.college" /></el-form-item>
      <el-form-item label="专业"><el-input v-model="classForm.major" /></el-form-item>
      <el-form-item label="年级"><el-input v-model="classForm.grade" /></el-form-item>
      <el-form-item label="班级"><el-input v-model="classForm.className" /></el-form-item>
      <el-form-item label="负责人"><el-input v-model="classForm.advisor" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="classDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="saveClass">保存</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="transferDialogVisible" title="学生转班" width="440px">
    <el-form label-width="84px">
      <el-form-item label="学生">
        <span>{{ transferringStudent?.studentNo }} {{ transferringStudent?.name }}</span>
      </el-form-item>
      <el-form-item label="目标班级">
        <el-select v-model="targetClassId" class="full-field" filterable>
          <el-option v-for="item in targetClassOptions" :key="item.id" :label="item.className" :value="item.id" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="transferDialogVisible = false">取消</el-button>
      <el-button type="primary" @click="transferStudent">确认转班</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.class-admin-layout {
  display: grid;
  grid-template-columns: minmax(340px, 0.9fr) minmax(0, 1.7fr);
  gap: 16px;
}

.class-student-actions {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-bottom: 12px;
}

@media (max-width: 1080px) {
  .class-admin-layout {
    grid-template-columns: 1fr;
  }
}
</style>
