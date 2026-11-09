<script setup>
import { ref, onMounted, onActivated, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppIcon from '../components/AppIcon.vue'
import { confirmDraft, discardDraft, extractByFile, extractByUrl, agentChat } from '../api/agent'
import { getDueCards, getProgress } from '../api/review'
import { me as currentUser, getAgentRule, updateAgentRule } from '../api/auth'
import { getAllKnowledge } from '../api/knowledge'
import { describeError } from '../utils/error'

// 显式声明组件名：App.vue 里的 <keep-alive include="AgentChat"> 靠它匹配。
// 有这个名字，切到知识库/复习页时本组件只是"失活"而不是销毁，
// 正在等待的 agent 请求回来后仍然能落到 messages 里，不会被丢掉。
defineOptions({ name: 'AgentChat' })

// 消息列表：role = 'agent' | 'user'，drafts = 这条消息附带的草稿卡片
const messages = ref([])
const input = ref('')
const sending = ref(false)
const errorTip = ref('')
const listRef = ref(null)
const lastScrollTop = ref(0)
let hasActivated = false

// 输入源：文件走左下角加号菜单，网页直接粘贴到输入框
const fileInput = ref(null)
const menuOpen = ref(false)

// 3 条示例提示词常驻在输入框上方：评委和用户随时能看到能干什么，不随对话历史消失
const examples = [
  { label: '今天该复习什么？', text: '今天该复习什么？' },
  { label: '搜一下我记过的 SQLite', text: '搜一下我记过的 SQLite 相关问题' },
  {
    label: '整理这段，再看该复习什么',
    text: '整理这段并告诉我今天该复习什么：Spring 循环依赖是因为两个 Bean 互相注入，构造器注入会在启动时直接失败，字段注入只是把报错推迟到运行时。'
  }
]

// 点示例 → 填进输入框，不自动发送，给用户改的机会
const fillExample = (text) => {
  input.value = text
}

// 右侧知识面板
const router = useRouter()
const knowledge = ref([])
const progress = ref({})   // knowledgeId -> 最高阶数

const loadKnowledge = async () => {
  try {
    knowledge.value = (await getAllKnowledge()) || []
  } catch (e) {
    // 右栏失败不能让对话页打不开，静默降级成空列表
    console.warn('知识列表加载失败', e)
  }
  try {
    progress.value = (await getProgress()) || {}
  } catch (e) {
    console.warn('复习进度加载失败', e)
  }
}

const openDetail = (id) => router.push(`/detail/${id}`)

// 回答风格：存在 user 表里，改一次对所有对话生效
const rules = [
  { value: 'EXPLAIN', label: '讲解' },
  { value: 'EXAM', label: '考试' },
  { value: 'SOCRATIC', label: '追问' },
  { value: 'CUSTOM', label: '自定义' }
]
const currentRule = ref('EXPLAIN')
// 自定义风格的原文：不存库（加一列要改表，风险大），存在浏览器本地，随每轮对话发给后端
const customRule = ref('')

const loadRule = async () => {
  try {
    const saved = await getAgentRule()
    if (saved) currentRule.value = saved
  } catch (e) {
    console.warn('读取回答风格失败', e)
  }
}

const changeRule = async (event) => {
  const value = event.target.value
  currentRule.value = value
  try {
    await updateAgentRule(value)
  } catch (e) {
    console.error('保存回答风格失败:', e)
    errorTip.value = '风格切换失败，请重试'
  }
}

// 自定义文案落盘：key 也带 userId，否则换账号会看到上一个人的规则
const saveCustomRule = () => {
  localStorage.setItem(customRuleKey, customRule.value.trim())
}

/** 去掉模型残留的 Markdown 标记：** 加粗、行首 # 标题、` 反引号，让气泡里是干净中文 */
const cleanText = (text) => {
  if (!text) return ''
  return text
      .replace(/\*\*/g, '')
      .replace(/^#{1,6}\s+/gm, '')
      .replace(/`/g, '')
}

/** 某条知识复习到第几阶：没建卡就是 0，前端按 7 格渲染 */
const stageOf = (id) => progress.value[id] || 0

/** 色条颜色按星级分档，一眼看出哪些更值得复习 */
const barClass = (score) => {
  if (score >= 5) return 'bar-5'
  if (score === 4) return 'bar-4'
  if (score === 3) return 'bar-3'
  return 'bar-low'
}

// 会话记录的最小持久化：存浏览器本地存储，刷新后恢复。
// 注意它是"不让历史消失"，不是"让 Agent 有记忆"——抽取本身是单轮的。
// key 必须带用户 id：localStorage 是浏览器级别的，不区分账号会把 A 的对话给 B 看。
const MAX_MESSAGES = 30
const conversations = ref([])
const activeConversationId = ref('')
let conversationsKey = 'kb-agent-conversations-anon'
let activeConversationKey = 'kb-agent-active-anon'
let legacyMessagesKey = 'kb-agent-messages-anon'
let customRuleKey = 'kb-agent-custom-rule-anon'

const cloneMessages = (items) => JSON.parse(JSON.stringify(items || []))

const conversationTitle = (items) => {
  const firstUser = (items || []).find((message) => message.role === 'user' && message.text)
  const text = firstUser?.text?.replace(/\s+/g, ' ').trim() || ''
  return text ? (text.length > 16 ? `${text.slice(0, 16)}…` : text) : '新任务'
}

const persistConversations = () => {
  const active = conversations.value.find((item) => item.id === activeConversationId.value)
  if (active) {
    active.messages = cloneMessages(messages.value.slice(-MAX_MESSAGES))
    active.title = conversationTitle(active.messages)
    active.updatedAt = Date.now()
  }

  localStorage.setItem(conversationsKey, JSON.stringify(
      conversations.value.map((item) => ({
        ...item,
        messages: item.messages.slice(-MAX_MESSAGES)
      }))
  ))
  if (activeConversationId.value) {
    localStorage.setItem(activeConversationKey, activeConversationId.value)
  }
}

const activateConversation = async (conversation) => {
  activeConversationId.value = conversation.id
  messages.value = cloneMessages(conversation.messages)
  lastScrollTop.value = 0
  persistConversations()
  await scrollToBottom()
}

const restoreConversations = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(conversationsKey) || '[]')
    if (!Array.isArray(saved) || saved.length === 0) return false

    conversations.value = saved
        .filter((item) => item && item.id && Array.isArray(item.messages))
        .map((item) => ({
          ...item,
          title: item.title || conversationTitle(item.messages),
          messages: item.messages.slice(-MAX_MESSAGES)
        }))
    if (conversations.value.length === 0) return false

    const savedActiveId = localStorage.getItem(activeConversationKey)
    const active = conversations.value.find((item) => item.id === savedActiveId)
        || conversations.value[0]
    activeConversationId.value = active.id
    messages.value = cloneMessages(active.messages)
    scrollToBottom()
    return true
  } catch (error) {
    console.warn('恢复对话任务失败，忽略损坏的本地数据', error)
    return false
  }
}

const migrateLegacyConversation = () => {
  try {
    const saved = JSON.parse(localStorage.getItem(legacyMessagesKey) || '[]')
    if (!Array.isArray(saved) || saved.length === 0) return false
    const conversation = {
      id: `legacy-${Date.now()}`,
      title: conversationTitle(saved),
      messages: saved.slice(-MAX_MESSAGES),
      createdAt: Date.now(),
      updatedAt: Date.now()
    }
    conversations.value = [conversation]
    activeConversationId.value = conversation.id
    messages.value = cloneMessages(conversation.messages)
    localStorage.removeItem(legacyMessagesKey)
    persistConversations()
    scrollToBottom()
    return true
  } catch (error) {
    localStorage.removeItem(legacyMessagesKey)
    return false
  }
}

// 首屏：优先恢复任务列表，其次迁移旧版单会话，最后创建第一条任务。
onMounted(async () => {
  loadKnowledge()
  loadRule()

  try {
    const info = await currentUser()
    conversationsKey = `kb-agent-conversations-${info.id}`
    activeConversationKey = `kb-agent-active-${info.id}`
    legacyMessagesKey = `kb-agent-messages-${info.id}`
    customRuleKey = `kb-agent-custom-rule-${info.id}`
    customRule.value = localStorage.getItem(customRuleKey) || ''
  } catch (e) {
    conversationsKey = 'kb-agent-conversations-anon'
    activeConversationKey = 'kb-agent-active-anon'
    legacyMessagesKey = 'kb-agent-messages-anon'
  }
  localStorage.removeItem('kb-agent-messages')   // 清掉旧版本不带账号的 key

  if (restoreConversations() || migrateLegacyConversation()) return

  let welcome
  try {
    const due = await getDueCards()
    const count = Array.isArray(due) ? due.length : 0
    welcome = count > 0
        ? `今天有 ${count} 张卡到期。你也可以把一段对话粘进来，我帮你从中抽知识点。`
        : '今天没有到期的卡。把一段对话粘进来，我帮你从中抽知识点。'
  } catch (e) {
    welcome = '把一段对话粘进来，我帮你从中抽知识点。'
  }

  const firstConversation = {
    id: `conversation-${Date.now()}`,
    title: '新任务',
    messages: [{
      role: 'agent',
      text: welcome,
      drafts: []
    }],
    createdAt: Date.now(),
    updatedAt: Date.now()
  }
  conversations.value = [firstConversation]
  activeConversationId.value = firstConversation.id
  messages.value = cloneMessages(firstConversation.messages)
  persistConversations()
  scrollToBottom()
})

// 消息一变就落盘。deep 才能监听到 drafts 里 state 这种嵌套字段的修改
watch(messages, () => {
  if (activeConversationId.value) {
    persistConversations()
  }
}, { deep: true })

const switchConversation = async (id) => {
  if (id === activeConversationId.value) return
  persistConversations()
  const conversation = conversations.value.find((item) => item.id === id)
  if (conversation) {
    await activateConversation(conversation)
  }
}

const newConversation = async () => {
  persistConversations()
  const conversation = {
    id: `conversation-${Date.now()}`,
    title: '新任务',
    messages: [{
      role: 'agent',
      text: '新任务已建立。你可以继续问知识库、复习安排，或者粘贴内容让我整理。',
      drafts: []
    }],
    createdAt: Date.now(),
    updatedAt: Date.now()
  }
  conversations.value.unshift(conversation)
  activeConversationId.value = conversation.id
  messages.value = cloneMessages(conversation.messages)
  lastScrollTop.value = 0
  persistConversations()
  await scrollToBottom()
}

// 只清当前任务的前端记录，不动其他任务和库里已入库的知识
const clearChat = () => {
  menuOpen.value = false
  if (!window.confirm('清空当前对话任务？其他任务和已入库的知识不受影响')) return
  messages.value = [{
    role: 'agent',
    text: '对话已清空。把一段对话粘进来吧。',
    drafts: []
  }]
  persistConversations()
}

const scrollToBottom = async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

const handleScroll = () => {
  if (listRef.value) {
    lastScrollTop.value = listRef.value.scrollTop
  }
}

onActivated(async () => {
  if (!hasActivated) {
    hasActivated = true
    return
  }
  await nextTick()
  if (listRef.value) {
    const maxScroll = Math.max(0, listRef.value.scrollHeight - listRef.value.clientHeight)
    listRef.value.scrollTop = Math.min(lastScrollTop.value, maxScroll)
  }
})

/** 是否是网址：http(s) 开头、中间没有空格。带说明文字的一律当对话处理 */
const isUrl = (text) => /^https?:\/\/\S+$/i.test(text)

// 加号菜单里点了"上传文件"：先收起菜单再打开文件选择框
const pickFile = () => {
  menuOpen.value = false
  fileInput.value?.click()
}

const send = async () => {
  const text = input.value.trim()
  if (!text || sending.value) return
  errorTip.value = ''

  // 粘贴的是网址 → 不是聊天内容，走后端抓正文再抽取
  if (isUrl(text)) {
    await extractUrl(text)
    return
  }

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
    const result = await agentChat(
        text,
        history,
        currentRule.value === 'CUSTOM' ? customRule.value.trim() : null
    )
    messages.value.push({
      role: 'agent',
      text: result.reply || '模型没有返回内容。',
      // state 是前端自己加的状态，后端不关心
      drafts: (result.cards || []).map(i => ({ ...i, state: 'pending' })),
      steps: result.steps || []
    })
    if ((result.steps || []).some((step) => step.includes('edit_knowledge'))) {
      loadKnowledge()
    }
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
    // 入库后立刻刷新右栏：否则卡片显示"已入库"、右边列表却没有它，看起来像没存进去
    loadKnowledge()
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

// 网址入口：后端调 DeepSeek Fetch 拿正文，再抽取
const extractUrl = async (url) => {
  if (sending.value) return

  errorTip.value = ''
  input.value = ''
  sending.value = true
  messages.value.push({ role: 'user', text: `抓取网页：${url}`, drafts: [] })
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
  <div class="chat-shell">
    <header class="page-header chat-header">
      <div class="page-heading">
        <div class="page-kicker">Agent workspace</div>
        <h1 class="page-title">对话</h1>
        <p class="page-subtitle">与知识库一起思考，把值得保留的内容整理成可复习的知识。</p>
      </div>
      <div class="page-actions">
        <span class="session-status"><i></i>会话已自动保存</span>
        <button class="btn btn-ghost" type="button" @click="newConversation">
          <AppIcon name="edit" :size="15" />
          新建对话
        </button>
      </div>
    </header>

    <nav v-if="conversations.length" class="conversation-tabs" aria-label="对话任务">
      <button
          v-for="conversation in conversations"
          :key="conversation.id"
          class="conversation-tab"
          :class="{ active: conversation.id === activeConversationId }"
          type="button"
          :title="conversation.title"
          @click="switchConversation(conversation.id)"
      >
        {{ conversation.title }}
      </button>
    </nav>

    <div class="chat-page">
    <div class="chat-main">
    <div class="messages" ref="listRef" @scroll.passive="handleScroll">
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="bubble">{{ cleanText(m.text) }}</div>

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
              <button class="btn-primary" type="button" @click="confirm(d)">入库</button>
              <button class="btn-ghost" type="button" @click="discard(d)">丢弃</button>
            </div>
            <div v-else class="draft-state">
              {{ d.state === 'confirmed' ? '已入库' : '已丢弃' }}
            </div>
          </div>
        </div>
      </div>

      <div v-if="sending" class="msg agent">
        <div class="bubble typing">正在思考…</div>
      </div>
    </div>

    <p v-if="errorTip" class="error-tip">{{ errorTip }}</p>

    <!-- 隐藏的文件选择框：由左下角加号的菜单触发 -->
    <div class="hidden-file">
      <input
          ref="fileInput"
          type="file"
          accept=".md,.jsonl,.docx"
          style="display:none"
          @change="handleFile"
      />
    </div>

    <!-- 自定义风格：只选了"自定义"才出现，回车或失焦自动保存 -->
    <div v-if="currentRule === 'CUSTOM'" class="url-row">
      <input
          v-model="customRule"
          type="text"
          maxlength="200"
          placeholder="例如：全部用英文回答，术语后面用括号解释"
          @change="saveCustomRule"
          @keydown.enter.prevent="saveCustomRule"
      />
      <button class="btn-ghost" type="button" @click="saveCustomRule">保存</button>
    </div>

    <div class="examples">
      <button
          v-for="ex in examples"
          :key="ex.label"
          class="example-chip"
          :disabled="sending"
          @click="fillExample(ex.text)"
      >{{ ex.label }}</button>
    </div>

    <div class="composer">
      <div class="composer-box">
      <textarea
          v-model="input"
          placeholder="粘贴一段对话或一个网址到这里"
          @keydown.enter.exact.prevent="send"
          @keydown.enter.ctrl.prevent="input += '\n'"
      ></textarea>
        <div class="composer-bar">
          <!-- 左下角加号：文件和清空都收进菜单，输入区不再堆按钮 -->
          <div class="plus-wrap">
            <button class="plus-btn" type="button" :disabled="sending" title="添加" @click.stop="menuOpen = !menuOpen">
              <AppIcon name="plus" :size="17" />
            </button>
            <div v-if="menuOpen" class="menu-mask" @click="menuOpen = false"></div>
            <div v-if="menuOpen" class="plus-menu">
              <button class="menu-item" type="button" @click="pickFile">
                <span>上传文件</span>
                <span class="menu-hint">.md / .jsonl / .docx</span>
              </button>
              <button class="menu-item" type="button" @click="clearChat">
                <span>清空当前对话</span>
              </button>
            </div>
          </div>

          <!-- 回答风格：从原来的顶部工具条挪进来，和加号同一行 -->
          <select class="rule-select" title="回答风格" :value="currentRule" @change="changeRule">
            <option v-for="r in rules" :key="r.value" :value="r.value">{{ r.label }}</option>
          </select>

          <span class="bar-spacer"></span>

          <button class="send-btn" type="button" :disabled="sending || !input.trim()" title="发送" @click="send">
            <AppIcon name="send" :size="17" />
          </button>
        </div>
      </div>
    </div>
    </div>

    <!-- 右栏：我的知识。对话页不再是一个孤立的聊天框 -->
    <aside class="knowledge-panel">
      <div class="panel-head">
        <span>我的知识</span>
        <span class="panel-count">{{ knowledge.length }}</span>
      </div>
      <p class="panel-hint">左侧色条＝AI 评级，右侧 7 格＝复习到第几阶（全灰＝还没建卡）</p>
      <div class="k-list">
        <div
            v-for="k in knowledge"
            :key="k.id"
            class="k-row"
            @click="openDetail(k.id)"
        >
          <span class="k-bar" :class="barClass(k.aiScore)"></span>
          <span class="k-title">{{ k.title }}</span>
          <span class="k-progress" :title="'复习到第 ' + stageOf(k.id) + ' 阶（共 7 阶）'">
            <span
                v-for="n in 7"
                :key="n"
                class="k-seg"
                :class="{ on: n <= stageOf(k.id) }"
            ></span>
          </span>
          <span class="k-score">{{ k.aiScore }}★</span>
        </div>
        <p v-if="knowledge.length === 0" class="k-empty">
          还没有知识，先在左边整理一段吧。
        </p>
      </div>
    </aside>
  </div>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  gap: 20px;
  height: calc(100vh - 140px);
}
.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}
.knowledge-panel {
  width: 380px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #eee;
  border-radius: 12px;
  padding: 14px 12px;
  overflow: hidden;
}
.panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 14px;
  font-weight: 600;
  color: #333;
  padding: 0 6px 10px;
  border-bottom: 1px solid #f0f0f0;
}
.panel-count {
  font-size: 12px;
  font-weight: 400;
  color: #888;
}
.panel-hint {
  font-size: 11px;
  color: #adb5bd;
  line-height: 1.5;
  padding: 8px 6px 2px;
}
.k-list {
  flex: 1;
  overflow-y: auto;
  padding-top: 6px;
}
.k-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 6px;
  border-radius: 6px;
  cursor: pointer;
}
.k-row:hover {
  background: #f5f7fb;
}
.k-bar {
  width: 4px;
  height: 26px;
  border-radius: 2px;
  flex-shrink: 0;
}
.bar-5 {
  background: #3b5bdb;
}
.bar-4 {
  background: #748ffc;
}
.bar-3 {
  background: #a5b4fc;
}
.bar-low {
  background: #ced4da;
}
.k-title {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.k-progress {
  width: 56px;
  height: 5px;
  flex-shrink: 0;
  display: flex;
  gap: 2px;
}
.k-seg {
  flex: 1;
  border-radius: 2px;
  background: #e9ecef;
}
.k-seg.on {
  background: #3b6ef5;
}
.k-score {
  flex-shrink: 0;
  font-size: 12px;
  color: #f5a623;
}
.k-empty {
  font-size: 13px;
  color: #999;
  padding: 16px 6px;
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
  background: #3b6ef5;
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
  color: #3b6ef5;
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
  border-left: 3px solid #3b6ef5;
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
  background: #3b6ef5;
  color: #fff;
}
.btn-primary:hover,
.send-btn:hover:not(:disabled) {
  background: #2f5cd8;
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
  padding-top: 12px;
  border-top: 1px solid #e0e0e0;
}
.composer-box {
  border: 1px solid #d5dae2;
  border-radius: 14px;
  background: #fff;
  padding: 8px 10px 8px 12px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.composer-box:focus-within {
  border-color: #3b6ef5;
  box-shadow: 0 0 0 3px rgba(59, 110, 245, 0.1);
}
.composer textarea {
  display: block;
  width: 100%;
  height: 64px;
  padding: 4px 4px 8px;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.6;
}
.composer-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.plus-wrap {
  position: relative;
}
.plus-btn {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #dfe3e8;
  border-radius: 50%;
  background: #fff;
  color: #555;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}
.plus-btn:hover:not(:disabled) {
  border-color: #3b6ef5;
  color: #3b6ef5;
}
.plus-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.menu-mask {
  position: fixed;
  inset: 0;
  z-index: 9;
}
.plus-menu {
  position: absolute;
  left: 0;
  bottom: 38px;
  z-index: 10;
  width: 200px;
  padding: 6px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}
.menu-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: 6px;
  background: none;
  text-align: left;
  font-size: 13px;
  color: #333;
  cursor: pointer;
}
.menu-item:hover {
  background: #f5f7fa;
}
.menu-hint {
  margin-top: 2px;
  font-size: 11px;
  color: #98a2b3;
}
.rule-select {
  height: 30px;
  padding: 0 4px;
  border: none;
  background: transparent;
  font-size: 13px;
  color: #666;
  cursor: pointer;
  outline: none;
}
.bar-spacer {
  flex: 1;
}
.send-btn {
  width: 34px;
  height: 34px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 50%;
  background: #3b6ef5;
  color: #fff;
  font-size: 16px;
  cursor: pointer;
}
.send-btn:hover:not(:disabled) {
  background: #2f5cd8;
}
.send-btn:disabled {
  background: #c9d6f5;
  cursor: not-allowed;
}
.examples {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 4px 0 10px;
}
.example-chip {
  padding: 6px 12px;
  border: 1px solid #e3e8ef;
  border-radius: 16px;
  background: #f8fafc;
  color: #475467;
  font-size: 13px;
  cursor: pointer;
  transition: border-color 0.15s, color 0.15s;
}
.example-chip:hover:not(:disabled) {
  border-color: #3b6ef5;
  color: #3b6ef5;
}
.example-chip:disabled {
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
  border-color: #3b6ef5;
}
.url-row .btn-primary,
.url-row .btn-ghost {
  height: 36px;
}
</style>

<style scoped>
.chat-shell {
  max-width: 1420px;
  margin: 0 auto;
}

.chat-header {
  margin-bottom: 18px;
}

.session-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--color-muted);
  font-size: 10px;
}

.session-status i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #48a47d;
}

.conversation-tabs {
  display: flex;
  gap: 6px;
  max-width: 100%;
  margin: -4px 0 10px;
  padding-bottom: 2px;
  overflow-x: auto;
  scrollbar-width: none;
}

.conversation-tabs::-webkit-scrollbar {
  display: none;
}

.conversation-tab {
  flex-shrink: 0;
  max-width: 180px;
  height: 30px;
  padding: 0 11px;
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: 6px;
  background: var(--color-surface);
  color: var(--color-muted);
  cursor: pointer;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conversation-tab:hover {
  border-color: #bdcaDD;
  color: var(--color-ink-soft);
}

.conversation-tab.active {
  border-color: #b9cbf1;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 650;
}

.chat-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 18px;
  height: calc(100vh - 224px);
  min-height: 620px;
}

.chat-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.messages {
  padding: 24px 30px 16px;
}

.msg {
  margin-bottom: 20px;
}

.bubble {
  max-width: 680px;
  padding: 13px 16px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.75;
}

.msg.user .bubble {
  background: var(--color-primary);
  color: #fff;
  border-bottom-right-radius: 3px;
}

.msg.agent .bubble {
  border: 1px solid #e2e7ee;
  background: #fbfcfe;
  color: #333d4b;
  border-bottom-left-radius: 3px;
}

.bubble.typing {
  color: var(--color-muted);
}

.steps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.step {
  padding: 4px 9px;
  border-radius: 9px;
  background: var(--color-primary-soft);
  color: #3f68bb;
  font-size: 10px;
}

.draft-list {
  width: min(100%, 720px);
  max-width: none;
}

.draft-card {
  margin-bottom: 8px;
  padding: 12px 14px;
  border: 1px solid #dfe5ed;
  border-left: 3px solid var(--color-primary);
  border-radius: 7px;
  background: #fff;
}

.draft-card.confirmed {
  border-left-color: var(--color-success);
  background: #fbfdfc;
}

.draft-card.discarded {
  border-left-color: #a7b0bd;
  background: #f7f8fa;
}

.draft-title {
  font-size: 12px;
}

.draft-star {
  color: var(--color-amber);
  font-size: 10px;
}

.draft-content {
  color: #748094;
  font-size: 11px;
  line-height: 1.65;
}

.btn-primary,
.btn-ghost {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 29px;
  padding: 0 11px;
  border-radius: 5px;
  font-size: 9px;
  font-weight: 600;
}

.btn-primary {
  border: 1px solid var(--color-primary);
  background: var(--color-primary);
  color: #fff;
}

.btn-ghost {
  border: 1px solid var(--color-line);
  background: #fff;
  color: var(--color-ink-soft);
}

.composer {
  padding-top: 10px;
  border-top: 1px solid #edf0f4;
  background: #fbfcfe;
}

.examples {
  padding: 0 24px 9px;
}

.example-chip {
  padding: 5px 10px;
  border: 1px solid #e1e6ee;
  border-radius: 12px;
  background: #fff;
  color: #617086;
  font-size: 10px;
}

.composer-box {
  margin: 0 24px 18px;
  border: 1px solid #cfd8e6;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 0 0 3px rgba(47, 105, 242, 0.05);
}

.composer textarea {
  height: 68px;
  font-size: 13px;
}

.plus-btn {
  width: 29px;
  height: 29px;
  border-color: #dce2ea;
  color: #657287;
}

.rule-select {
  color: #687589;
  font-size: 10px;
}

.send-btn {
  width: 32px;
  height: 32px;
  background: var(--color-primary);
}

.send-btn:disabled {
  background: #c7d6f3;
}

.knowledge-panel {
  width: auto;
  min-height: 0;
  padding: 16px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.panel-head {
  color: var(--color-ink);
  font-size: 13px;
}

.panel-count {
  font-size: 10px;
}

.panel-hint {
  color: var(--color-muted);
  font-size: 10px;
}

.k-row {
  gap: 9px;
  min-height: 50px;
  padding: 8px 3px;
  border-bottom: 1px solid #edf0f4;
  border-radius: 0;
}

.k-row:hover {
  background: #f8faff;
}

.k-title {
  font-size: 11px;
}

.k-score {
  color: var(--color-amber);
  font-size: 9px;
}

.k-seg {
  background: #e6eaf0;
}

.k-seg.on,
.k-progress .k-seg.on {
  background: #6b90e9;
}

.url-row {
  padding: 0 24px 9px;
}

@media (max-width: 1120px) {
  .chat-page {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
    min-height: 0;
  }

  .knowledge-panel {
    display: none;
  }

  .chat-main {
    min-height: 660px;
  }
}

@media (max-width: 760px) {
  .chat-header {
    margin-bottom: 16px;
  }

  .session-status,
  .chat-header .page-actions {
    display: none;
  }

  .chat-page {
    height: calc(100dvh - 282px);
    min-height: 0;
  }

  .chat-main {
    min-height: 0;
  }

  .messages {
    padding: 18px 14px 12px;
  }

  .bubble {
    max-width: 88%;
    font-size: 12px;
  }

  .examples {
    overflow-x: auto;
    flex-wrap: nowrap;
    padding: 0 14px 9px;
  }

  .example-chip {
    flex-shrink: 0;
  }

  .composer-box {
    margin: 0 14px 14px;
  }
}
</style>
