<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { batchTasksApi, type BatchTaskRow } from '@/api/batchOps'

const loading = ref(false)
const rows = ref<BatchTaskRow[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const taskType = ref('')
const status = ref('')

const statusOptions = ['PENDING', 'RUNNING', 'PARTIAL_SUCCESS', 'SUCCESS', 'FAILED']

onMounted(loadRows)

async function loadRows() {
  loading.value = true
  try {
    const response = await batchTasksApi({
      taskType: taskType.value.trim() || undefined,
      status: status.value || undefined,
      page: page.value,
      size: size.value,
    })
    rows.value = response.data.records
    total.value = response.data.total
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '批量任务加载失败'))
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  taskType.value = ''
  status.value = ''
  page.value = 1
  loadRows()
}

function statusType(value: string) {
  if (value === 'SUCCESS') return 'success'
  if (value === 'FAILED') return 'danger'
  if (value === 'PARTIAL_SUCCESS') return 'warning'
  if (value === 'RUNNING') return 'primary'
  return 'info'
}

function resolveErrorMessage(error: unknown, fallback: string) {
  if (
      typeof error === 'object'
      && error !== null
      && 'response' in error
      && typeof (error as { response?: { data?: { message?: string } } }).response?.data?.message === 'string'
  ) {
    return (error as { response: { data: { message: string } } }).response.data.message
  }
  return fallback
}
</script>

<template>
  <PageHeader
      title="批量任务中心"
      description="集中查看批量导入、批量通知、批量审核、归档清理等任务的状态、成功数和失败明细。"
  />

  <section class="admin-toolbar">
    <div class="admin-actions">
      <el-input
          v-model="taskType"
          class="keyword-input"
          clearable
          placeholder="任务类型"
          @keyup.enter="() => { page = 1; loadRows() }"
      />
      <el-select v-model="status" class="small-select" clearable placeholder="任务状态">
        <el-option v-for="item in statusOptions" :key="item" :label="item" :value="item" />
      </el-select>
      <el-button type="primary" @click="() => { page = 1; loadRows() }">查询</el-button>
      <el-button @click="resetFilters">重置</el-button>
    </div>
  </section>

  <section class="work-panel">
    <el-table v-loading="loading" :data="rows" empty-text="暂无批量任务记录">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="taskType" label="任务类型" min-width="180" />
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column label="状态" width="140">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="successCount" label="成功数" width="100" />
      <el-table-column prop="failureCount" label="失败数" width="100" />
      <el-table-column prop="startedAt" label="开始时间" width="190" />
      <el-table-column prop="endedAt" label="结束时间" width="190" />
      <el-table-column prop="failureDetail" label="失败明细/报告摘要" min-width="260" show-overflow-tooltip />
      <el-table-column prop="reportPath" label="报告路径" min-width="180" show-overflow-tooltip />
    </el-table>

    <el-pagination
        v-model:current-page="page"
        v-model:page-size="size"
        class="table-pagination"
        layout="total, sizes, prev, pager, next"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        @current-change="loadRows"
        @size-change="loadRows"
    />
  </section>
</template>
