<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  adminStatusChangesApi,
  adminStatusChangeAttachmentsApi,
  adminStatusChangeAttachmentDownloadUrl,
  adminStatusChangeAttachmentPreviewUrl,
  reviewStatusChangeApi,
  type AdminStatusChangeAttachment,
  type AdminStatusChangeApplication,
  type ReviewDecision,
} from '@/api/adminStatusChange'
import type { ApplicationStatus, StatusChangeType } from '@/api/student'

const loading = ref(false)
const saving = ref(false)
const reviewDialogVisible = ref(false)
const attachmentDialogVisible = ref(false)
const attachmentLoading = ref(false)
const currentApplication = ref<AdminStatusChangeApplication | null>(null)
const attachments = ref<AdminStatusChangeAttachment[]>([])
const records = ref<AdminStatusChangeApplication[]>([])
const page = ref(1)
const size = ref(10)
const total = ref(0)

const filters = reactive({
  status: 'SUBMITTED' as ApplicationStatus | '',
  keyword: '',
})

const reviewForm = reactive({
  decision: 'APPROVE' as ReviewDecision,
  comment: '',
})

const pendingCount = computed(() =>
  records.value.filter((item) => item.status === 'SUBMITTED' || item.status === 'UNDER_REVIEW').length,
)

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
    const response = await adminStatusChangesApi({
      status: filters.status,
      keyword: filters.keyword.trim(),
      page: page.value,
      size: size.value,
    })
    records.value = response.data.records
    page.value = response.data.page
    size.value = response.data.size
    total.value = response.data.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  void loadRecords()
}

function handleSizeChange() {
  page.value = 1
  void loadRecords()
}

function openReviewDialog(row: AdminStatusChangeApplication, decision: ReviewDecision) {
  currentApplication.value = row
  reviewForm.decision = decision
  reviewForm.comment = decision === 'APPROVE' ? '同意该学籍异动申请。' : ''
  reviewDialogVisible.value = true
}

async function submitReview() {
  if (!currentApplication.value) {
    return
  }
  if (!reviewForm.comment.trim()) {
    ElMessage.warning('请填写审核意见')
    return
  }
  saving.value = true
  try {
    await reviewStatusChangeApi(currentApplication.value.id, {
      decision: reviewForm.decision,
      comment: reviewForm.comment,
    })
    ElMessage.success(reviewForm.decision === 'APPROVE' ? '审核已通过' : '申请已驳回')
    reviewDialogVisible.value = false
    await loadRecords()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '审核操作失败'))
  } finally {
    saving.value = false
  }
}

async function openAttachmentDialog(row: AdminStatusChangeApplication) {
  currentApplication.value = row
  attachmentDialogVisible.value = true
  attachmentLoading.value = true
  try {
    attachments.value = (await adminStatusChangeAttachmentsApi(row.id)).data
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '附件列表加载失败'))
  } finally {
    attachmentLoading.value = false
  }
}

function previewAttachment(row: AdminStatusChangeAttachment) {
  window.open(adminStatusChangeAttachmentPreviewUrl(row.applicationId, row.id), '_blank')
}

function downloadAttachment(row: AdminStatusChangeAttachment) {
  window.open(adminStatusChangeAttachmentDownloadUrl(row.applicationId, row.id), '_blank')
}

function canReview(row: AdminStatusChangeApplication) {
  return row.status === 'SUBMITTED' || row.status === 'UNDER_REVIEW'
}

function formatDateTime(value?: string) {
  if (!value) {
    return '-'
  }
  return new Date(value).toLocaleString('zh-CN')
}

function formatFileSize(value?: number) {
  if (!value) return '-'
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  return `${Math.max(1, Math.round(value / 1024))} KB`
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
  <PageHeader title="学籍异动审核" description="集中处理学生休学、复学、转专业等学籍异动申请。" />

  <section class="admin-toolbar">
    <div class="admin-summary">
      <article>
        <span>当前列表</span>
        <strong>{{ total }}</strong>
      </article>
      <article>
        <span>待处理</span>
        <strong>{{ pendingCount }}</strong>
      </article>
      <article>
        <span>筛选状态</span>
        <strong>{{ filters.status ? statusText[filters.status] : '全部' }}</strong>
      </article>
    </div>
    <div class="admin-actions">
      <el-select v-model="filters.status" class="status-filter" clearable placeholder="状态">
        <el-option label="已提交" value="SUBMITTED" />
        <el-option label="审核中" value="UNDER_REVIEW" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="已驳回" value="REJECTED" />
        <el-option label="已取消" value="CANCELED" />
      </el-select>
      <el-input v-model="filters.keyword" class="keyword-input" placeholder="学号、姓名、专业、班级" clearable />
      <el-button type="primary" @click="search">查询</el-button>
    </div>
  </section>

  <section v-loading="loading" class="work-panel">
    <el-table :data="records" empty-text="暂无学籍异动申请">
      <el-table-column prop="studentNo" label="学号" width="110" />
      <el-table-column prop="studentName" label="姓名" width="100" />
      <el-table-column prop="major" label="专业" min-width="130" />
      <el-table-column prop="className" label="班级" min-width="130" />
      <el-table-column prop="studentStatus" label="当前学籍" width="100" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ typeText[row.type as StatusChangeType] }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="申请原因" min-width="190" show-overflow-tooltip />
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
      <el-table-column label="材料" width="90">
        <template #default="{ row }">
          <el-button type="primary" link @click="openAttachmentDialog(row)">附件</el-button>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :disabled="!canReview(row)" @click="openReviewDialog(row, 'APPROVE')">
            通过
          </el-button>
          <el-button type="danger" link :disabled="!canReview(row)" @click="openReviewDialog(row, 'REJECT')">
            驳回
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
      @current-change="loadRecords"
      @size-change="handleSizeChange"
    />
  </section>

  <el-dialog
    v-model="reviewDialogVisible"
    :title="reviewForm.decision === 'APPROVE' ? '通过申请' : '驳回申请'"
    width="520px"
  >
    <div v-if="currentApplication" class="review-context">
      <strong>{{ currentApplication.studentName }} / {{ currentApplication.studentNo }}</strong>
      <span>{{ typeText[currentApplication.type] }}：{{ currentApplication.reason }}</span>
    </div>
    <el-form label-width="84px" :model="reviewForm">
      <el-form-item label="审核意见">
        <el-input v-model="reviewForm.comment" type="textarea" :rows="5" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="reviewDialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submitReview">确认</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="attachmentDialogVisible" title="申请材料" width="760px">
    <el-table v-loading="attachmentLoading" :data="attachments" empty-text="暂无附件材料">
      <el-table-column prop="originalFilename" label="文件名" min-width="220" show-overflow-tooltip />
      <el-table-column prop="fileTypeLabel" label="类型" width="110" />
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ formatFileSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column label="上传时间" width="180">
        <template #default="{ row }">{{ formatDateTime(row.uploadedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link :disabled="!row.previewable" @click="previewAttachment(row)">预览</el-button>
          <el-button type="primary" link @click="downloadAttachment(row)">下载</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>
