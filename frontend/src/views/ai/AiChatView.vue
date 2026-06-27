<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { Bot, Plus, SendHorizontal, Trash2 } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  aiChatMessagesApi,
  aiChatModelsApi,
  aiChatSessionsApi,
  aiStatusApi,
  createAiChatSessionApi,
  deleteAiChatSessionApi,
  sendAiChatMessageApi,
  updateAiChatSessionApi,
  type AiChatMessage,
  type AiChatSession,
  type AiServiceStatusResponse,
} from '@/api/ai'
import type { AiModelRecord } from '@/api/aiModel'

const input = ref('帮我总结一下今天应该如何准备教务系统答辩')
const loading = ref(false)
const booting = ref(false)
const status = ref<AiServiceStatusResponse>()
const statusError = ref('')
const chatWindow = ref<HTMLElement>()
const models = ref<AiModelRecord[]>([])
const sessions = ref<AiChatSession[]>([])
const activeSession = ref<AiChatSession>()
const selectedModelId = ref<number>()
const messages = ref<AiChatMessage[]>([])

onMounted(async () => {
  booting.value = true
  try {
    await Promise.all([loadStatus(), loadModels(), loadSessions()])
    if (!activeSession.value) {
      await newSession()
    }
  } finally {
    booting.value = false
  }
})

async function loadStatus() {
  try {
    status.value = (await aiStatusApi()).data
    statusError.value = ''
  } catch (error) {
    status.value = undefined
    statusError.value = resolveErrorMessage(error, 'AI 状态暂不可用，请确认后端服务已经启动。')
  }
}

async function loadModels() {
  models.value = (await aiChatModelsApi()).data
  selectedModelId.value = models.value[0]?.id
}

async function loadSessions() {
  sessions.value = (await aiChatSessionsApi()).data
  if (sessions.value.length > 0) {
    await selectSession(sessions.value[0])
  }
}

async function newSession() {
  const session = (await createAiChatSessionApi({ title: '新会话', modelId: selectedModelId.value })).data
  sessions.value = [session, ...sessions.value]
  await selectSession(session)
}

async function selectSession(session: AiChatSession) {
  activeSession.value = session
  selectedModelId.value = session.modelId || selectedModelId.value || models.value[0]?.id
  messages.value = (await aiChatMessagesApi(session.id)).data
  await scrollToBottom()
}

async function renameSession(session: AiChatSession) {
  const value = await ElMessageBox.prompt('请输入新的会话标题', '重命名会话', {
    inputValue: session.title,
    confirmButtonText: '保存',
    cancelButtonText: '取消',
  })
  const updated = (await updateAiChatSessionApi(session.id, { title: value.value, modelId: session.modelId })).data
  sessions.value = sessions.value.map((item) => (item.id === updated.id ? updated : item))
  if (activeSession.value?.id === updated.id) activeSession.value = updated
}

async function removeSession(session: AiChatSession) {
  await ElMessageBox.confirm(`确认删除会话“${session.title}”吗？`, '删除会话', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
  await deleteAiChatSessionApi(session.id)
  sessions.value = sessions.value.filter((item) => item.id !== session.id)
  if (activeSession.value?.id === session.id) {
    activeSession.value = undefined
    messages.value = []
    if (sessions.value[0]) {
      await selectSession(sessions.value[0])
    } else {
      await newSession()
    }
  }
}

async function changeModel() {
  if (!activeSession.value) return
  const updated = (await updateAiChatSessionApi(activeSession.value.id, {
    title: activeSession.value.title,
    modelId: selectedModelId.value,
  })).data
  activeSession.value = updated
  sessions.value = sessions.value.map((item) => (item.id === updated.id ? updated : item))
}

async function send() {
  if (loading.value) return
  const text = input.value.trim()
  if (!text) return
  if (!activeSession.value) {
    await newSession()
  }
  const sessionId = activeSession.value?.id
  if (!sessionId) return
  input.value = ''
  loading.value = true
  messages.value.push({
    id: Date.now(),
    sessionId,
    role: 'user',
    content: text,
    searchUsed: false,
    createdAt: new Date().toISOString(),
  })
  await scrollToBottom()
  try {
    const result = (await sendAiChatMessageApi(sessionId, { message: text, modelId: selectedModelId.value })).data
    messages.value = result.messages
    activeSession.value = result.session
    sessions.value = [result.session, ...sessions.value.filter((item) => item.id !== result.session.id)]
  } catch (error) {
    const message = resolveErrorMessage(error, 'AI 聊天暂不可用，请确认后端和 ai-service 已启动。')
    messages.value.push({
      id: Date.now() + 1,
      sessionId,
      role: 'assistant',
      content: `发送失败：${message}`,
      serviceMode: 'error',
      searchUsed: false,
      createdAt: new Date().toISOString(),
    })
    ElMessage.error(message)
  } finally {
    loading.value = false
    await loadStatus()
    await scrollToBottom()
  }
}

function handleInputKeydown(event: KeyboardEvent) {
  if (event.key !== 'Enter' || event.shiftKey) return
  event.preventDefault()
  void send()
}

async function scrollToBottom() {
  await nextTick()
  if (chatWindow.value) {
    chatWindow.value.scrollTop = chatWindow.value.scrollHeight
  }
}

function resolveErrorMessage(error: unknown, fallback: string) {
  const maybe = error as {
    response?: { status?: number; data?: { message?: string } }
    message?: string
  }
  if (maybe.response?.status === 401) return '登录状态已失效，请重新登录。'
  if (maybe.response?.status === 403) return '当前账号没有访问 AI 功能的权限。'
  return maybe.response?.data?.message || maybe.message || fallback
}
</script>

<template>
  <PageHeader title="AI 聊天" description="通用聊天入口，用于答辩准备、文本润色和普通问答；正式教务依据请使用 RAG 助手。" />

  <section v-loading="booting" class="chat-layout">
    <aside class="session-sidebar">
      <el-button type="primary" :icon="Plus" @click="newSession">新建会话</el-button>
      <div class="session-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          :class="['session-item', { active: activeSession?.id === session.id }]"
          @click="selectSession(session)"
        >
          <span>{{ session.title }}</span>
          <small>{{ session.modelName || '默认模型' }}</small>
        </button>
      </div>
    </aside>

    <section class="chat-page">
      <el-alert
        v-if="status"
        :type="status.aiServiceOnline ? 'success' : 'warning'"
        :closable="false"
        :title="`ai-service ${status.aiServiceOnline ? '在线' : '离线'} / ${status.currentMode}`"
        :description="`调用：${status.discoveryEnabled ? status.serviceName : status.baseUrl}，默认模型：${status.defaultChatModel || status.chatModel || '-'}，搜索：${status.searchEnabled ? status.searchProvider : '未启用'}，耗时：${status.lastLatencyMs}ms`"
      />
      <el-alert v-else-if="statusError" type="warning" :closable="false" title="AI 状态不可用" :description="statusError" />

      <div class="chat-toolbar">
        <el-select v-model="selectedModelId" class="model-select" filterable placeholder="选择聊天模型" @change="changeModel">
          <el-option v-for="model in models" :key="model.id" :label="model.name" :value="model.id">
            <span>{{ model.name }}</span>
            <small class="model-option">{{ model.modelName }}</small>
          </el-option>
        </el-select>
        <el-button :disabled="!activeSession" @click="activeSession && renameSession(activeSession)">重命名</el-button>
        <el-button :icon="Trash2" :disabled="!activeSession" @click="activeSession && removeSession(activeSession)" />
      </div>

      <article ref="chatWindow" class="chat-window">
        <div v-if="messages.length === 0" class="chat-empty">暂无历史消息，发送第一条消息开始会话。</div>
        <div v-for="message in messages" :key="message.id" :class="['chat-message', message.role]">
          <Bot v-if="message.role === 'assistant'" :size="18" />
          <p>{{ message.content }}</p>
          <small v-if="message.serviceMode || message.modelName">
            {{ message.serviceMode }}<template v-if="message.modelName"> / {{ message.modelName }}</template>
            <template v-if="message.searchUsed"> / 已联网搜索</template>
          </small>
        </div>
      </article>

      <div class="chat-input">
        <el-input v-model="input" type="textarea" :rows="3" maxlength="1000" show-word-limit @keydown="handleInputKeydown" />
        <el-button type="primary" :icon="SendHorizontal" :loading="loading" @click="send">发送</el-button>
      </div>
    </section>
  </section>
</template>

<style scoped>
.chat-layout {
  display: grid;
  grid-template-columns: 260px minmax(0, 1fr);
  gap: 16px;
}

.session-sidebar {
  display: grid;
  align-content: start;
  gap: 12px;
}

.session-list {
  display: grid;
  gap: 8px;
}

.session-item {
  border: 1px solid #e5e7eb;
  background: #fff;
  border-radius: 8px;
  padding: 10px;
  display: grid;
  gap: 4px;
  text-align: left;
  cursor: pointer;
}

.session-item.active {
  border-color: #2563eb;
  background: #eff6ff;
}

.session-item small,
.model-option {
  color: #6b7280;
}

.chat-page {
  display: grid;
  gap: 16px;
}

.chat-toolbar {
  display: flex;
  gap: 10px;
}

.model-select {
  min-width: 260px;
}

.chat-window {
  min-height: 420px;
  max-height: 560px;
  overflow-y: auto;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 18px;
  display: grid;
  align-content: start;
  gap: 12px;
}

.chat-empty {
  color: #6b7280;
  text-align: center;
  padding: 80px 0;
}

.chat-message {
  max-width: 78%;
  padding: 12px 14px;
  border-radius: 8px;
  background: #f8fafc;
  color: #1f2937;
}

.chat-message.user {
  justify-self: end;
  background: #e0f2fe;
}

.chat-message.assistant {
  justify-self: start;
}

.chat-message p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
}

.chat-message small {
  display: block;
  margin-top: 6px;
  color: #6b7280;
}

.chat-input {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 12px;
  align-items: end;
}

@media (max-width: 900px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }
}
</style>
