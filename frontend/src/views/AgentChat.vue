<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { confirmDraft, discardDraft, extractByFile, extractByUrl, agentChat } from '../api/agent'
import { getDueCards, getProgress } from '../api/review'
import { me as currentUser, getAgentRule, updateAgentRule } from '../api/auth'
import { getAllKnowledge } from '../api/knowledge'
import { describeError } from '../utils/error'

// 消息列表：role = 'agent' | 'user'，drafts = 这条消息附带的草稿卡片
const messages = ref([])
const input = ref('')
const sending = ref(false)
const errorTip = ref('')
const listRef = ref(null)

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
let storageKey = 'kb-agent-messages-anon'
let customRuleKey = 'kb-agent-custom-rule-anon'

// 首屏：agent 先开口。
// 这类"主动性"不需要常驻进程，浏览器一打开就有一次请求，足以支撑叙事。
onMounted(async () => {
  // 右栏列表独立加载，不阻塞对话区的历史恢复
  loadKnowledge()
  loadRule()

  try {
    const info = await currentUser()
    storageKey = `kb-agent-messages-${info.id}`
    customRuleKey = `kb-agent-custom-rule-${info.id}`
    customRule.value = localStorage.getItem(customRuleKey) || ''
  } catch (e) {
    storageKey = 'kb-agent-messages-anon'   // 未登录时兜底，正常会被路由守卫拦住
  }
  localStorage.removeItem('kb-agent-messages')   // 清掉旧版本不带账号的 key

  // 有历史就先恢复，不再打首屏问候
  const saved = localStorage.getItem(storageKey)
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
      localStorage.removeItem(storageKey)
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
  if (!storageKey) return
  localStorage.setItem(storageKey, JSON.stringify(val.slice(-MAX_MESSAGES)))
}, { deep: true })

// 只清前端记录，不动库里已入库的知识
const clearChat = () => {
  menuOpen.value = false
  if (!window.confirm('清空当前对话？已入库的知识不受影响')) return
  messages.value = [{
    role: 'agent',
    text: '对话已清空。把一段对话粘进来吧。',
    drafts: []
  }]
  localStorage.removeItem(storageKey)
}

const scrollToBottom = async () => {
  await nextTick()
  if (listRef.value) {
    listRef.value.scrollTop = listRef.value.scrollHeight
  }
}

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
  <div class="chat-page">
    <div class="chat-main">
    <div class="messages" ref="listRef">
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
      <button class="btn-ghost" @click="saveCustomRule">保存</button>
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
            <button class="plus-btn" :disabled="sending" title="添加" @click.stop="menuOpen = !menuOpen">+</button>
            <div v-if="menuOpen" class="menu-mask" @click="menuOpen = false"></div>
            <div v-if="menuOpen" class="plus-menu">
              <button class="menu-item" @click="pickFile">
                <span>上传文件</span>
                <span class="menu-hint">.md / .jsonl / .docx</span>
              </button>
              <button class="menu-item" @click="clearChat">
                <span>清空对话</span>
              </button>
            </div>
          </div>

          <!-- 回答风格：从原来的顶部工具条挪进来，和加号同一行 -->
          <select class="rule-select" title="回答风格" :value="currentRule" @change="changeRule">
            <option v-for="r in rules" :key="r.value" :value="r.value">{{ r.label }}</option>
          </select>

          <span class="bar-spacer"></span>

          <button class="send-btn" :disabled="sending || !input.trim()" title="发送" @click="send">↑</button>
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
