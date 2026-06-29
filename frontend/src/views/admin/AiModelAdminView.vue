<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  aiModelsApi,
  aiSafetyConfigsApi,
  aiSafetyTemplatesApi,
  aiSearchConfigApi,
  aiSearchTemplatesApi,
  createAiModelApi,
  deleteAiModelApi,
  disableAiModelApi,
  enableAiModelApi,
  setDefaultAiModelApi,
  testAiSafetyApi,
  testAiModelApi,
  testAiSearchApi,
  updateAiModelApi,
  updateAiSafetyConfigsApi,
  updateAiSearchConfigApi,
  type AiSafetyConfig,
  type AiModelRecord,
  type AiModelRequest,
  type SafetyTemplate,
  type SearchConfig,
  type SearchResult,
  type SearchConfigTemplate,
  type SafetyTestResponse,
} from '@/api/aiModel'

const loading = ref(false)
const saving = ref(false)
const testingId = ref<number>()
const models = ref<AiModelRecord[]>([])
const dialogVisible = ref(false)
const editingId = ref<number>()
const searchConfig = ref<SearchConfig>()
const searchSaving = ref(false)
const searchTesting = ref(false)
const searchQuery = ref('Spring Cloud Alibaba Nacos Discovery 最新用法')
const searchResults = ref<SearchResult[]>([])
const searchTemplates = ref<SearchConfigTemplate[]>([])
const searchTemplateCode = ref('LOCAL_DEMO')
const safetyConfigs = ref<AiSafetyConfig[]>([])
const safetyTemplates = ref<SafetyTemplate[]>([])
const safetyTemplateCode = ref('BALANCED')
const safetyTestScene = ref('AI_INPUT')
const safetyTestContent = ref('示例敏感词A')
const safetyTestResult = ref<SafetyTestResponse>()
const safetyTesting = ref(false)
const safetySaving = ref(false)

const modelForm = reactive<AiModelRequest>({
  name: '',
  provider: 'OLLAMA',
  modelName: '',
  baseUrl: 'http://localhost:11434',
  apiKeyRef: 'OLLAMA_API_KEY',
  modelType: 'CHAT',
  purpose: '',
  enabled: true,
  defaultModel: false,
  description: '',
})

const modelTypes = [
  { label: '聊天', value: 'CHAT' },
  { label: '教务问答', value: 'RAG' },
  { label: 'SQL 生成', value: 'SQL' },
  { label: '安全审查', value: 'SAFETY' },
]

onMounted(loadAll)

async function loadAll() {
  loading.value = true
  try {
    const results = await Promise.allSettled([
      aiModelsApi(),
      aiSearchConfigApi(),
      aiSafetyConfigsApi(),
      aiSearchTemplatesApi(),
      aiSafetyTemplatesApi(),
    ])
    if (results[0].status === 'fulfilled') {
      models.value = results[0].value.data
    }
    if (results[1].status === 'fulfilled') {
      searchConfig.value = results[1].value.data
    }
    if (results[2].status === 'fulfilled') {
      safetyConfigs.value = results[2].value.data
    }
    if (results[3].status === 'fulfilled') {
      searchTemplates.value = results[3].value.data
    }
    if (results[4].status === 'fulfilled') {
      safetyTemplates.value = results[4].value.data
    }
    const failed = results.some((result) => result.status === 'rejected')
    if (failed) {
      ElMessage.warning('部分 AI 管理配置加载失败，请检查后端迁移和服务状态')
    }
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = undefined
  Object.assign(modelForm, {
    name: '',
    provider: 'OLLAMA',
    modelName: '',
    baseUrl: 'http://localhost:11434',
    apiKeyRef: 'OLLAMA_API_KEY',
    modelType: 'CHAT',
    purpose: '',
    enabled: true,
    defaultModel: false,
    description: '',
  })
  dialogVisible.value = true
}

function openEdit(row: AiModelRecord) {
  editingId.value = row.id
  Object.assign(modelForm, {
    name: row.name,
    provider: row.provider,
    modelName: row.modelName,
    baseUrl: row.baseUrl,
    apiKeyRef: row.apiKeyRef,
    modelType: row.modelType,
    purpose: row.purpose,
    enabled: row.enabled,
    defaultModel: row.defaultModel,
    description: row.description,
  })
  dialogVisible.value = true
}

async function saveModel() {
  saving.value = true
  try {
    if (editingId.value) {
      await updateAiModelApi(editingId.value, modelForm)
    } else {
      await createAiModelApi(modelForm)
    }
    ElMessage.success('模型配置已保存')
    dialogVisible.value = false
    await loadAll()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '模型配置保存失败'))
  } finally {
    saving.value = false
  }
}

async function toggleModel(row: AiModelRecord) {
  try {
    if (row.enabled) {
      await disableAiModelApi(row.id)
    } else {
      await enableAiModelApi(row.id)
    }
    await loadAll()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '模型状态更新失败'))
  }
}

async function deleteModel(row: AiModelRecord) {
  try {
    await ElMessageBox.confirm(`确认删除模型“${row.name}”？系统会自动停用并软删除该模型，历史日志会保留模型名称。`, '删除模型', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteAiModelApi(row.id)
    ElMessage.success('模型已删除')
    await loadAll()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(resolveErrorMessage(error, '模型删除失败'))
    }
  }
}

async function setDefault(row: AiModelRecord) {
  try {
    await setDefaultAiModelApi(row.id)
    ElMessage.success('默认模型已更新')
    await loadAll()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '默认模型更新失败'))
  }
}

async function testModel(row: AiModelRecord) {
  testingId.value = row.id
  try {
    const response = await testAiModelApi(row.id)
    response.data.success ? ElMessage.success(response.data.message) : ElMessage.warning(response.data.message)
    await loadAll()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '模型测试失败'))
  } finally {
    testingId.value = undefined
  }
}

async function saveSearchConfig() {
  if (!searchConfig.value) return
  searchSaving.value = true
  try {
    await updateAiSearchConfigApi({
      enabled: searchConfig.value.enabled,
      provider: searchConfig.value.provider,
      baseUrl: searchConfig.value.baseUrl,
      apiKeyEnv: searchConfig.value.apiKeyEnv,
      allowedScenes: searchConfig.value.allowedScenes,
      safetyPolicy: searchConfig.value.safetyPolicy,
    })
    ElMessage.success('搜索配置已保存')
    await loadAll()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '搜索配置保存失败'))
  } finally {
    searchSaving.value = false
  }
}

function applySearchTemplate() {
  if (!searchConfig.value) return
  const template = searchTemplates.value.find((item) => item.code === searchTemplateCode.value)
  if (!template) return
  Object.assign(searchConfig.value, {
    enabled: template.enabled,
    provider: template.provider,
    baseUrl: template.baseUrl,
    apiKeyEnv: template.apiKeyEnv,
    allowedScenes: template.allowedScenes,
    safetyPolicy: template.safetyPolicy,
  })
  searchQuery.value = template.testQuery
  ElMessage.success(`已应用模板：${template.name}`)
}

async function testSearch() {
  searchTesting.value = true
  try {
    const response = await testAiSearchApi(searchQuery.value)
    searchResults.value = response.data.results
    response.data.allowed ? ElMessage.success(response.data.message) : ElMessage.warning(response.data.message)
    await loadAll()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '搜索测试失败'))
  } finally {
    searchTesting.value = false
  }
}

function applySafetyTemplate() {
  const template = safetyTemplates.value.find((item) => item.code === safetyTemplateCode.value)
  if (!template) return
  safetyConfigs.value = safetyConfigs.value.map((item) => ({
    ...item,
    enabled: template.enabled,
    strategy: template.strategy,
    description: `${template.name}：${template.description}`,
  }))
  ElMessage.success(`已应用安全审查模板：${template.name}`)
}

async function testSafety() {
  safetyTesting.value = true
  try {
    const response = await testAiSafetyApi(safetyTestScene.value, safetyTestContent.value)
    safetyTestResult.value = response.data
    response.data.success ? ElMessage.success(response.data.message) : ElMessage.warning(response.data.message)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '安全审查测试失败'))
  } finally {
    safetyTesting.value = false
  }
}

async function saveSafetyConfigs() {
  safetySaving.value = true
  try {
    await updateAiSafetyConfigsApi(safetyConfigs.value.map((item) => ({
      scene: item.scene,
      enabled: item.enabled,
      strategy: item.strategy,
      description: item.description,
    })))
    ElMessage.success('安全策略已保存')
    await loadAll()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '安全策略保存失败'))
  } finally {
    safetySaving.value = false
  }
}

function statusTag(status?: string) {
  if (status === 'UP') return 'success'
  if (status === 'DOWN') return 'danger'
  if (status === 'SKIPPED') return 'warning'
  return 'info'
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
  <PageHeader title="AI模型与联网搜索" description="维护模型注册、默认模型、搜索配置和安全审查策略。" />

  <section class="admin-stack" v-loading="loading">
    <article class="work-panel">
      <div class="panel-title-row">
        <h2>模型注册</h2>
        <el-button type="primary" @click="openCreate">新增模型</el-button>
      </div>
      <el-table :data="models" empty-text="暂无模型">
        <el-table-column prop="name" label="名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="provider" label="提供方" width="110" />
        <el-table-column prop="modelName" label="模型标识" min-width="220" show-overflow-tooltip />
        <el-table-column prop="modelType" label="类型" width="110" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '停用' }}</el-tag>
            <el-tag v-if="row.defaultModel" class="inline-tag" type="warning">默认</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="探测" width="120">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.lastStatus)">{{ row.lastStatus || '未测试' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="330" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text @click="toggleModel(row)">{{ row.enabled ? '停用' : '启用' }}</el-button>
            <el-button text :disabled="row.defaultModel || !row.enabled" @click="setDefault(row)">默认</el-button>
            <el-button text :loading="testingId === row.id" @click="testModel(row)">测试</el-button>
            <el-button text type="danger" :disabled="row.defaultModel" @click="deleteModel(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </article>

    <article v-if="searchConfig" class="work-panel">
      <div class="panel-title-row">
        <h2>联网搜索与安全审查</h2>
        <el-switch v-model="searchConfig.enabled" active-text="启用" inactive-text="停用" />
      </div>
      <el-form label-width="110px" class="compact-form">
        <el-form-item label="预设模板">
          <div class="template-row">
            <el-select v-model="searchTemplateCode" class="full-field">
              <el-option v-for="item in searchTemplates" :key="item.code" :label="item.name" :value="item.code">
                <span>{{ item.name }}</span>
                <small class="template-hint">{{ item.description }}</small>
              </el-option>
            </el-select>
            <el-button @click="applySearchTemplate">应用模板</el-button>
          </div>
        </el-form-item>
        <el-form-item label="搜索提供方">
          <el-select v-model="searchConfig.provider" class="full-field">
            <el-option label="本地演示" value="LOCAL_DEMO" />
            <el-option label="SearXNG" value="SEARXNG" />
            <el-option label="自定义 JSON" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="searchConfig.baseUrl" placeholder="https://search.example.com/search 或包含 {query} 的 URL" />
        </el-form-item>
        <el-form-item label="API Key 环境变量">
          <el-input v-model="searchConfig.apiKeyEnv" placeholder="SEARCH_API_KEY" />
        </el-form-item>
        <el-form-item label="允许场景">
          <el-input v-model="searchConfig.allowedScenes" />
        </el-form-item>
        <el-form-item label="安全策略">
          <el-input v-model="searchConfig.safetyPolicy" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="searchSaving" @click="saveSearchConfig">保存配置</el-button>
          <el-tag :type="statusTag(searchConfig.lastStatus)" class="inline-tag">{{ searchConfig.lastStatus || '未测试' }}</el-tag>
        </el-form-item>
      </el-form>
      <div class="search-test-row">
        <el-input v-model="searchQuery" />
        <el-button :loading="searchTesting" @click="testSearch">测试搜索</el-button>
      </div>
      <el-alert
        v-if="searchConfig.lastStatus || searchConfig.lastError"
        class="result-alert"
        :type="searchConfig.lastStatus === 'UP' ? 'success' : searchConfig.lastStatus === 'DOWN' ? 'error' : 'info'"
        :title="searchConfig.lastStatus === 'UP' ? '最近测试成功' : '最近测试状态'"
        :description="searchConfig.lastError || `耗时 ${searchConfig.lastLatencyMs || 0} ms`"
        show-icon
        :closable="false"
      />
      <el-table v-if="searchResults.length" :data="searchResults" class="result-table">
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="link" label="链接" min-width="260" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="260" show-overflow-tooltip />
      </el-table>
    </article>

    <article class="work-panel">
      <div class="panel-title-row">
        <h2>AI 安全策略</h2>
        <el-button type="primary" :loading="safetySaving" @click="saveSafetyConfigs">保存策略</el-button>
      </div>
      <div class="template-row safety-template-row">
        <el-select v-model="safetyTemplateCode" class="full-field">
          <el-option v-for="item in safetyTemplates" :key="item.code" :label="item.name" :value="item.code">
            <span>{{ item.name }}</span>
            <small class="template-hint">{{ item.scenario }}</small>
          </el-option>
        </el-select>
        <el-button @click="applySafetyTemplate">应用模板</el-button>
      </div>
      <el-table :data="safetyConfigs" empty-text="暂无安全策略">
        <el-table-column prop="scene" label="场景" width="160" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-switch v-model="row.enabled" active-text="启用" inactive-text="停用" />
          </template>
        </el-table-column>
        <el-table-column label="策略" width="150">
          <template #default="{ row }">
            <el-select v-model="row.strategy">
              <el-option label="阻断" value="block" />
              <el-option label="告警" value="warn" />
              <el-option label="复核" value="review" />
              <el-option label="仅记录" value="log_only" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
      </el-table>
      <div class="search-test-row safety-test-row">
        <el-input v-model="safetyTestScene" placeholder="测试场景，例如 AI_INPUT" />
        <el-input v-model="safetyTestContent" placeholder="测试文本" />
        <el-button :loading="safetyTesting" @click="testSafety">测试安全审查</el-button>
      </div>
      <el-alert
        v-if="safetyTestResult"
        class="result-alert"
        :type="safetyTestResult.success ? 'success' : 'warning'"
        :title="safetyTestResult.message"
        :description="`${safetyTestResult.action} / ${safetyTestResult.riskLevel}。${safetyTestResult.suggestion}`"
        show-icon
        :closable="false"
      />
    </article>
  </section>

  <el-dialog v-model="dialogVisible" :title="editingId ? '编辑模型' : '新增模型'" width="680px">
    <el-form label-width="100px">
      <el-form-item label="名称"><el-input v-model="modelForm.name" /></el-form-item>
      <el-form-item label="提供方"><el-input v-model="modelForm.provider" /></el-form-item>
      <el-form-item label="模型标识"><el-input v-model="modelForm.modelName" /></el-form-item>
      <el-form-item label="Base URL"><el-input v-model="modelForm.baseUrl" /></el-form-item>
      <el-form-item label="密钥引用"><el-input v-model="modelForm.apiKeyRef" /></el-form-item>
      <el-form-item label="类型">
        <el-select v-model="modelForm.modelType" class="full-field">
          <el-option v-for="item in modelTypes" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="用途"><el-input v-model="modelForm.purpose" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="modelForm.description" type="textarea" :rows="3" /></el-form-item>
      <el-form-item label="状态">
        <el-checkbox v-model="modelForm.enabled">启用</el-checkbox>
        <el-checkbox v-model="modelForm.defaultModel">设为默认</el-checkbox>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="!modelForm.name || !modelForm.modelName" @click="saveModel">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.admin-stack {
  display: grid;
  gap: 16px;
}

.panel-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.panel-title-row h2 {
  margin: 0;
}

.inline-tag {
  margin-left: 6px;
}

.compact-form {
  max-width: 920px;
}

.full-field {
  width: 100%;
}

.search-test-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  margin-top: 12px;
}

.template-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  width: 100%;
}

.template-hint {
  margin-left: 10px;
  color: var(--nexus-text-muted);
}

.safety-template-row {
  margin-bottom: 12px;
}

.safety-test-row {
  grid-template-columns: 180px minmax(0, 1fr) auto;
}

.result-alert {
  margin-top: 12px;
}

.result-table {
  margin-top: 12px;
}

@media (max-width: 720px) {
  .panel-title-row,
  .search-test-row,
  .template-row {
    grid-template-columns: 1fr;
    display: grid;
  }
}
</style>
