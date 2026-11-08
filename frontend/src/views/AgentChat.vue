<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { confirmDraft, discardDraft, extractByFile, extractByUrl, agentChat } from '../api/agent'
import { getDueCards } from '../api/review'
import { describeError } from '../utils/error'

// 消息列表：role = 'agent' | 'user'，drafts = 这条消息附带的草稿卡片
const messages = ref([])
const input = ref('')
const sending = ref(false)
const errorTip = ref('')
const listRef = ref(null)

// 另外两个输入源：文件、网页
const fileInput = ref(null)
const urlMode = ref(false)
const urlInput = ref('')

// 会话记录的最小持久化：存浏览器本地存储，刷新后恢复。
// 注意它是"不让历史消失"，不是"让 Agent 有记忆"——抽取本身是单轮的。
const STORAGE_KEY = 'kb-agent-messages'
const MAX_MESSAGES = 30

// 首屏：agent 先开口。
// 这类"主动性"不需要常驻进程，浏览器一打开就有一次请求，足以支撑叙事。
onMounted(async () => {
  // 有历史就先恢复，不再打首屏问候
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved) {
    try {
      const parsed = JSON.parse(saved)
      if (Array.isArray(parsed) && parsed.length > 0) {
        messages.value = parsed
        scrollToBottom()
        return
      }
    } catch (e) {
      // 本地存储可能被手动改坏，解析失败就丢掉，不能让页面白屏
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  try {
    const due = await getDueCards()
    const count = Array.isArray(due) ? due.length : 0
    messages.value.push({
      role: 'agent',
      text: count > 0
          ? `今天有 ${count} 张卡到期。你也可以把一段对话粘进来，我帮你从中抽知识点。`
          : '今天没有到期的卡。把一段对话粘进来，我帮你从中抽知识点。',
      drafts: []
    })
  } catch (e) {
    // 复习接口挂了也不能让对话页打不开，退化成一句普通欢迎语
    messages.value.push({
      role: 'agent',
      text: '把一段对话粘进来，我帮你从中抽知识点。',
      drafts: []
    })
  }
})

// 消息一变就落盘。deep 才能监听到 drafts 里 state 这种嵌套字段的修改
watch(messages, (val) => {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(val.slice(-MAX_MESSAGES)))
}, { deep: true })

// 只清前端记录，不动库里已入库的知识
const clearChat = () => {
  if (!window.confirm('清空当前对话？已入库的知识不受影响')) return
  messages.value = [{
    role: 'agent',
    text: '对话已清空。把一段对话粘进来吧。',
    drafts: []
  }]
  localStorage.removeItem(STORAGE_KEY)
}

const scrollToBottom = async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

const send = async () => {
  const text = input.value.trim()
  if (!text || sending.value) return
  errorTip.value = ''

  // 先取历史（不含当前这句），再 push 当前消息，避免同一句被发两遍
  const history = messages.value
      .slice(-6)
      .filter(m => m.text)
      .map(m => ({
        role: m.role === 'agent' ? 'assistant' : 'user',
        content: m.text
      }))

  messages.value.push({ role: 'user', text, drafts: [] })
  input.value = ''
  sending.value = true
  scrollToBottom()

  try {
    // 走后端 Agent 循环：由模型决定调哪些工具，可能一次调多个
    const result = await agentChat(text, history)
    messages.value.push({
      role: 'agent',
      text: result.reply || '模型没有返回内容。',
      // state 是前端自己加的状态，后端不关心
      drafts: (result.cards || []).map(i => ({ ...i, state: 'pending' })),
      steps: result.steps || []
    })
  } catch (e) {
    console.error('Agent 调用失败:', e)
    errorTip.value = describeError(e, '对话失败，请稍后重试')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

const confirm = async (draft) => {
  try {
    await confirmDraft(draft.id)
    draft.state = 'confirmed'
  } catch (e) {
    console.error('入库失败:', e)
    errorTip.value = describeError(e, '入库失败')
  }
}

const discard = async (draft) => {
  try {
    await discardDraft(draft.id)
    draft.state = 'discarded'
  } catch (e) {
    console.error('丢弃失败:', e)
    errorTip.value = describeError(e, '丢弃失败')
  }
}

// 文件入口：解析器已经分好条，后端逐条评分后返回草稿
const handleFile = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  event.target.value = ''            // 清空，允许再选同一个文件
  if (sending.value) return

  errorTip.value = ''
  sending.value = true
  messages.value.push({ role: 'user', text: `上传文件：${file.name}`, drafts: [] })
  scrollToBottom()

  try {
    const result = await extractByFile(file)
    const items = result.items || []
    messages.value.push({
      role: 'agent',
      text: items.length === 0
          ? '这个文件里没有能入库的内容。'
          : `从文件里整理出 ${items.length} 条，看哪几条要入库：`,
      drafts: items.map(i => ({ ...i, state: 'pending' }))
    })
  } catch (e) {
    console.error('文件抽取失败:', e)
    errorTip.value = describeError(e, '文件抽取失败')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

// 网页入口：后端调 DeepSeek Fetch 拿正文，再抽取
const submitUrl = async () => {
  const url = urlInput.value.trim()
  if (!url || sending.value) return

  errorTip.value = ''
  urlMode.value = false
  sending.value = true
  messages.value.push({ role: 'user', text: `抓取网页：${url}`, drafts: [] })
  urlInput.value = ''
  scrollToBottom()

  try {
    const result = await extractByUrl(url)
    const items = result.items || []
    messages.value.push({
      role: 'agent',
      text: items.length === 0
          ? '这个页面里没抽到值得保存的知识点。'
          : `从网页里抽出 ${items.length} 条，看哪几条要入库：`,
      drafts: items.map(i => ({ ...i, state: 'pending' }))
    })
  } catch (e) {
    console.error('网页抽取失败:', e)
    errorTip.value = describeError(e, '网页抓取失败')
  } finally {
    sending.value = false
    scrollToBottom()
  }
}
</script>

<template>
  <div class="chat-page">
    <div class="messages" ref="listRef">
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="bubble">{{ m.text }}</div>

        <!-- 模型自己调用了哪些工具，给用户看执行轨迹 -->
        <div v-if="m.steps && m.steps.length" class="steps">
          <span v-for="(s, si) in m.steps" :key="si" class="step">{{ s }}</span>
        </div>

        <!-- 草稿卡片：挂在 agent 那条消息下面 -->
        <div v-if="m.drafts && m.drafts.length" class="draft-list">
          <div
              v-for="d in m.drafts"
              :key="d.id"
              :class="['draft-card', d.state]"
          >
            <div class="draft-head">
              <span class="draft-title">{{ d.title }}</span>
              <span class="draft-star">{{ d.aiScore }} 星</span>
            </div>
            <div class="draft-content">{{ d.content }}</div>

            <div v-if="d.state === 'pending'" class="draft-actions">
              <button class="btn-primary" @click="confirm(d)">入库</button>
              <button class="btn-ghost" @click="discard(d)">丢弃</button>
            </div>
            <div v-else class="draft-state">
              {{ d.state === 'confirmed' ? '已入库' : '已丢弃' }}
            </div>
          </div>
        </div>
      </div>

      <div v-if="sending" class="msg agent">
        <div class="bubble typing">正在抽取…</div>
      </div>
    </div>

    <p v-if="errorTip" class="error-tip">{{ errorTip }}</p>

    <!-- 另外两个输入源 -->
    <div class="tools">
      <button class="tool-btn" :disabled="sending" @click="fileInput.click()">上传文件</button>
      <button class="tool-btn" :disabled="sending" @click="urlMode = true">抓取网页</button>
      <input
          ref="fileInput"
          type="file"
          accept=".md,.jsonl,.docx"
          style="display:none"
          @change="handleFile"
      />
    </div>

    <div v-if="urlMode" class="url-row">
      <input
          v-model="urlInput"
          type="text"
          placeholder="https://..."
          @keydown.enter.prevent="submitUrl"
      />
      <button class="btn-primary" @click="submitUrl">抓取</button>
      <button class="btn-ghost" @click="urlMode = false">取消</button>
    </div>

    <div class="composer">
      <button class="btn-ghost clear-btn" @click="clearChat">清空</button>
      <textarea
          v-model="input"
          placeholder="把一段对话粘到这里，Ctrl+Enter 发送"
          @keydown.enter.ctrl.prevent="send"
      ></textarea>
      <button class="send-btn" :disabled="sending || !input.trim()" @click="send">
        发送
      </button>
    </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
}
.messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px 16px;
}
.msg {
  display: flex;
  flex-direction: column;
  margin-bottom: 18px;
}
.msg.user {
  align-items: flex-end;
}
.msg.agent {
  align-items: flex-start;
}
.bubble {
  max-width: 720px;
  padding: 10px 14px;
  border-radius: 10px;
  line-height: 1.6;
  font-size: 15px;
  white-space: pre-wrap;
  word-break: break-word;
}
.msg.user .bubble {
  background: #1976d2;
  color: #fff;
  border-bottom-right-radius: 3px;
}
.msg.agent .bubble {
  background: #fff;
  color: #333;
  border: 1px solid #e0e0e0;
  border-bottom-left-radius: 3px;
}
.bubble.typing {
  color: #888;
  font-style: italic;
}
.steps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 6px;
}
.step {
  font-size: 12px;
  color: #1976d2;
  background: #e8f1fb;
  border-radius: 10px;
  padding: 2px 8px;
}
.draft-list {
  width: 100%;
  max-width: 720px;
  margin-top: 10px;
}
.draft-card {
  background: #fff;
  border: 1px solid #e0e0e0;
  border-left: 3px solid #1976d2;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 10px;
}
.draft-card.confirmed {
  border-left-color: #2e7d32;
  background: #f3faf3;
}
.draft-card.discarded {
  border-left-color: #bbb;
  background: #f7f7f7;
  opacity: 0.7;
}
.draft-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.draft-title {
  font-weight: 600;
  font-size: 15px;
  color: #222;
}
.draft-star {
  flex-shrink: 0;
  font-size: 13px;
  color: #f5a623;
}
.draft-content {
  margin-top: 6px;
  font-size: 14px;
  color: #555;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 160px;
  overflow-y: auto;
}
.draft-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}
.btn-primary,
.btn-ghost,
.send-btn {
  border: none;
  border-radius: 6px;
  padding: 7px 16px;
  font-size: 14px;
  cursor: pointer;
}
.btn-primary,
.send-btn {
  background: #1976d2;
  color: #fff;
}
.btn-primary:hover,
.send-btn:hover:not(:disabled) {
  background: #1565c0;
}
.btn-ghost {
  background: #fff;
  color: #666;
  border: 1px solid #ddd;
}
.btn-ghost:hover {
  background: #f5f5f5;
}
.send-btn:disabled {
  background: #b0c9e3;
  cursor: not-allowed;
}
.draft-state {
  margin-top: 10px;
  font-size: 13px;
  color: #888;
}
.error-tip {
  color: #d32f2f;
  font-size: 14px;
  margin: 0 0 8px;
}
.composer {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding-top: 12px;
  border-top: 1px solid #e0e0e0;
}
.composer textarea {
  flex: 1;
  height: 72px;
  resize: none;
  padding: 10px 12px;
  border: 1px solid #ccc;
  border-radius: 8px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
  outline: none;
}
.composer textarea:focus {
  border-color: #1976d2;
}
.send-btn {
  height: 40px;
}
.clear-btn {
  height: 40px;
  flex-shrink: 0;
}
.tools {
  display: flex;
  gap: 8px;
  padding: 8px 0 4px;
}
.tool-btn {
  background: #fff;
  border: 1px solid #ddd;
  border-radius: 6px;
  padding: 6px 14px;
  font-size: 13px;
  color: #555;
  cursor: pointer;
}
.tool-btn:hover:not(:disabled) {
  border-color: #1976d2;
  color: #1976d2;
}
.tool-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.url-row {
  display: flex;
  gap: 8px;
  padding: 4px 0 8px;
}
.url-row input {
  flex: 1;
  height: 36px;
  padding: 0 12px;
  border: 1px solid #ccc;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
}
.url-row input:focus {
  border-color: #1976d2;
}
.url-row .btn-primary,
.url-row .btn-ghost {
  height: 36px;
}
</style>
