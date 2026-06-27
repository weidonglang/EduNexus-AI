<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  dataArchiveArchiveApi,
  dataArchiveCleanupApi,
  dataArchiveExportCsvUrl,
  dataArchivePreviewApi,
  type ArchivePreview,
  type ArchiveRecordRow,
} from '@/api/batchOps'

const objectType = ref('COURSE_SELECTION')
const term = ref('2025-2026-1')
const loading = ref(false)
const preview = ref<ArchivePreview>()
const lastRecord = ref<ArchiveRecordRow>()

const objectOptions = [
  { label: '历史选课记录', value: 'COURSE_SELECTION' },
  { label: '成绩记录', value: 'GRADE' },
  { label: '考试安排', value: 'EXAM' },
  { label: '通知记录', value: 'NOTICE' },
  { label: 'AI 调用日志', value: 'AI_CALL_LOG' },
  { label: '审计日志', value: 'AUDIT_LOG' },
]

async function runPreview() {
  loading.value = true
  try {
    preview.value = (await dataArchivePreviewApi({
      objectType: objectType.value,
      term: term.value.trim() || undefined,
    })).data
    lastRecord.value = undefined
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '归档预览失败'))
  } finally {
    loading.value = false
  }
}

async function runAction(action: 'ARCHIVE' | 'CLEANUP', dryRun: boolean) {
  await ElMessageBox.confirm(
    dryRun ? '确认执行 dry-run 预览？不会改变数据库。' : '确认记录正式操作？当前演示模式不会执行物理删除，但会写入审计与任务记录。',
    action === 'ARCHIVE' ? '归档确认' : '清理确认',
    { type: dryRun ? 'info' : 'warning' },
  )

  loading.value = true
  try {
    const params = {
      objectType: objectType.value,
      term: term.value.trim() || undefined,
      dryRun,
    }
    lastRecord.value = action === 'ARCHIVE'
      ? (await dataArchiveArchiveApi(params)).data
      : (await dataArchiveCleanupApi(params)).data
    ElMessage.success(dryRun ? 'dry-run 已完成' : '操作记录已写入')
    await runPreview()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '归档清理操作失败'))
  } finally {
    loading.value = false
  }
}

function exportCsv() {
  window.open(dataArchiveExportCsvUrl(), '_blank')
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
      title="数据归档清理"
      description="按学期预览历史数据规模，记录归档与清理任务，保留审计追踪和 CSV 导出。"
  />

  <section class="admin-toolbar">
    <div class="admin-actions">
      <el-select v-model="objectType" class="template-select" placeholder="归档对象">
        <el-option v-for="item in objectOptions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-input v-model="term" class="small-input" clearable placeholder="学期/日期关键词" @keyup.enter="runPreview" />
      <el-button type="primary" :loading="loading" @click="runPreview">dry-run 预览</el-button>
      <el-button :loading="loading" @click="runAction('ARCHIVE', false)">正式归档</el-button>
      <el-button type="warning" plain :loading="loading" @click="runAction('CLEANUP', false)">正式清理</el-button>
      <el-button @click="exportCsv">导出记录 CSV</el-button>
    </div>
  </section>

  <section class="admin-summary">
    <article>
      <span>对象</span>
      <strong>{{ objectOptions.find((item) => item.value === objectType)?.label }}</strong>
    </article>
    <article>
      <span>筛选学期</span>
      <strong>{{ term || '-' }}</strong>
    </article>
    <article>
      <span>影响记录数</span>
      <strong>{{ preview?.affectedCount ?? '-' }}</strong>
    </article>
    <article>
      <span>模式</span>
      <strong>{{ preview?.dryRun ? 'dry-run' : '待预览' }}</strong>
    </article>
  </section>

  <section class="work-panel" v-loading="loading">
    <el-alert
        type="info"
        :closable="false"
        title="安全边界"
        description="当前学期禁止清理；演示环境正式清理仅写入任务与审计记录，不做物理删除。"
    />

    <el-descriptions v-if="preview" class="archive-descriptions" :column="2" border>
      <el-descriptions-item label="归档对象">{{ preview.objectType }}</el-descriptions-item>
      <el-descriptions-item label="筛选条件">{{ preview.term || '-' }}</el-descriptions-item>
      <el-descriptions-item label="影响记录">{{ preview.affectedCount }}</el-descriptions-item>
      <el-descriptions-item label="说明">{{ preview.message }}</el-descriptions-item>
    </el-descriptions>

    <el-empty v-else description="请先执行 dry-run 预览" />

    <el-descriptions v-if="lastRecord" class="archive-descriptions" :column="2" border>
      <el-descriptions-item label="记录 ID">{{ lastRecord.id }}</el-descriptions-item>
      <el-descriptions-item label="动作">{{ lastRecord.action }}</el-descriptions-item>
      <el-descriptions-item label="操作人">{{ lastRecord.operator }}</el-descriptions-item>
      <el-descriptions-item label="操作时间">{{ lastRecord.createdAt }}</el-descriptions-item>
      <el-descriptions-item label="结果说明" :span="2">{{ lastRecord.detail }}</el-descriptions-item>
    </el-descriptions>
  </section>
</template>

<style scoped>
.archive-descriptions {
  margin-top: 16px;
}
</style>
