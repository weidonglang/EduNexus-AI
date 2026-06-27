<script setup lang="ts">
// 只读数据库浏览页面。
// 用于实时查看数据库表结构、索引、外键和分页数据预览，不提供任何增删改和任意 SQL 执行能力。
import { computed, nextTick, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { EChartsOption } from 'echarts'
import PageHeader from '@/components/PageHeader.vue'
import ChartPanel from '@/components/charts/ChartPanel.vue'
import DashboardCard from '@/components/charts/DashboardCard.vue'
import ErGraphChart from '@/components/charts/ErGraphChart.vue'
import FieldTypePieChart from '@/components/charts/FieldTypePieChart.vue'
import QueryResultChart from '@/components/charts/QueryResultChart.vue'
import SqlPerformanceChart from '@/components/charts/SqlPerformanceChart.vue'
import TableDataBarChart from '@/components/charts/TableDataBarChart.vue'
import {
  databaseColumnsApi,
  databaseConnectionApi,
  databaseDashboardApi,
  databaseErGraphApi,
  databaseExportCsvUrl,
  databaseForeignKeysApi,
  databaseHistoryApi,
  databaseIndexesApi,
  databasePreviewApi,
  databaseRunTemplateApi,
  databaseTablesApi,
  databaseTemplateExportCsvUrl,
  databaseTemplatesApi,
  databaseTreeApi,
  type DatabaseConnectionInfo,
  type DatabaseColumnInfo,
  type DatabaseDashboardData,
  type DatabaseErGraph,
  type DatabaseForeignKeyInfo,
  type DatabaseHistoryRow,
  type DatabaseIndexInfo,
  type DatabasePreviewRow,
  type DatabaseQueryTemplate,
  type DatabaseTableInfo,
  type DatabaseTree,
} from '@/api/databaseBrowser'

interface DbTreeNode {
  id: string
  label: string
  tableName?: string
  children?: DbTreeNode[]
}

const loadingTables = ref(false)
const loadingDetail = ref(false)
const tableKeyword = ref('')
const moduleFilter = ref('')
const activeTab = ref('columns')
const selectedTable = ref('')
const tables = ref<DatabaseTableInfo[]>([])
const connection = ref<DatabaseConnectionInfo>()
const dashboard = ref<DatabaseDashboardData>()
const tree = ref<DatabaseTree>()
const erGraph = ref<DatabaseErGraph>()
const columns = ref<DatabaseColumnInfo[]>([])
const indexes = ref<DatabaseIndexInfo[]>([])
const foreignKeys = ref<DatabaseForeignKeyInfo[]>([])
const historyRows = ref<DatabaseHistoryRow[]>([])
const previewRows = ref<DatabasePreviewRow[]>([])
const queryTemplates = ref<DatabaseQueryTemplate[]>([])
const selectedTemplate = ref('')
const templateKeyword = ref('')
const templateTerm = ref('')
const templateRows = ref<DatabasePreviewRow[]>([])
const templatePage = ref(1)
const templateSize = ref(20)
const templateTotal = ref(0)
const loadingTemplate = ref(false)
const previewPage = ref(1)
const previewSize = ref(20)
const previewTotal = ref(0)
const previewKeyword = ref('')
const previewSortBy = ref('')
const previewSortDir = ref('asc')
const historyPage = ref(1)
const historySize = ref(20)
const historyTotal = ref(0)
const apiError = ref('')
const loadingDashboard = ref(false)

const modules = computed(() => Array.from(new Set(tables.value.map((item) => item.module))).sort())
const totalRows = computed(() => tables.value.reduce((sum, item) => sum + Number(item.rowCount || 0), 0))
const selectedTableInfo = computed(() => tables.value.find((item) => item.tableName === selectedTable.value))

const treeData = computed<DbTreeNode[]>(() => {
  if (!tree.value) return []

  return [{
    id: tree.value.databaseName,
    label: tree.value.databaseName,
    children: [
      ...tree.value.tables.map((table) => ({
        id: `table:${table.tableName}`,
        label: `${table.tableName} (${table.module})`,
        tableName: table.tableName,
        children: [
          {
            id: `columns:${table.tableName}`,
            label: '字段',
            children: table.columns.map((column) => ({
              id: `column:${table.tableName}:${column.columnName}`,
              label: `${column.primaryKey ? 'PK ' : ''}${column.columnName} ${column.columnType}`,
            })),
          },
          {
            id: `indexes:${table.tableName}`,
            label: '索引',
            children: table.indexes.map((index) => ({
              id: `index:${table.tableName}:${index.indexName}:${index.columnName}`,
              label: `${index.uniqueIndex ? '唯一 ' : ''}${index.indexName}.${index.columnName}`,
            })),
          },
        ],
      })),
      {
        id: 'views',
        label: '视图',
        children: tree.value.views.map((view) => ({
          id: `view:${view}`,
          label: view,
        })),
      },
    ],
  }]
})

const previewColumns = computed(() => {
  const firstRow = previewRows.value[0]
  return firstRow ? Object.keys(firstRow) : columns.value.map((item) => item.columnName)
})

const templateColumns = computed(() => {
  const firstRow = templateRows.value[0]
  return firstRow ? Object.keys(firstRow) : []
})

const sqlStatusOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [{
    type: 'pie',
    radius: ['48%', '72%'],
    center: ['50%', '44%'],
    data: dashboard.value?.sqlStatus ?? [],
    label: { formatter: '{b}: {d}%' },
  }],
}))

const importQualityOption = computed<EChartsOption>(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 48, right: 20, top: 24, bottom: 52 },
  xAxis: {
    type: 'category',
    data: dashboard.value?.importQuality.map((item) => item.name) ?? [],
  },
  yAxis: { type: 'value' },
  series: [{
    type: 'bar',
    data: dashboard.value?.importQuality.map((item) => item.value) ?? [],
    itemStyle: { color: '#7c3aed' },
  }],
}))

onMounted(async () => {
  await Promise.all([
    loadConnection(),
    loadDashboard(),
    loadTables(),
    loadTemplates(),
    loadTree(),
    loadErGraph(),
    loadHistory(),
  ])
})

async function loadConnection() {
  connection.value = (await databaseConnectionApi()).data
}

async function loadTemplates() {
  queryTemplates.value = (await databaseTemplatesApi()).data
  if (!selectedTemplate.value && queryTemplates.value.length) {
    selectedTemplate.value = queryTemplates.value[0].code
  }
}

async function loadDashboard() {
  loadingDashboard.value = true
  try {
    dashboard.value = (await databaseDashboardApi()).data
  } finally {
    loadingDashboard.value = false
  }
}

async function loadTables() {
  loadingTables.value = true
  apiError.value = ''

  try {
    const response = await databaseTablesApi({
      module: moduleFilter.value || undefined,
      keyword: tableKeyword.value.trim() || undefined,
    })

    tables.value = response.data

    if (!selectedTable.value && tables.value.length) {
      await selectTable(tables.value[0])
    } else if (selectedTable.value && !tables.value.some((item) => item.tableName === selectedTable.value)) {
      selectedTable.value = ''
      clearDetail()
    }
  } catch (error) {
    apiError.value = resolveErrorMessage(error, '数据库只读浏览接口不可用，请确认后端已经重启到最新代码')
    tables.value = []
    selectedTable.value = ''
    clearDetail()
    ElMessage.error(apiError.value)
  } finally {
    loadingTables.value = false
  }
}

async function loadTree() {
  tree.value = (await databaseTreeApi({
    keyword: tableKeyword.value.trim() || undefined,
  })).data
}

async function loadErGraph() {
  erGraph.value = (await databaseErGraphApi()).data
}

async function selectTable(row: DatabaseTableInfo) {
  selectedTable.value = row.tableName
  previewPage.value = 1
  activeTab.value = 'columns'
  await loadDetail()
}

async function loadDetail() {
  if (!selectedTable.value) return

  loadingDetail.value = true

  try {
    const [columnResponse, indexResponse, foreignKeyResponse] = await Promise.all([
      databaseColumnsApi(selectedTable.value),
      databaseIndexesApi(selectedTable.value),
      databaseForeignKeysApi(selectedTable.value),
    ])

    columns.value = columnResponse.data
    indexes.value = indexResponse.data
    foreignKeys.value = foreignKeyResponse.data

    await loadPreview()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '数据库信息加载失败'))
  } finally {
    loadingDetail.value = false
  }
}

async function loadPreview() {
  if (!selectedTable.value) return

  const response = await databasePreviewApi(selectedTable.value, {
    page: previewPage.value,
    size: previewSize.value,
    keyword: previewKeyword.value.trim() || undefined,
    sortBy: previewSortBy.value || undefined,
    sortDir: previewSortDir.value,
  })

  previewRows.value = response.data.records
  previewTotal.value = response.data.total
}

async function runTemplate() {
  if (!selectedTemplate.value) return

  loadingTemplate.value = true
  try {
    const response = await databaseRunTemplateApi(selectedTemplate.value, {
      keyword: templateKeyword.value.trim() || undefined,
      term: templateTerm.value.trim() || undefined,
      page: templatePage.value,
      size: templateSize.value,
    })
    templateRows.value = response.data.records
    templateTotal.value = response.data.total
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '查询模板执行失败'))
    templateRows.value = []
    templateTotal.value = 0
  } finally {
    loadingTemplate.value = false
  }
}

async function refreshAll() {
  await loadDashboard()
  await loadTables()
  await loadTemplates()
  await loadTree()
  await loadErGraph()
  await loadHistory()

  if (selectedTable.value) {
    await loadDetail()
  }
}

function exportTemplateCsv() {
  if (!selectedTemplate.value) return

  window.open(databaseTemplateExportCsvUrl(selectedTemplate.value, {
    keyword: templateKeyword.value.trim() || undefined,
    term: templateTerm.value.trim() || undefined,
  }), '_blank')
}

async function loadHistory() {
  const response = await databaseHistoryApi({
    page: historyPage.value,
    size: historySize.value,
  })

  historyRows.value = response.data.records
  historyTotal.value = response.data.total
}

async function handleTabChange(tabName: string | number) {
  if (tabName !== 'er') return

  if (!erGraph.value) {
    await loadErGraph()
  }

  await nextTick()

  requestAnimationFrame(() => {
    window.dispatchEvent(new Event('resize'))
  })
}

function sortPreview({ prop, order }: { prop: string; order: string | null }) {
  previewSortBy.value = order ? prop : ''
  previewSortDir.value = order === 'descending' ? 'desc' : 'asc'
  previewPage.value = 1
  loadPreview()
}

function exportCsv() {
  if (!selectedTable.value) return

  window.open(databaseExportCsvUrl(selectedTable.value, {
    keyword: previewKeyword.value.trim() || undefined,
    sortBy: previewSortBy.value || undefined,
    sortDir: previewSortDir.value,
  }), '_blank')
}

function handleTreeNodeClick(node: DbTreeNode) {
  if (node.tableName) {
    selectTable({ tableName: node.tableName } as DatabaseTableInfo)
  }
}

function clearDetail() {
  columns.value = []
  indexes.value = []
  foreignKeys.value = []
  previewRows.value = []
  previewTotal.value = 0
}

function formatValue(value: unknown) {
  if (value === null || value === undefined) return ''
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
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
      title="数据库只读浏览"
      description="实时查看表结构、字段、主键外键、索引和分页数据预览，不提供增删改操作。"
  />

  <section class="admin-toolbar">
    <div class="admin-summary">
      <article>
        <span>表数量</span>
        <strong>{{ tables.length }}</strong>
      </article>
      <article>
        <span>累计行数</span>
        <strong>{{ totalRows }}</strong>
      </article>
      <article>
        <span>当前表</span>
        <strong>{{ selectedTable || '-' }}</strong>
      </article>
      <article>
        <span>连接</span>
        <strong>{{ connection?.databaseName || '-' }}</strong>
      </article>
    </div>

    <div class="admin-actions">
      <el-select
          v-model="moduleFilter"
          class="small-select"
          clearable
          placeholder="全部分类"
          @change="loadTables"
      >
        <el-option
            v-for="item in modules"
            :key="item"
            :label="item"
            :value="item"
        />
      </el-select>

      <el-input
          v-model="tableKeyword"
          class="keyword-input"
          clearable
          placeholder="搜索表名或说明"
          @keyup.enter="loadTables"
      />

      <el-button type="primary" @click="loadTables">查询</el-button>
      <el-button @click="refreshAll">刷新</el-button>
    </div>
  </section>

  <el-alert
      v-if="apiError"
      class="inline-alert"
      type="error"
      :closable="false"
      :title="apiError"
      description="如果刚刚新增了数据库只读浏览功能，需要停止旧的 Spring Boot 进程，再重新启动后端。"
  />

  <section class="visual-dashboard" v-loading="loadingDashboard">
    <div class="visual-card-grid">
      <DashboardCard
          label="数据表数量"
          :value="dashboard?.stats.tableCount ?? tables.length"
          caption="information_schema.tables"
      />
      <DashboardCard
          label="字段总数"
          :value="dashboard?.stats.fieldCount ?? 0"
          caption="字段结构规模"
      />
      <DashboardCard
          label="索引数量"
          :value="dashboard?.stats.indexCount ?? 0"
          caption="主键、普通索引、唯一索引"
      />
      <DashboardCard
          label="外键数量"
          :value="dashboard?.stats.foreignKeyCount ?? 0"
          caption="表关系约束"
      />
      <DashboardCard
          label="累计记录数"
          :value="dashboard?.stats.totalRows ?? totalRows"
          caption="所有业务表合计"
      />
      <DashboardCard
          label="近24小时操作"
          :value="dashboard?.stats.recentSqlCount ?? 0"
          caption="数据库浏览审计记录"
      />
      <DashboardCard
          label="执行成功率"
          :value="`${dashboard?.stats.sqlSuccessRate ?? 100}%`"
          caption="安全只读操作"
      />
    </div>

    <div class="visual-chart-grid">
      <TableDataBarChart
          :rows="dashboard?.tableRows ?? []"
          :loading="loadingDashboard"
      />

      <FieldTypePieChart
          :rows="dashboard?.fieldTypes ?? []"
          :loading="loadingDashboard"
      />

      <SqlPerformanceChart
          :trend="dashboard?.sqlTrend ?? []"
          :ranking="dashboard?.actionRanking ?? []"
          :loading="loadingDashboard"
      />

      <ChartPanel
          title="SQL执行成功率"
          :option="sqlStatusOption"
          :loading="loadingDashboard"
          :empty="!(dashboard?.sqlStatus?.length)"
      />

      <ChartPanel
          title="导入质量分析示例"
          :option="importQualityOption"
          :loading="loadingDashboard"
          :empty="!(dashboard?.importQuality?.length)"
      />
    </div>
  </section>

  <section class="database-browser">
    <article class="work-panel database-table-list" v-loading="loadingTables">
      <el-tabs>
        <el-tab-pane label="对象树">
          <el-tree
              v-if="tree"
              class="db-tree"
              node-key="id"
              :data="treeData"
              default-expand-all
              @node-click="handleTreeNodeClick"
          />
        </el-tab-pane>

        <el-tab-pane label="表列表">
          <el-table
              :data="tables"
              height="620"
              highlight-current-row
              empty-text="暂无表"
              @row-click="selectTable"
          >
            <el-table-column
                prop="tableName"
                label="表名"
                min-width="170"
                show-overflow-tooltip
            />
            <el-table-column
                prop="module"
                label="分类"
                width="96"
            />
            <el-table-column
                prop="rowCount"
                label="行数"
                width="90"
                sortable
            />
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </article>

    <article class="work-panel database-detail" v-loading="loadingDetail">
      <div class="section-heading">
        <div>
          <h2>{{ selectedTable || '请选择数据表' }}</h2>
          <p v-if="selectedTableInfo">
            {{ selectedTableInfo.module }} · {{ selectedTableInfo.comment || '暂无表注释' }}
          </p>
        </div>
      </div>

      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="字段结构" name="columns">
          <el-table :data="columns" empty-text="暂无字段">
            <el-table-column
                prop="ordinalPosition"
                label="#"
                width="64"
            />
            <el-table-column
                prop="columnName"
                label="字段名"
                min-width="150"
            />
            <el-table-column
                prop="columnType"
                label="类型"
                min-width="150"
            />
            <el-table-column
                prop="nullable"
                label="可空"
                width="80"
            />
            <el-table-column
                prop="columnKey"
                label="键"
                width="80"
            />
            <el-table-column
                prop="defaultValue"
                label="默认值"
                min-width="120"
                show-overflow-tooltip
            />
            <el-table-column
                prop="extra"
                label="附加"
                min-width="120"
                show-overflow-tooltip
            />
            <el-table-column
                prop="comment"
                label="说明"
                min-width="180"
                show-overflow-tooltip
            />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="索引" name="indexes">
          <el-table :data="indexes" empty-text="暂无索引">
            <el-table-column
                prop="indexName"
                label="索引名"
                min-width="160"
            />
            <el-table-column
                prop="columnName"
                label="字段"
                min-width="150"
            />
            <el-table-column label="唯一" width="80">
              <template #default="{ row }">
                {{ row.uniqueIndex ? '是' : '否' }}
              </template>
            </el-table-column>
            <el-table-column
                prop="sequence"
                label="顺序"
                width="80"
            />
            <el-table-column
                prop="indexType"
                label="类型"
                width="120"
            />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="外键关系" name="foreignKeys">
          <el-table :data="foreignKeys" empty-text="暂无外键">
            <el-table-column
                prop="constraintName"
                label="约束名"
                min-width="180"
            />
            <el-table-column
                prop="columnName"
                label="本表字段"
                min-width="140"
            />
            <el-table-column
                prop="referencedTableName"
                label="关联表"
                min-width="150"
            />
            <el-table-column
                prop="referencedColumnName"
                label="关联字段"
                min-width="130"
            />
            <el-table-column
                prop="updateRule"
                label="更新规则"
                width="110"
            />
            <el-table-column
                prop="deleteRule"
                label="删除规则"
                width="110"
            />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="数据预览" name="preview">
          <div class="admin-actions preview-actions">
            <el-input
                v-model="previewKeyword"
                class="keyword-input"
                clearable
                placeholder="只读关键词搜索"
                @keyup.enter="loadPreview"
            />
            <el-button type="primary" @click="loadPreview">
              查询数据
            </el-button>
            <el-button @click="exportCsv">
              导出CSV
            </el-button>
          </div>

          <el-table
              :data="previewRows"
              empty-text="暂无数据"
              height="420"
              @sort-change="sortPreview"
          >
            <el-table-column
                v-for="column in previewColumns"
                :key="column"
                :prop="column"
                :label="column"
                min-width="140"
                sortable="custom"
                show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ formatValue(row[column]) }}
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
              v-model:current-page="previewPage"
              v-model:page-size="previewSize"
              class="table-pagination"
              layout="total, sizes, prev, pager, next"
              :page-sizes="[10, 20, 50, 100]"
              :total="previewTotal"
              @current-change="loadPreview"
              @size-change="loadPreview"
          />

          <QueryResultChart :rows="previewRows" />
        </el-tab-pane>

        <el-tab-pane label="查询模板" name="templates">
          <div class="admin-actions preview-actions">
            <el-select
                v-model="selectedTemplate"
                class="template-select"
                placeholder="选择只读模板"
                @change="() => { templatePage = 1; runTemplate() }"
            >
              <el-option
                  v-for="item in queryTemplates"
                  :key="item.code"
                  :label="`${item.title} · ${item.module}`"
                  :value="item.code"
              />
            </el-select>
            <el-input
                v-model="templateKeyword"
                class="keyword-input"
                clearable
                placeholder="课程/学生/班级关键词"
                @keyup.enter="() => { templatePage = 1; runTemplate() }"
            />
            <el-input
                v-model="templateTerm"
                class="small-input"
                clearable
                placeholder="学期或日期"
                @keyup.enter="() => { templatePage = 1; runTemplate() }"
            />
            <el-button type="primary" :loading="loadingTemplate" @click="() => { templatePage = 1; runTemplate() }">
              执行模板
            </el-button>
            <el-button @click="exportTemplateCsv">
              导出CSV
            </el-button>
          </div>

          <el-table
              v-loading="loadingTemplate"
              :data="templateRows"
              empty-text="请选择模板并执行查询"
              height="420"
          >
            <el-table-column
                v-for="column in templateColumns"
                :key="column"
                :prop="column"
                :label="column"
                min-width="140"
                show-overflow-tooltip
            >
              <template #default="{ row }">
                {{ formatValue(row[column]) }}
              </template>
            </el-table-column>
          </el-table>

          <el-pagination
              v-model:current-page="templatePage"
              v-model:page-size="templateSize"
              class="table-pagination"
              layout="total, sizes, prev, pager, next"
              :page-sizes="[10, 20, 50, 100]"
              :total="templateTotal"
              @current-change="runTemplate"
              @size-change="runTemplate"
          />
        </el-tab-pane>

        <el-tab-pane label="ER关系" name="er">
          <ErGraphChart
              v-if="activeTab === 'er'"
              :key="`er-${erGraph?.nodes?.length ?? 0}-${erGraph?.relations?.length ?? 0}`"
              :graph="erGraph"
          />

          <div class="er-summary">
            <article
                v-for="node in erGraph?.nodes ?? []"
                :key="node.tableName"
                class="er-node"
            >
              <h3>{{ node.tableName }}</h3>
              <p>{{ node.module }}</p>
              <ul>
                <li
                    v-for="column in node.columns.slice(0, 8)"
                    :key="column.columnName"
                >
                  <span>{{ column.primaryKey ? 'PK' : column.foreignKey ? 'FK' : '' }}</span>
                  {{ column.columnName }}
                </li>
              </ul>
            </article>
          </div>

          <el-table :data="erGraph?.relations ?? []" empty-text="暂无外键连线">
            <el-table-column
                prop="sourceTable"
                label="来源表"
                min-width="150"
            />
            <el-table-column
                prop="sourceColumn"
                label="来源字段"
                min-width="130"
            />
            <el-table-column
                prop="targetTable"
                label="目标表"
                min-width="150"
            />
            <el-table-column
                prop="targetColumn"
                label="目标字段"
                min-width="130"
            />
            <el-table-column
                prop="label"
                label="关系"
                width="100"
            />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="历史操作" name="history">
          <el-table :data="historyRows" empty-text="暂无历史操作">
            <el-table-column
                prop="operator"
                label="操作人"
                width="110"
            />
            <el-table-column
                prop="action"
                label="动作"
                min-width="180"
            />
            <el-table-column
                prop="targetId"
                label="对象"
                min-width="160"
            />
            <el-table-column
                prop="detail"
                label="详情"
                min-width="260"
                show-overflow-tooltip
            />
            <el-table-column
                prop="createdAt"
                label="时间"
                width="190"
            />
          </el-table>

          <el-pagination
              v-model:current-page="historyPage"
              v-model:page-size="historySize"
              class="table-pagination"
              layout="total, prev, pager, next"
              :total="historyTotal"
              @current-change="loadHistory"
          />
        </el-tab-pane>
      </el-tabs>
    </article>
  </section>
</template>
