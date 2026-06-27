<script setup lang="ts">
// 压测历史报告页面。
// 读取 reports 目录下自动生成的 JSON/HTML 报告，用表格汇总请求数、成功数、
// FULL 数量、吞吐量和延迟指标，方便答辩时回看不同压测结果。
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  loadTestReportHtmlApi,
  loadTestReportsApi,
  type LoadTestReportRow,
} from '@/api/systemMonitor'
import { analyzeLoadTestReportApi, type LoadTestAnalysisResponse } from '@/api/ai'

const loading = ref(false)
const rows = ref<LoadTestReportRow[]>([])
const apiError = ref('')
const analyzing = ref(false)
const analysis = ref<LoadTestAnalysisResponse>()
const analysisError = ref('')
const analysisReportName = ref('')
const analysisPanel = ref<HTMLElement>()
const page = ref(1)
const size = ref(10)
const total = ref(0)

const latest = computed(() => rows.value[0])
const analysisConclusionHtml = computed(() => formatAnalysisMarkdown(analysis.value?.conclusion || ''))
const totals = computed(() => {
  return rows.value.reduce(
    (acc, row) => {
      acc.requests += row.requestCount
      acc.success += row.successCount
      acc.full += row.fullCount
      return acc
    },
    { requests: 0, success: 0, full: 0 },
  )
})

onMounted(loadData)

async function loadData() {
  loading.value = true
  apiError.value = ''
  try {
    const response = (await loadTestReportsApi({ page: page.value, size: size.value })).data
    rows.value = response.records
    page.value = response.page
    size.value = response.size
    total.value = response.total
  } catch {
    rows.value = []
    total.value = 0
    apiError.value = '后端接口连接失败，请确认 Spring Boot 本地 http://localhost:8080 或 Docker http://localhost:8088 已启动。'
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
  void loadData()
}

async function openReport(row: LoadTestReportRow) {
  if (!row.htmlName) {
    ElMessage.warning('这条记录没有对应的 HTML 报告')
    return
  }
  const html = await loadTestReportHtmlApi(row.htmlName)
  const blob = new Blob([html], { type: 'text/html;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  window.open(url, '_blank', 'noopener,noreferrer')
  window.setTimeout(() => URL.revokeObjectURL(url), 60000)
}

async function analyzeReport(row: LoadTestReportRow) {
  analysis.value = undefined
  analysisError.value = ''
  analysisReportName.value = row.jsonName
  analyzing.value = true
  try {
    analysis.value = (await analyzeLoadTestReportApi(row.jsonName)).data
    ElMessage.success('AI 压测报告解读已生成')
    await nextTick()
    analysisPanel.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } catch (error) {
    analysisError.value = resolveErrorMessage(error, 'AI 解读失败，请确认后端、ai-service 和登录权限正常。')
    ElMessage.error(analysisError.value)
  } finally {
    analyzing.value = false
  }
}

function percent(row: LoadTestReportRow) {
  if (!row.requestCount) return '0.00%'
  return `${((row.successCount / row.requestCount) * 100).toFixed(2)}%`
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const maybe = error as {
    response?: { status?: number; data?: { message?: string } }
    message?: string
  }
  if (maybe.response?.status === 401) return '登录状态已失效，请重新登录。'
  if (maybe.response?.status === 403) return '当前账号没有压测报告 AI 解读权限，请使用管理员账号。'
  return maybe.response?.data?.message || maybe.message || fallback
}

function formatAnalysisMarkdown(value: string) {
  return value
    .split(/\r?\n/)
    .map((line) => {
      const trimmed = line.trim()
      if (!trimmed) return ''
      if (/^-{3,}$/.test(trimmed)) return '<hr />'
      if (trimmed.startsWith('#### ')) return `<h4>${inlineMarkdown(trimmed.slice(5))}</h4>`
      if (trimmed.startsWith('### ')) return `<h3>${inlineMarkdown(trimmed.slice(4))}</h3>`
      if (/^\d+\.\s+/.test(trimmed)) {
        return `<p class="analysis-numbered">${inlineMarkdown(trimmed)}</p>`
      }
      if (trimmed.startsWith('- ')) return `<p class="analysis-bullet">${inlineMarkdown(trimmed.slice(2))}</p>`
      return `<p>${inlineMarkdown(trimmed)}</p>`
    })
    .join('')
}

function inlineMarkdown(value: string) {
  return escapeHtml(value).replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
</script>

<template>
  <PageHeader title="压测历史报告" description="查看 reports 目录中自动生成的万人抢课压测 JSON 和 HTML 报告。" />

  <section class="admin-toolbar">
    <div class="admin-summary">
      <article>
        <span>报告数量</span>
        <strong>{{ total }}</strong>
      </article>
      <article>
        <span>当前页请求</span>
        <strong>{{ totals.requests }}</strong>
      </article>
      <article>
        <span>当前页成功</span>
        <strong>{{ totals.success }}</strong>
      </article>
    </div>
    <div class="admin-actions">
      <el-button type="primary" @click="loadData">刷新列表</el-button>
      <el-button v-if="latest" :loading="analyzing" @click="analyzeReport(latest)">AI 解读最新报告</el-button>
    </div>
  </section>

  <section v-if="apiError" class="report-warning">
    {{ apiError }}
  </section>

  <section v-if="latest" class="report-highlight">
    <div>
      <span>最新报告</span>
      <strong>{{ latest.modifiedAt }}</strong>
      <small>{{ latest.smartMode }} / {{ latest.concurrency }} 并发 / Redis {{ latest.redisReachable ? '正常' : '未连接' }}</small>
    </div>
    <div class="report-bars">
      <div>
        <span>成功 {{ latest.successCount }}</span>
        <i :style="{ width: percent(latest) }" />
      </div>
      <div>
        <span>满员 {{ latest.fullCount }}</span>
        <i class="full" :style="{ width: latest.requestCount ? `${(latest.fullCount / latest.requestCount) * 100}%` : '0%' }" />
      </div>
    </div>
  </section>

  <section v-if="analysisError" class="report-warning">
    {{ analysisReportName }} 解读失败：{{ analysisError }}
  </section>

  <section v-if="analysis" ref="analysisPanel" class="work-panel ai-analysis">
    <div class="panel-heading">
      <h2>AI 压测报告解读</h2>
      <el-tag>{{ analysis.serviceMode }}</el-tag>
    </div>
    <p class="analysis-file">报告文件：{{ analysisReportName }}</p>
    <div class="analysis-conclusion" v-html="analysisConclusionHtml" />
    <div class="analysis-grid">
      <article>
        <h3>风险等级</h3>
        <strong>{{ analysis.riskLevel }}</strong>
      </article>
      <article>
        <h3>瓶颈判断</h3>
        <ul>
          <li v-for="item in analysis.bottlenecks" :key="item">{{ item }}</li>
        </ul>
      </article>
      <article>
        <h3>优化建议</h3>
        <ul>
          <li v-for="item in analysis.suggestions" :key="item">{{ item }}</li>
        </ul>
      </article>
    </div>
  </section>

  <section v-loading="loading" class="work-panel">
    <div class="panel-heading">
      <h2>历史报告</h2>
      <span>{{ total }} 条</span>
    </div>
    <el-table :data="rows" empty-text="reports 目录暂无压测报告">
      <el-table-column prop="modifiedAt" label="生成时间" width="170" />
      <el-table-column prop="smartMode" label="模式" width="110" />
      <el-table-column prop="requestCount" label="请求数" width="110" />
      <el-table-column prop="successCount" label="成功" width="100" />
      <el-table-column prop="fullCount" label="满员" width="100" />
      <el-table-column label="成功率" width="110">
        <template #default="{ row }">{{ percent(row) }}</template>
      </el-table-column>
      <el-table-column label="Redis" width="100">
        <template #default="{ row }">
          <el-tag :type="row.redisReachable ? 'success' : 'warning'">
            {{ row.redisReachable ? '正常' : '未连接' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="吞吐量" width="130">
        <template #default="{ row }">{{ row.throughput.toFixed(2) }} /s</template>
      </el-table-column>
      <el-table-column label="平均响应" width="130">
        <template #default="{ row }">{{ row.avgLatency.toFixed(1) }} ms</template>
      </el-table-column>
      <el-table-column label="P95" width="120">
        <template #default="{ row }">{{ row.p95.toFixed(1) }} ms</template>
      </el-table-column>
      <el-table-column prop="jsonName" label="JSON 文件" min-width="260" show-overflow-tooltip />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :disabled="!row.htmlName" @click="openReport(row)">预览</el-button>
          <el-button
            type="primary"
            link
            :loading="analyzing && analysisReportName === row.jsonName"
            :disabled="analyzing && analysisReportName !== row.jsonName"
            @click="analyzeReport(row)"
          >
            AI 解读
          </el-button>
        </template>
      </el-table-column>
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
.report-highlight {
  display: grid;
  grid-template-columns: minmax(320px, 0.55fr) minmax(0, 1fr);
  gap: 18px;
  margin-bottom: 18px;
  padding: 18px;
  background: #ffffff;
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: 0 10px 26px rgba(28, 45, 65, 0.05);
}

.report-warning {
  margin-bottom: 18px;
  padding: 12px 14px;
  color: #9a3412;
  background: #fff7ed;
  border: 1px solid #fed7aa;
  border-radius: 8px;
}

.report-highlight span,
.report-highlight strong,
.report-highlight small {
  display: block;
}

.report-highlight span,
.report-highlight small {
  color: var(--muted);
}

.report-highlight strong {
  margin-top: 8px;
  font-size: 24px;
}

.report-highlight small {
  margin-top: 8px;
}

.report-bars {
  display: grid;
  gap: 12px;
  align-content: center;
}

.report-bars div {
  display: grid;
  gap: 7px;
}

.report-bars div::after {
  content: "";
  height: 10px;
  background: #e4edf0;
  border-radius: 999px;
  grid-row: 2;
}

.report-bars i {
  height: 10px;
  background: var(--success);
  border-radius: 999px;
  grid-row: 2;
  z-index: 1;
}

.report-bars i.full {
  background: var(--accent);
}

.ai-analysis {
  margin-bottom: 18px;
}

.analysis-file {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 13px;
}

.analysis-conclusion {
  padding: 16px 18px;
  background: #f8fafc;
  border: 1px solid #dbeafe;
  border-radius: 8px;
  color: #1f2937;
  line-height: 1.75;
}

.analysis-conclusion :deep(h3),
.analysis-conclusion :deep(h4),
.analysis-conclusion :deep(p) {
  margin: 0;
}

.analysis-conclusion :deep(h3) {
  margin-top: 18px;
  font-size: 18px;
  color: #0f172a;
}

.analysis-conclusion :deep(h3:first-child) {
  margin-top: 0;
}

.analysis-conclusion :deep(h4) {
  margin-top: 14px;
  font-size: 16px;
  color: #1d4ed8;
}

.analysis-conclusion :deep(p) {
  margin-top: 8px;
}

.analysis-conclusion :deep(.analysis-bullet) {
  padding-left: 18px;
  position: relative;
}

.analysis-conclusion :deep(.analysis-bullet::before) {
  content: "";
  position: absolute;
  left: 4px;
  top: 0.78em;
  width: 5px;
  height: 5px;
  background: #2563eb;
  border-radius: 50%;
}

.analysis-conclusion :deep(.analysis-numbered) {
  font-weight: 600;
}

.analysis-conclusion :deep(hr) {
  margin: 16px 0;
  border: 0;
  border-top: 1px solid #dbe3ef;
}

.analysis-conclusion :deep(strong) {
  font-weight: 700;
  color: #111827;
}

.analysis-grid {
  display: grid;
  grid-template-columns: 0.4fr 1fr 1fr;
  gap: 16px;
  margin-top: 14px;
}

.analysis-grid article {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 14px;
}

.analysis-grid h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.analysis-grid strong {
  font-size: 22px;
}

@media (max-width: 1000px) {
  .report-highlight {
    grid-template-columns: 1fr;
  }
}
</style>
