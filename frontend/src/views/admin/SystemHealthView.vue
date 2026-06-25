<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { NexusMetricCard, NexusPageHeader, NexusStatusCard } from '@/components/nexus'
import { systemHealthApi, type SystemHealthItem, type SystemHealthResponse } from '@/api/systemMonitor'

const loading = ref(false)
const health = ref<SystemHealthResponse | null>(null)

const statusText = computed(() => statusLabel(health.value?.overallStatus ?? 'DEGRADED'))
const statusTone = computed(() => toneOf(health.value?.overallStatus ?? 'DEGRADED'))
const upCount = computed(() => health.value?.items.filter((item) => item.status === 'UP').length ?? 0)
const degradedCount = computed(() => health.value?.items.filter((item) => item.status === 'DEGRADED').length ?? 0)
const downCount = computed(() => health.value?.items.filter((item) => item.status === 'DOWN').length ?? 0)

onMounted(loadHealth)

async function loadHealth() {
  loading.value = true
  try {
    health.value = (await systemHealthApi()).data
  } catch {
    ElMessage.error('系统健康检查加载失败，请确认后端服务已启动')
  } finally {
    loading.value = false
  }
}

function statusLabel(status: SystemHealthItem['status']) {
  return {
    UP: '正常',
    DEGRADED: '降级',
    DOWN: '不可用',
  }[status]
}

function toneOf(status: SystemHealthItem['status']) {
  if (status === 'UP') return 'healthy'
  if (status === 'DOWN') return 'down'
  return 'degraded'
}

function metricTone(key: string) {
  if (key.includes('usedMemory')) return 'warning'
  if (key.includes('uploadFree')) return 'success'
  return 'info'
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  return new Date(value).toLocaleString()
}

function metadataRows(item: SystemHealthItem) {
  return Object.entries(item.metadata ?? {}).map(([key, value]) => ({
    key,
    value: value === null || value === undefined || value === '' ? '-' : String(value),
  }))
}
</script>

<template>
  <NexusPageHeader
    title="系统健康中心"
    eyebrow="System Health"
    description="聚合数据库、Redis、AI、上传目录、迁移版本和 JVM 状态，用于运行巡检、答辩演示和故障定位。"
  >
    <template #actions>
      <el-button type="primary" :icon="Refresh" :loading="loading" @click="loadHealth">刷新</el-button>
    </template>
  </NexusPageHeader>

  <section class="health-overview">
    <NexusStatusCard
      title="总体状态"
      :status="statusText"
      :tone="statusTone"
      :description="`最近检查：${formatDateTime(health?.checkedAt)}`"
      meta="降级不等于不可用，页面会展示具体兜底原因"
    />
    <NexusMetricCard label="正常组件" :value="upCount" suffix="项" tone="success" hint="完全可用" />
    <NexusMetricCard label="降级组件" :value="degradedCount" suffix="项" tone="warning" hint="仍可兜底运行" />
    <NexusMetricCard label="不可用组件" :value="downCount" suffix="项" tone="danger" hint="需要处理" />
  </section>

  <section v-loading="loading" class="health-grid">
    <NexusStatusCard
      v-for="item in health?.items ?? []"
      :key="item.key"
      :title="item.name"
      :status="statusLabel(item.status)"
      :tone="toneOf(item.status)"
      :description="item.detail"
      :meta="`耗时 ${item.latencyMs} ms`"
    />
  </section>

  <section class="metric-grid">
    <NexusMetricCard
      v-for="metric in health?.metrics ?? []"
      :key="metric.key"
      :label="metric.label"
      :value="metric.value"
      :suffix="metric.unit"
      :tone="metricTone(metric.key)"
    />
  </section>

  <section class="work-panel health-detail-panel">
    <div class="panel-heading">
      <h2>检查明细</h2>
      <span>{{ health?.items.length ?? 0 }} 个组件</span>
    </div>
    <el-table :data="health?.items ?? []" empty-text="暂无健康检查数据">
      <el-table-column prop="name" label="组件" min-width="150" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 'UP' ? 'success' : row.status === 'DOWN' ? 'danger' : 'warning'">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="detail" label="说明" min-width="220" show-overflow-tooltip />
      <el-table-column prop="latencyMs" label="耗时(ms)" width="110" />
      <el-table-column label="关键元数据" min-width="280">
        <template #default="{ row }">
          <div class="metadata-list">
            <span v-for="meta in metadataRows(row)" :key="meta.key">
              <b>{{ meta.key }}</b>{{ meta.value }}
            </span>
          </div>
        </template>
      </el-table-column>
    </el-table>
  </section>
</template>

<style scoped>
.health-overview,
.health-grid,
.metric-grid {
  display: grid;
  gap: 14px;
  margin-bottom: 18px;
}

.health-overview {
  grid-template-columns: minmax(260px, 1.3fr) repeat(3, minmax(150px, 0.7fr));
}

.health-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.metric-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.health-detail-panel {
  margin-bottom: 18px;
}

.metadata-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.metadata-list span {
  max-width: 260px;
  padding: 4px 7px;
  overflow: hidden;
  color: var(--nexus-text-muted);
  background: var(--nexus-surface-soft);
  border: 1px solid var(--nexus-border);
  border-radius: 6px;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.metadata-list b {
  margin-right: 5px;
  color: var(--nexus-text);
}

@media (max-width: 1180px) {
  .health-overview,
  .health-grid,
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .health-overview,
  .health-grid,
  .metric-grid {
    grid-template-columns: 1fr;
  }
}
</style>
