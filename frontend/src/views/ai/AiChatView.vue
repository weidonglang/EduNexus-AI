<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { Bot, Copy, Eraser, Plus, RefreshCcw, SendHorizontal, Trash2 } from 'lucide-vue-next'
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
  type ThinkingMode,
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
const thinkingMode = ref<ThinkingMode>('AUTO')
const generatingState = ref<'idle' | 'thinking' | 'waiting' | 'typing'>('idle')

const selectedModel = computed(() => models.value.find((model) => model.id === selectedModelId.value))
const searchStateText = computed(() => {
  if (!status.value) return '未连接'
  if (!status.value.searchEnabled) return '关闭或未配置'
  return `${status.value.searchProvider || '已开启'} / ${status.value.searchStatus || '未测试'}`
})
const safetyStateText = computed(() => (status.value?.aiServiceOnline ? '平衡' : '仅记录'))
const modelSourceText = computed(() => {
  const provider = selectedModel.value?.provider || ''
  if (provider.toUpperCase().includes('OLLAMA')) return 'Ollama'
  if (provider.toUpperCase().includes('OPENAI')) return 'OpenAI-compatible'
  if (status.value?.ollamaReachable) return 'Ollama'
  if (status.value?.ollamaEnabled) return '本地'
  return 'mock / fallback'
})

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

async function send(textOverride?: string, modeOverride?: ThinkingMode) {
  if (loading.value) return
  const text = (textOverride ?? input.value).trim()
  if (!text) return
  if (!activeSession.value) {
    await newSession()
  }
  const sessionId = activeSession.value?.id
  if (!sessionId) return
  const previousInput = input.value
  if (!textOverride) input.value = ''
  loading.value = true
  generatingState.value = modeOverride === 'ON' || thinkingMode.value === 'ON' ? 'thinking' : 'waiting'
  const requestThinkingMode = modeOverride ?? thinkingMode.value
  messages.value.push({
    id: Date.now(),
    sessionId,
    role: 'user',
    content: text,
    searchUsed: false,
    thinkingMode: requestThinkingMode,
    createdAt: new Date().toISOString(),
  })
  await scrollToBottom()
  try {
    generatingState.value = 'typing'
    const result = (await sendAiChatMessageApi(sessionId, {
      message: text,
      modelId: selectedModelId.value,
      thinkingMode: requestThinkingMode,
    })).data
    messages.value = result.messages
    activeSession.value = result.session
    sessions.value = [result.session, ...sessions.value.filter((item) => item.id !== result.session.id)]
  } catch (error) {
    const message = resolveErrorMessage(error, 'AI 聊天暂不可用，请确认后端和 ai-service 已启动。')
    if (!textOverride) input.value = previousInput
    messages.value.push({
      id: Date.now() + 1,
      sessionId,
      role: 'assistant',
      content: `发送失败：${message}`,
      serviceMode: 'error',
      searchUsed: false,
      thinkingMode: requestThinkingMode,
      createdAt: new Date().toISOString(),
    })
    ElMessage.error(message)
  } finally {
    loading.value = false
    generatingState.value = 'idle'
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
  const message = maybe.response?.data?.message || maybe.message || fallback
  if (message.includes('No servers available') || message.includes('ai-service')) return 'AI service 不可用，请检查 academic-ai-service。'
  if (message.includes('Ollama') || message.includes('ollama')) return 'Ollama 未启动或模型不可用，请检查 OLLAMA 配置。'
  if (message.includes('模型不存在') || message.includes('停用')) return '模型不存在或已停用，请切换模型后重试。'
  if (message.includes('联网搜索') || message.includes('search')) return '联网搜索配置失败，请检查搜索配置。'
  if (message.includes('安全') || message.includes('拦截')) return '安全审查已拦截该请求。'
  if (message.includes('timeout') || message.includes('超时')) return 'AI 请求超时，请稍后重试。'
  if (message.includes('thinking')) return '当前模型不支持思考模式，请切换为自动。'
  return message
}

function changeThinkingMode(value: ThinkingMode) {
  thinkingMode.value = value
  const label = value === 'ON' ? '开启' : value === 'OFF' ? '关闭' : '自动'
  ElMessage.success(`已切换思考模式：${label}`)
}

function handleThinkingModeChange(value: string | number | boolean) {
  changeThinkingMode(String(value) as ThinkingMode)
}

function clearInput() {
  input.value = ''
}

async function copyAnswer(content: string) {
  await navigator.clipboard.writeText(content)
  ElMessage.success('回答已复制')
}

function regenerateLastAnswer() {
  const lastUser = [...messages.value].reverse().find((message) => message.role === 'user')
  if (!lastUser) {
    ElMessage.warning('暂无可重新生成的用户问题')
    return
  }
  void send(lastUser.content, lastUser.thinkingMode ?? thinkingMode.value)
}

function renderMessageContent(message: AiChatMessage) {
  if (message.role !== 'assistant') {
    return `<p>${escapeHtml(message.content).replace(/\n/g, '<br>')}</p>`
  }
  return renderMarkdown(message.content)
}

function renderMarkdown(source: string) {
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  const blocks: string[] = []
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]
    if (!line.trim()) continue

    if (line.trim().startsWith('```')) {
      const codeLines: string[] = []
      index += 1
      while (index < lines.length && !lines[index].trim().startsWith('```')) {
        codeLines.push(lines[index])
        index += 1
      }
      blocks.push(`<pre><code>${escapeHtml(codeLines.join('\n'))}</code></pre>`)
      continue
    }

    const heading = /^(#{1,4})\s+(.+)$/.exec(line)
    if (heading) {
      const level = heading[1].length + 1
      blocks.push(`<h${level}>${renderInline(heading[2].trim())}</h${level}>`)
      continue
    }

    const ordered = /^(\d+)\.\s+(.+)$/.exec(line)
    if (ordered) {
      const items: string[] = []
      let cursor = index
      while (cursor < lines.length) {
        const match = /^(\d+)\.\s+(.+)$/.exec(lines[cursor])
        if (!match) break
        const itemLines = [match[2].trim()]
        cursor += 1
        while (
          cursor < lines.length &&
          lines[cursor].trim() &&
          !/^(\d+)\.\s+/.test(lines[cursor]) &&
          !/^[-*]\s+/.test(lines[cursor])
        ) {
          itemLines.push(lines[cursor].trim())
          cursor += 1
        }
        while (cursor < lines.length && !lines[cursor].trim()) cursor += 1
        items.push(`<li>${itemLines.map((itemLine) => renderInline(itemLine)).join('<br>')}</li>`)
      }
      blocks.push(`<ol>${items.join('')}</ol>`)
      index = cursor - 1
      continue
    }

    const unordered = /^[-*]\s+(.+)$/.exec(line)
    if (unordered) {
      const items: string[] = []
      let cursor = index
      while (cursor < lines.length) {
        const match = /^[-*]\s+(.+)$/.exec(lines[cursor])
        if (!match) break
        items.push(`<li>${renderInline(match[1].trim())}</li>`)
        cursor += 1
      }
      blocks.push(`<ul>${items.join('')}</ul>`)
      index = cursor - 1
      continue
    }

    const paragraphLines = [line.trim()]
    let cursor = index + 1
    while (
      cursor < lines.length &&
      lines[cursor].trim() &&
      !/^#{1,4}\s+/.test(lines[cursor]) &&
      !/^(\d+)\.\s+/.test(lines[cursor]) &&
      !/^[-*]\s+/.test(lines[cursor]) &&
      !lines[cursor].trim().startsWith('```')
    ) {
      paragraphLines.push(lines[cursor].trim())
      cursor += 1
    }
    blocks.push(`<p>${paragraphLines.map((paragraphLine) => renderInline(paragraphLine)).join('<br>')}</p>`)
    index = cursor - 1
  }
  return blocks.join('')
}

function renderInline(source: string) {
  let html = escapeHtml(source)
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>')
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>')
  html = html.replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+|mailto:[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
  return html
}

function escapeHtml(source: string) {
  return source
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
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

      <section class="chat-status-bar">
        <el-tag type="info">模型：{{ selectedModel?.name || status?.defaultChatModel || '-' }}</el-tag>
        <el-tag>来源：{{ modelSourceText }}</el-tag>
        <el-tag :type="status?.searchEnabled ? 'success' : 'warning'">联网搜索：{{ searchStateText }}</el-tag>
        <el-tag type="warning">安全审查：{{ safetyStateText }}</el-tag>
        <el-tag type="success">思考模式：{{ thinkingMode === 'ON' ? '开启' : thinkingMode === 'OFF' ? '关闭' : '自动' }}</el-tag>
        <el-tag type="info">会话：{{ activeSession?.title || activeSession?.id || '-' }}</el-tag>
      </section>

      <div class="chat-toolbar">
        <el-select v-model="selectedModelId" class="model-select" filterable placeholder="选择聊天模型" @change="changeModel">
          <el-option v-for="model in models" :key="model.id" :label="model.name" :value="model.id">
            <span>{{ model.name }}</span>
            <small class="model-option">{{ model.modelName }}</small>
          </el-option>
        </el-select>
        <el-segmented
          v-model="thinkingMode"
          :options="[
            { label: '自动', value: 'AUTO' },
            { label: '开启', value: 'ON' },
            { label: '关闭', value: 'OFF' },
          ]"
          @change="handleThinkingModeChange"
        />
        <el-button :disabled="!activeSession" @click="activeSession && renameSession(activeSession)">重命名</el-button>
        <el-button :icon="RefreshCcw" :disabled="!activeSession || loading" @click="regenerateLastAnswer">重新生成</el-button>
        <el-button :icon="Trash2" :disabled="!activeSession" @click="activeSession && removeSession(activeSession)" />
      </div>

      <article ref="chatWindow" class="chat-window">
        <div v-if="messages.length === 0" class="chat-empty">暂无历史消息，发送第一条消息开始会话。</div>
        <div v-for="message in messages" :key="message.id" :class="['chat-message', message.role]">
          <Bot v-if="message.role === 'assistant'" :size="18" />
          <div class="message-content markdown-body" v-html="renderMessageContent(message)" />
          <small v-if="message.serviceMode || message.modelName">
            {{ message.serviceMode }}<template v-if="message.modelName"> / {{ message.modelName }}</template>
            <template v-if="message.searchUsed"> / 已联网搜索</template>
            <template v-if="message.thinkingMode"> / 思考：{{ message.thinkingMode }}</template>
          </small>
          <el-button
            v-if="message.role === 'assistant'"
            class="copy-answer"
            text
            size="small"
            :icon="Copy"
            @click="copyAnswer(message.content)"
          >
            复制
          </el-button>
          <small v-if="message.role === 'assistant' && message.serviceMode?.includes('fallback')" class="fallback-line">
            模型兜底：{{ message.modelName || '本地兜底' }}
          </small>
        </div>
        <div v-if="loading" class="chat-message assistant generating">
          <Bot :size="18" />
          <p>{{ generatingState === 'thinking' ? '正在思考...' : generatingState === 'typing' ? '正在生成回答...' : '正在等待模型响应...' }}</p>
        </div>
      </article>

      <div class="chat-input">
        <el-input v-model="input" type="textarea" :rows="3" maxlength="1000" show-word-limit @keydown="handleInputKeydown" />
        <el-button :icon="Eraser" :disabled="!input || loading" @click="clearInput">清空</el-button>
        <el-button type="primary" :icon="SendHorizontal" :loading="loading" @click="() => send()">发送</el-button>
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
  flex-wrap: wrap;
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

.chat-message small {
  display: block;
  margin-top: 6px;
  color: #6b7280;
}

.message-content {
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.message-content :deep(p),
.message-content :deep(ol),
.message-content :deep(ul),
.message-content :deep(pre),
.message-content :deep(h2),
.message-content :deep(h3),
.message-content :deep(h4),
.message-content :deep(h5) {
  margin: 0;
}

.message-content :deep(p + p),
.message-content :deep(p + ol),
.message-content :deep(p + ul),
.message-content :deep(ol + p),
.message-content :deep(ul + p),
.message-content :deep(pre + p) {
  margin-top: 10px;
}

.message-content :deep(ol),
.message-content :deep(ul) {
  padding-left: 22px;
}

.message-content :deep(li + li) {
  margin-top: 12px;
}

.message-content :deep(strong) {
  font-weight: 700;
}

.message-content :deep(a) {
  color: #2563eb;
  text-decoration: none;
}

.message-content :deep(a:hover) {
  text-decoration: underline;
}

.message-content :deep(code) {
  border-radius: 4px;
  background: #e5e7eb;
  padding: 1px 5px;
  font-family: Consolas, Monaco, monospace;
  font-size: 0.92em;
}

.message-content :deep(pre) {
  overflow-x: auto;
  border-radius: 6px;
  background: #111827;
  color: #f9fafb;
  padding: 10px 12px;
}

.message-content :deep(pre code) {
  background: transparent;
  color: inherit;
  padding: 0;
}

.chat-status-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.copy-answer {
  margin-top: 6px;
}

.chat-message.generating {
  opacity: 0.85;
}

.chat-input {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 12px;
  align-items: end;
}

@media (max-width: 900px) {
  .chat-layout {
    grid-template-columns: 1fr;
  }
}
</style>
