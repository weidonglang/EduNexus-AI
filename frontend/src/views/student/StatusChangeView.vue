<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import type { UploadRequestOptions } from 'element-plus'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  statusChangeAttachmentsApi,
  statusChangeAttachmentDownloadUrl,
  statusChangeApplicationsApi,
  submitStatusChangeApplicationApi,
  uploadStatusChangeAttachmentApi,
  type ApplicationStatus,
  type StatusChangeAttachment,
  type StatusChangeApplication,
  type StatusChangeType,
} from '@/api/student'

const loading = ref(false)
const submitting = ref(false)
const records = ref<StatusChangeApplication[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)
const attachmentDialogVisible = ref(false)
const attachmentLoading = ref(false)
const attachmentUploading = ref(false)
const currentApplication = ref<StatusChangeApplication>()
const attachments = ref<StatusChangeAttachment[]>([])

const form = reactive({
  type: 'SUSPEND' as StatusChangeType,
  reason: '',
})

const typeText: Record<StatusChangeType, string> = {
  SUSPEND: '休学',
  RESUME: '复学',
  TRANSFER_MAJOR: '转专业',
  OTHER: '其他',
}

const statusText: Record<ApplicationStatus, string> = {
  SUBMITTED: '已提交',
  UNDER_REVIEW: '审核中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  CANCELED: '已取消',
}

const statusType: Record<ApplicationStatus, 'info' | 'warning' | 'success' | 'danger'> = {
  SUBMITTED: 'info',
  UNDER_REVIEW: 'warning',
  APPROVED: 'success',
  REJECTED: 'danger',
  CANCELED: 'info',
}

onMounted(loadRecords)

async function loadRecords() {
  loading.value = true
  try {
    const response = await statusChangeApplicationsApi({ page: page.value, size: size.value })
    records.value = response.data.records
    page.value = response.data.page
    size.value = response.data.size
    total.value = response.data.total
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '学籍异动记录加载失败'))
  } finally {
    loading.value = false
  }
}

function handleSizeChange() {
  page.value = 1
  void loadRecords()
}

async function submitApplication() {
  submitting.value = true
  try {
    await submitStatusChangeApplicationApi({ type: form.type, reason: form.reason })
    form.reason = ''
    ElMessage.success('申请已提交')
    await loadRecords()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '提交申请失败'))
  } finally {
    submitting.value = false
  }
}

async function openAttachments(row: StatusChangeApplication) {
  currentApplication.value = row
  attachmentDialogVisible.value = true
  await loadAttachments()
}

async function loadAttachments() {
  if (!currentApplication.value) return
  attachmentLoading.value = true
  try {
    const response = await statusChangeAttachmentsApi(currentApplication.value.id)
    attachments.value = response.data
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '附件列表加载失败'))
  } finally {
    attachmentLoading.value = false
  }
}

async function uploadAttachment(options: UploadRequestOptions) {
  if (!currentApplication.value) return
  attachmentUploading.value = true
  try {
    await uploadStatusChangeAttachmentApi(currentApplication.value.id, options.file as File)
    ElMessage.success('附件已上传')
    await loadAttachments()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '附件上传失败'))
  } finally {
    attachmentUploading.value = false
  }
}

function formatFileSize(value?: number) {
  if (!value) return '-'
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(value / 1024))} KB`
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN')
}

function downloadAttachment(row: StatusChangeAttachment) {
  if (!currentApplication.value) return
  window.open(statusChangeAttachmentDownloadUrl(currentApplication.value.id, row.id), '_blank')
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
  <PageHeader title="学籍异动申请" description="提交休学、复学、转专业等申请，并跟踪审核状态。" />

  <section class="profile-grid">
    <article class="work-panel">
      <h2>提交申请</h2>
      <el-form label-width="90px">
        <el-form-item label="异动类型">
          <el-select v-model="form.type" class="full-field">
            <el-option label="休学" value="SUSPEND" />
            <el-option label="复学" value="RESUME" />
            <el-option label="转专业" value="TRANSFER_MAJOR" />
            <el-option label="其他" value="OTHER" />
          </el-select>
        </el-form-item>
        <el-form-item label="申请原因">
          <el-input v-model="form.reason" type="textarea" :rows="6" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="submitting" :disabled="!form.reason.trim()" @click="submitApplication">
            提交申请
          </el-button>
        </el-form-item>
      </el-form>
    </article>

    <article v-loading="loading" class="work-panel">
      <h2>申请记录</h2>
      <el-table :data="records" empty-text="暂无申请记录">
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ typeText[row.type as StatusChangeType] }}</template>
        </el-table-column>
        <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusType[row.status as ApplicationStatus]">
              {{ statusText[row.status as ApplicationStatus] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.submittedAt) }}</template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.reviewComment || '-' }}</template>
        </el-table-column>
        <el-table-column label="材料" width="100">
          <template #default="{ row }">
            <el-button text type="primary" @click="openAttachments(row)">附件</el-button>
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
        @current-change="loadRecords"
        @size-change="handleSizeChange"
      />
    </article>
  </section>

  <el-dialog v-model="attachmentDialogVisible" title="申请材料" width="680px">
    <div class="attachment-toolbar">
      <span v-if="currentApplication">
        {{ typeText[currentApplication.type] }} / {{ statusText[currentApplication.status] }}
      </span>
      <el-upload :show-file-list="false" :http-request="uploadAttachment" :disabled="attachmentUploading">
        <el-button type="primary" :loading="attachmentUploading">上传材料</el-button>
      </el-upload>
    </div>
    <el-alert
      class="upload-tip"
      type="info"
      :closable="false"
      title="支持 PDF、图片、Word、Excel，单个文件不超过 10MB。"
    />
    <el-table v-loading="attachmentLoading" :data="attachments" empty-text="暂无附件">
      <el-table-column prop="originalFilename" label="文件名" min-width="220" show-overflow-tooltip />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatFileSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column label="上传时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.uploadedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button type="primary" link @click="downloadAttachment(row)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>

<style scoped>
.attachment-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.upload-tip {
  margin-bottom: 12px;
}

@media (max-width: 720px) {
  .attachment-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
