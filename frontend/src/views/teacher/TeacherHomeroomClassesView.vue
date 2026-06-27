<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import {
  teacherHomeroomClassesApi,
  teacherHomeroomClassStudentsApi,
  type HomeroomClass,
  type HomeroomClassStudent,
} from '@/api/teacher'

const loading = ref(false)
const studentLoading = ref(false)
const classes = ref<HomeroomClass[]>([])
const students = ref<HomeroomClassStudent[]>([])
const selectedClassId = ref<number>()

const selectedClass = computed(() => classes.value.find((item) => item.classId === selectedClassId.value))

onMounted(loadClasses)

async function loadClasses() {
  loading.value = true
  try {
    classes.value = (await teacherHomeroomClassesApi()).data
    selectedClassId.value = selectedClassId.value && classes.value.some((item) => item.classId === selectedClassId.value)
      ? selectedClassId.value
      : classes.value[0]?.classId
    await loadStudents()
  } finally {
    loading.value = false
  }
}

async function selectClass(row: HomeroomClass) {
  selectedClassId.value = row.classId
  await loadStudents()
}

async function loadStudents() {
  if (!selectedClassId.value) {
    students.value = []
    return
  }
  studentLoading.value = true
  try {
    students.value = (await teacherHomeroomClassStudentsApi(selectedClassId.value)).data
  } finally {
    studentLoading.value = false
  }
}
</script>

<template>
  <PageHeader title="班主任班级" description="查看本人负责班级和学生基础名单。" />

  <section v-loading="loading" class="class-admin-layout">
    <article class="work-panel class-list-panel">
      <el-table :data="classes" highlight-current-row empty-text="暂无负责班级" @row-click="selectClass">
        <el-table-column prop="className" label="班级" min-width="160" />
        <el-table-column prop="major" label="专业" min-width="140" />
        <el-table-column prop="studentCount" label="人数" width="90" />
      </el-table>
    </article>

    <article class="work-panel class-student-panel">
      <div class="section-heading">
        <div>
          <h2>{{ selectedClass?.className || '请选择班级' }}</h2>
          <p>{{ selectedClass ? `${selectedClass.college} / ${selectedClass.major} / ${selectedClass.grade}` : '仅显示本人被指定为班主任的班级' }}</p>
        </div>
        <el-button :disabled="!selectedClassId" @click="loadStudents">刷新</el-button>
      </div>

      <el-table v-loading="studentLoading" :data="students" empty-text="暂无学生">
        <el-table-column prop="studentNo" label="学号" width="140" />
        <el-table-column prop="studentName" label="姓名" width="120" />
        <el-table-column prop="college" label="学院" min-width="160" />
        <el-table-column prop="major" label="专业" min-width="140" />
        <el-table-column prop="grade" label="年级" width="90" />
        <el-table-column prop="status" label="状态" width="100" />
      </el-table>
    </article>
  </section>
</template>
