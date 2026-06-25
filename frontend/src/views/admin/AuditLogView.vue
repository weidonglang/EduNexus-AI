<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/PageHeader.vue'
import { auditLogsApi, type AuditLog } from '@/api/audit'

const loading = ref(false)
const keyword = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<AuditLog[]>([])

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const response = (await auditLogsApi({ keyword: keyword.value.trim(), page: page.value, size: size.value })).data
    rows.value = response.records
    total.value = response.total
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <PageHeader title="操作审计" description="查看课程、审核、密码、权限等关键操作日志。" />
  <section class="admin-toolbar">
    <div class="admin-actions">
      <el-input v-model="keyword" class="keyword-input" placeholder="操作人、动作、对象" clearable />
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>
  </section>
  <section v-loading="loading" class="work-panel">
    <el-table :data="rows" empty-text="暂无审计日志">
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="action" label="动作" width="180" />
      <el-table-column prop="targetType" label="对象" width="130" />
      <el-table-column prop="targetId" label="对象ID" width="120" />
      <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="时间" width="180" />
    </el-table>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      class="table-pagination"
      layout="total, prev, pager, next"
      :total="total"
      @current-change="loadData"
    />
  </section>
</template>
