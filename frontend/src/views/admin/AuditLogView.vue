<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { NexusMetricCard, NexusPageHeader, NexusRiskBadge } from '@/components/nexus'
import { auditLogsApi, type AuditLog } from '@/api/audit'

const loading = ref(false)
const keyword = ref('')
const riskFilter = ref('')
const moduleFilter = ref('')
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<AuditLog[]>([])

const highRiskActions = ['UPDATE_ROLE_MENUS', 'DELETE_FILE', 'SQL_EXECUTE', 'GRADE_UPDATE', 'GRADE_LOCK']
const mediumRiskActions = ['PUBLISH_NOTICE', 'REVIEW_STATUS_CHANGE', 'IMPORT', 'EXPORT']

const highRiskCount = computed(() => rows.value.filter((row) => riskLevel(row) === 'high').length)
const operatorCount = computed(() => new Set(rows.value.map((row) => row.operator)).size)

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    const response = (await auditLogsApi({
      keyword: keyword.value.trim(),
      riskLevel: riskFilter.value,
      module: moduleFilter.value,
      page: page.value,
      size: size.value,
    })).data
    rows.value = response.records
    total.value = response.total
  } finally {
    loading.value = false
  }
}

function riskLevel(row: AuditLog): 'none' | 'low' | 'medium' | 'high' {
  if (row.riskLevel === 'HIGH') return 'high'
  if (row.riskLevel === 'MEDIUM') return 'medium'
  if (row.riskLevel === 'LOW') return 'low'
  const action = row.action.toUpperCase()
  if (highRiskActions.some((keyword) => action.includes(keyword))) {
    return 'high'
  }
  if (mediumRiskActions.some((keyword) => action.includes(keyword))) {
    return 'medium'
  }
  return 'none'
}
</script>

<template>
  <NexusPageHeader title="操作审计" description="查看课程、审核、密码、权限等关键操作日志。" eyebrow="Audit Center">
    <template #actions>
      <el-button type="primary" @click="loadData">刷新</el-button>
    </template>
  </NexusPageHeader>
  <section class="nexus-audit-summary">
    <NexusMetricCard label="当前记录" :value="rows.length" suffix="条" tone="primary" />
    <NexusMetricCard label="高风险操作" :value="highRiskCount" suffix="条" tone="danger" />
    <NexusMetricCard label="涉及操作人" :value="operatorCount" suffix="人" tone="info" />
  </section>
  <section class="admin-toolbar">
    <div class="admin-actions">
      <el-input v-model="keyword" class="keyword-input" placeholder="操作人、动作、对象" clearable />
      <el-select v-model="riskFilter" class="status-filter" placeholder="风险等级" clearable>
        <el-option label="高风险" value="HIGH" />
        <el-option label="中风险" value="MEDIUM" />
        <el-option label="低风险" value="LOW" />
      </el-select>
      <el-select v-model="moduleFilter" class="status-filter" placeholder="模块" clearable>
        <el-option label="成绩" value="GRADE" />
        <el-option label="课程" value="COURSE" />
        <el-option label="申请" value="APPLICATION" />
        <el-option label="权限" value="PERMISSION" />
        <el-option label="数据库" value="DATABASE" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
    </div>
  </section>
  <section v-loading="loading" class="work-panel">
    <el-table :data="rows" empty-text="暂无审计日志">
      <el-table-column prop="operator" label="操作人" width="120" />
      <el-table-column prop="action" label="动作" width="180" />
      <el-table-column prop="targetType" label="对象" width="130" />
      <el-table-column prop="targetId" label="对象ID" width="120" />
      <el-table-column prop="module" label="模块" width="110" />
      <el-table-column label="风险" width="100">
        <template #default="{ row }">
          <NexusRiskBadge :level="riskLevel(row)" />
        </template>
      </el-table-column>
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

<style scoped>
.nexus-audit-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 16px;
}

@media (max-width: 760px) {
  .nexus-audit-summary {
    grid-template-columns: 1fr;
  }
}
</style>
