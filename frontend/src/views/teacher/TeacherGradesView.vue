<script setup lang="ts">
// 教师成绩录入页面。
// 教师只能看到自己任课教学班下的学生，页面使用分页和课程筛选降低数据量，
// 保存时由后端再次校验课程归属和成绩锁定状态。
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  saveTeacherGradeApi,
  teacherGradesApi,
  teacherOfferingsApi,
  type TeacherGradeEntry,
  type TeacherOffering,
} from '@/api/teacher'

const DEFAULT_TERM = '2025-2026-2'

const loading = ref(false)
const savingId = ref<string | null>(null)
const term = ref(DEFAULT_TERM)
const keyword = ref('')
const offeringId = ref<number | undefined>()
const rows = ref<TeacherGradeEntry[]>([])
const offerings = ref<TeacherOffering[]>([])
const page = ref(1)
const pageSize = ref(50)
const total = ref(0)

const editCache = reactive<Record<string, { score?: number; examType: string; gradeStatus: string; reason: string }>>({})

const enteredCount = computed(() => rows.value.filter((row) => row.score !== null && row.score !== undefined).length)

onMounted(loadData)

// 功能：加载教师任课教学班的成绩录入数据。
// 说明：教师端按学期、关键字和分页参数查询学生成绩，页面只展示当前教师相关教学班。
async function loadData() {
  loading.value = true
  try {
    const [gradeResponse, offeringResponse] = await Promise.all([
      teacherGradesApi({
        term: term.value || undefined,
        offeringId: offeringId.value,
        keyword: keyword.value.trim() || undefined,
        page: page.value,
        size: pageSize.value,
      }),
      teacherOfferingsApi(term.value),
    ])
    rows.value = gradeResponse.data.records
    total.value = gradeResponse.data.total
    offerings.value = offeringResponse.data
    syncEditCache()
  } finally {
    loading.value = false
  }
}

// 功能：同步可编辑成绩缓存。
// 说明：表格中的分数输入框先写入本地缓存，点击保存时再提交对应学生课程成绩。
function syncEditCache() {
  rows.value.forEach((row) => {
    editCache[rowKey(row)] = {
      score: row.score ?? undefined,
      examType: row.examType || '正常考试',
      gradeStatus: row.gradeStatus === '未录入' ? '已录入' : row.gradeStatus || '已录入',
      reason: '',
    }
  })
}

function searchRows() {
  page.value = 1
  loadData()
}

function handlePageChange(nextPage: number) {
  page.value = nextPage
  loadData()
}

function handleSizeChange(nextSize: number) {
  pageSize.value = nextSize
  page.value = 1
  loadData()
}

// 功能：保存单个学生成绩。
// 说明：教师录入或修改分数后调用后端接口，后端校验任课关系并更新成绩记录。
async function saveRow(row: TeacherGradeEntry) {
  const cache = editCache[rowKey(row)]
  if (!cache || cache.score === undefined || cache.score === null) {
    ElMessage.warning('请先录入成绩')
    return
  }
  savingId.value = rowKey(row)
  try {
    await saveTeacherGradeApi({
      gradeId: row.gradeId,
      offeringId: row.offeringId,
      studentNo: row.studentNo,
      term: row.term,
      score: cache.score,
      examType: cache.examType,
      gradeStatus: cache.gradeStatus,
      reason: cache.reason || undefined,
    })
    ElMessage.success('成绩已保存')
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存成绩失败'))
  } finally {
    savingId.value = null
  }
}

function rowKey(row: TeacherGradeEntry) {
  return `${row.offeringId}-${row.studentNo}`
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
  <PageHeader title="成绩录入" description="教师按本人任课教学班录入学生成绩，已锁定成绩不能修改。" />

  <section class="admin-toolbar">
    <div class="admin-summary">
      <article><span>记录总数</span><strong>{{ total }}</strong></article>
      <article><span>本页已录入</span><strong>{{ enteredCount }}</strong></article>
      <article><span>本页记录</span><strong>{{ rows.length }}</strong></article>
    </div>
    <div class="admin-actions">
      <el-input v-model="term" class="term-input" placeholder="学期" />
      <el-select v-model="offeringId" class="term-input" clearable filterable placeholder="全部教学班">
        <el-option
          v-for="offering in offerings"
          :key="offering.offeringId"
          :label="`${offering.courseCode} ${offering.courseName}`"
          :value="offering.offeringId"
        />
      </el-select>
      <el-input v-model="keyword" class="keyword-input" placeholder="学号、姓名或课程" clearable @keyup.enter="searchRows" />
      <el-button type="primary" @click="searchRows">查询</el-button>
    </div>
  </section>

  <section v-loading="loading" class="work-panel">
    <el-table :data="rows" empty-text="暂无成绩记录">
      <el-table-column prop="studentNo" label="学号" width="110" />
      <el-table-column prop="studentName" label="姓名" width="100" />
      <el-table-column prop="courseName" label="课程" min-width="150" />
      <el-table-column label="成绩" width="130">
        <template #default="{ row }">
          <el-input-number
            v-model="editCache[rowKey(row)].score"
            :min="0"
            :max="100"
            :disabled="row.locked"
            controls-position="right"
          />
        </template>
      </el-table-column>
      <el-table-column prop="gradePoint" label="绩点" width="80" />
      <el-table-column label="考试类型" width="150">
        <template #default="{ row }">
          <el-select v-model="editCache[rowKey(row)].examType" :disabled="row.locked">
            <el-option label="正常考试" value="正常考试" />
            <el-option label="补考" value="补考" />
            <el-option label="重修" value="重修" />
            <el-option label="缓考" value="缓考" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{ row }">
          <el-select v-model="editCache[rowKey(row)].gradeStatus" :disabled="row.locked">
            <el-option label="已录入" value="已录入" />
            <el-option label="待发布" value="待发布" />
            <el-option label="已发布" value="已发布" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="修改原因" min-width="160">
        <template #default="{ row }">
          <el-input
            v-model="editCache[rowKey(row)].reason"
            :disabled="row.locked || !row.gradeId"
            placeholder="修改已有成绩时必填"
          />
        </template>
      </el-table-column>
      <el-table-column label="锁定" width="80">
        <template #default="{ row }">{{ row.locked ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            link
            :disabled="row.locked"
            :loading="savingId === rowKey(row)"
            @click="saveRow(row)"
          >
            保存
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-bar">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :page-sizes="[20, 50, 100, 200]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </section>
</template>

<style scoped>
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}
</style>
