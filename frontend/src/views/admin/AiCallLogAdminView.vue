<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { aiCallLogsApi, type AiCallLogRow } from '@/api/ai'

const loading = ref(false)
const rows = ref<AiCallLogRow[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filters = reactive({
  keyword: '',
  username: '',
  functionType: '',
  success: '' as boolean | '',
  level: '',
  range: [] as string[],
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const response = (await aiCallLogsApi({
      keyword: filters.keyword.trim() || undefined,
      username: filters.username.trim() || undefined,
      functionType: filters.functionType || undefined,
      success: filters.success,
      level: filters.level || undefined,
      startAt: filters.range?.[0],
      endAt: filters.range?.[1],
      page: page.value,
      size: size.value,
    })).data
    rows.value = response.records
    page.value = response.page
    size.value = response.size
    total.value = response.total
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, 'AI 调用日志加载失败'))
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void loadData()
}

function reset() {
  filters.keyword = ''
  filters.username = ''
  filters.functionType = ''
  filters.success = ''
  filters.level = ''
  filters.range = []
  search()
}

function handleSizeChange() {
  page.value = 1
  void loadData()
}

function levelTag(level: string) {
  if (level === 'ERROR') return 'danger'
  if (level === 'WARN') return 'warning'
  return 'success'
}

function formatDateTime(value?: string) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-'
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const maybe = error as { response?: { data?: { message?: string } }; message?: string }
  return maybe.response?.data?.message || maybe.message || fallback
}
</script>

<template>
  <PageHeader title="AI 调用日志" description="查看 RAG、SQL、聊天、学业画像和压测解读的调用历史、耗时与错误信息。" />

  <section class="admin-toolbar">
    <div class="admin-actions wrap-actions">
      <el-input v-model="filters.keyword" class="keyword-input" placeholder="关键词 / traceId / 错误" clearable @keyup.enter="search" />
      <el-input v-model="filters.username" class="status-filter" placeholder="用户关键词" clearable @keyup.enter="search" />
      <el-select v-model="filters.functionType" class="status-filter" clearable placeholder="功能类型">
        <el-option label="CHAT" value="CHAT" />
        <el-option label="CHAT_FALLBACK" value="CHAT_FALLBACK" />
        <el-option label="RAG" value="RAG" />
        <el-option label="SQL_GENERATE" value="SQL_GENERATE" />
        <el-option label="SQL_EXECUTE" value="SQL_EXECUTE" />
        <el-option label="LOAD_TEST_ANALYSIS" value="LOAD_TEST_ANALYSIS" />
        <el-option label="ACADEMIC_PROFILE" value="ACADEMIC_PROFILE" />
      </el-select>
      <el-select v-model="filters.success" class="status-filter" clearable placeholder="成功状态">
        <el-option label="成功" :value="true" />
        <el-option label="失败" :value="false" />
      </el-select>
      <el-select v-model="filters.level" class="status-filter" clearable placeholder="等级">
        <el-option label="INFO" value="INFO" />
        <el-option label="WARN" value="WARN" />
        <el-option label="ERROR" value="ERROR" />
      </el-select>
      <el-date-picker
        v-model="filters.range"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ssZ"
        start-placeholder="开始时间"
        end-placeholder="结束时间"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>
  </section>

  <section class="work-panel" v-loading="loading">
    <div class="panel-heading">
      <h2>最近调用</h2>
      <el-button type="primary" @click="loadData">刷新</el-button>
    </div>
    <el-table :data="rows" empty-text="暂无 AI 调用日志">
      <el-table-column label="时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="等级" width="90">
        <template #default="{ row }">
          <el-tag :type="levelTag(row.level)">{{ row.level }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="用户" width="120" />
      <el-table-column prop="functionType" label="功能" width="150" />
      <el-table-column prop="modelName" label="模型" width="160" show-overflow-tooltip />
      <el-table-column prop="serviceMode" label="serviceMode" width="160" show-overflow-tooltip />
      <el-table-column prop="durationMs" label="耗时(ms)" width="100" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="traceId" label="traceId" width="170" show-overflow-tooltip />
      <el-table-column prop="promptSummary" label="输入摘要" min-width="260" show-overflow-tooltip />
      <el-table-column prop="errorMessage" label="错误" min-width="220" show-overflow-tooltip />
    </el-table>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      :total="total"
      @current-change="loadData"
      @size-change="handleSizeChange"
    />
  </section>
</template>

<style scoped>
.wrap-actions {
  flex-wrap: wrap;
  justify-content: flex-start;
}
</style>
