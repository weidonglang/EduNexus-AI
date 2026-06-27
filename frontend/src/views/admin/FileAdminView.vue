<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import { adminFilesApi, deleteAdminFileApi, downloadAdminFileApi, type AdminAttachment } from '@/api/adminFile'

const loading = ref(false)
const files = ref<AdminAttachment[]>([])
const page = ref(1)
const size = ref(10)

const totalSize = computed(() => files.value.reduce((sum, item) => sum + Number(item.sizeBytes || 0), 0))
const pagedFiles = computed(() => files.value.slice((page.value - 1) * size.value, page.value * size.value))

onMounted(loadData)

async function loadData() {
  loading.value = true
  try {
    files.value = (await adminFilesApi()).data
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
}

async function downloadFile(row: AdminAttachment) {
  try {
    const blob = await downloadAdminFileApi(row.id)
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.originalFilename
    link.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '下载失败'))
  }
}

async function removeFile(row: AdminAttachment) {
  await ElMessageBox.confirm(`确认删除 ${row.originalFilename} 吗？数据库记录和本地文件都会删除。`, '删除材料', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  try {
    await deleteAdminFileApi(row.id)
    ElMessage.success('材料已删除')
    await loadData()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '删除失败'))
  }
}

function formatSize(size: number) {
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(2)} MB`
  if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${size || 0} B`
}

function formatDateTime(value: string) {
  return new Date(value).toLocaleString('zh-CN')
}

function fileTypeLabel(contentType?: string) {
  if (contentType === 'application/pdf') return 'PDF'
  if (contentType === 'image/jpeg' || contentType === 'image/png') return '图片'
  if (
    contentType === 'application/msword' ||
    contentType === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
  ) {
    return 'Word 文档'
  }
  if (
    contentType === 'application/vnd.ms-excel' ||
    contentType === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  ) {
    return 'Excel 表格'
  }
  return '其他文件'
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
  <PageHeader title="文件管理" description="查看、下载和删除学生学籍异动申请上传材料。" />

  <section class="admin-toolbar">
    <div class="admin-summary">
      <article><span>材料数量</span><strong>{{ files.length }}</strong></article>
      <article><span>总大小</span><strong>{{ formatSize(totalSize) }}</strong></article>
      <article><span>管理范围</span><strong>学籍材料</strong></article>
    </div>
    <div class="admin-actions">
      <el-button @click="loadData">刷新</el-button>
    </div>
  </section>

  <section v-loading="loading" class="work-panel">
    <el-table :data="pagedFiles" empty-text="暂无上传材料">
      <el-table-column prop="originalFilename" label="文件名" min-width="180" />
      <el-table-column prop="studentNo" label="学号" width="120" />
      <el-table-column prop="studentName" label="学生" width="120" />
      <el-table-column prop="changeType" label="申请类型" width="130" />
      <el-table-column prop="applicationStatus" label="申请状态" width="110" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ row.fileTypeLabel || fileTypeLabel(row.contentType) }}</template>
      </el-table-column>
      <el-table-column label="上传时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.uploadedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link @click="downloadFile(row)">下载</el-button>
          <el-button type="danger" link @click="removeFile(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      class="table-pagination"
      layout="total, sizes, prev, pager, next"
      :page-sizes="[10, 20, 50, 100]"
      :total="files.length"
      @size-change="handleSizeChange"
    />
  </section>
</template>
