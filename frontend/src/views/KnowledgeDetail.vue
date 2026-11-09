<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import MarkdownIt from 'markdown-it'
import AppIcon from '../components/AppIcon.vue'
import CondenseDialog from '../components/CondenseDialog.vue'
import {
  getKnowledgeById,
  updateKnowledge,
  deleteKnowledge,
  condenseKnowledge,
  generateKnowledgeTags
} from '../api/knowledge'
import { generateCards } from '../api/review'
import { describeError } from '../utils/error'

const route = useRoute()
const router = useRouter()

const knowledge = ref(null)
const loading = ref(true)
// 是否处于编辑模式：false = 只读查看，true = 表单编辑
const editing = ref(false)
const condensing = ref(false)
const condenseDialogOpen = ref(false)
const condenseMsg = ref('')
const generatingTags = ref(false)
const tagMsg = ref('')
// 编辑草稿：独立于 knowledge，取消时整份丢弃，不污染原数据
const editForm = ref({})

// Markdown 渲染器：html:false 禁止内嵌 HTML（正文是 AI 生成的，防注入）
const md = new MarkdownIt({ html: false, linkify: true, breaks: true })

const formatDateTime = (value) => {
  if (!value) return '未知'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

const formatSource = (source) => {
  const labels = {
    MANUAL: '手动录入',
    TRAE_EXPORT: 'Trae 对话',
    TRAE_IMPORT: 'Trae 导入',
    AGENT_EXTRACT: 'Agent 整理'
  }
  return labels[source] || source || '手动录入'
}

/** 把正文的 Markdown 渲染成 HTML，星号、竖线表格、代码块都会变成正常排版 */
const renderedContent = computed(() => {
  const text = knowledge.value?.content
  if (!text) return ''
  return md.render(text)
})

// 进入编辑：把当前详情拷贝一份当草稿
const openEdit = () => {
  editForm.value = { ...knowledge.value }
  editing.value = true
}

// 取消编辑：草稿直接扔掉
const cancelEdit = () => {
  editing.value = false
}

// 保存
const saveEdit = async () => {
  try {
    const id = route.params.id

    const updated = await updateKnowledge(id, editForm.value)
    knowledge.value = updated
    editing.value = false
  } catch (error) {
    console.error('保存失败:', error)

    const message =
        error.response?.data?.message ||
        '保存失败，请稍后重试'

    alert(message)
  }
}
onMounted(async () => {
  try {
    const id = route.params.id
    knowledge.value = await getKnowledgeById(id)
  } catch (error) {
    console.error('获取详情失败:', error)
  } finally {
    loading.value = false
  }
})

const goBack = () => {
  router.push('/')
}

const handleDelete = async () => {
  if (!confirm('确定要删除这条知识吗？')) return
  try {
    const id = route.params.id
    await deleteKnowledge(id)
    alert('删除成功')
    router.push('/')
  } catch (error) {
    console.error('删除失败:', error)
    alert('删除失败')
  }
}

const handleCondense = () => {
  if (condensing.value) return
  condenseDialogOpen.value = true
}

const confirmCondense = async (keepOriginal) => {
  if (condensing.value) return
  condensing.value = true
  condenseMsg.value = ''
  try {
    knowledge.value = await condenseKnowledge(route.params.id, keepOriginal)
    condenseDialogOpen.value = false
    condenseMsg.value = keepOriginal
        ? 'AI 已生成精简版并放在原文上方，个人备注和评分保持不变。'
        : 'AI 已精简这张知识卡，个人备注和评分保持不变。'
  } catch (error) {
    console.error('AI 精简知识卡失败:', error)
    condenseDialogOpen.value = false
    condenseMsg.value = describeError(error, 'AI 精简失败，请稍后重试')
  } finally {
    condensing.value = false
  }
}

const cancelCondense = () => {
  if (!condensing.value) condenseDialogOpen.value = false
}

const handleGenerateTags = async () => {
  if (generatingTags.value) return
  generatingTags.value = true
  tagMsg.value = ''
  try {
    knowledge.value = await generateKnowledgeTags(route.params.id)
    tagMsg.value = knowledge.value.tags
        ? `已生成标签：${knowledge.value.tags}`
        : 'AI 没有返回有效标签，请重试'
  } catch (error) {
    console.error('生成标签失败:', error)
    tagMsg.value = describeError(error, '生成标签失败，请稍后重试')
  } finally {
    generatingTags.value = false
  }
}

  const generating = ref(false)      // 独立 loading：AI 可能要十几秒
  const reviewMsg = ref('')          // 提示文案
  const reviewMsgType = ref('')      // 'ok' | 'error'
  const hasCards = ref(false)        // 生成后是否显示"去复习"入口

  const handleGenerate = async () => {
    if (generating.value) return
    generating.value = true
    reviewMsg.value = ''
    try {
      const cards = await generateCards(route.params.id)
      hasCards.value = cards.length > 0
      reviewMsgType.value = 'ok'
      // 接口不区分"新建"和"已存在"，所以文案保持中性
      reviewMsg.value = `已准备好 ${cards.length} 张复习卡`
    } catch (error) {
      console.error('生成复习卡失败:', error)
      reviewMsgType.value = 'error'
      reviewMsg.value = describeError(error, '生成复习卡失败，请稍后重试')
    } finally {
      generating.value = false
    }
  }
</script>

<template>
  <div>
    <p v-if="loading">加载中...</p>
    <div v-else-if="knowledge" class="detail">
      <div class="top-actions">
        <button class="back-btn" type="button" @click="goBack">
          <AppIcon name="arrow-left" :size="16" />
          返回知识库
        </button>
        <div class="actions-right">
          <button v-if="!editing" class="tag-edit-btn" type="button" :disabled="generatingTags" @click="handleGenerateTags">
            <AppIcon name="tag" :size="15" />
            {{ generatingTags ? '生成标签中…' : (knowledge.tags ? 'AI 重写标签' : 'AI 补标签') }}
          </button>
          <button v-if="!editing" class="ai-edit-btn" type="button" :disabled="condensing" @click="handleCondense">
            <AppIcon name="sparkles" :size="15" />
            {{ condensing ? 'AI 精简中…' : 'AI 精简' }}
          </button>
          <button v-if="!editing" class="edit-btn" type="button" @click="openEdit">
            <AppIcon name="edit" :size="15" />
            编辑
          </button>
          <button class="delete-btn" type="button" @click="handleDelete">
            <AppIcon name="trash" :size="15" />
            删除
          </button>
        </div>
      </div>
      <p v-if="condenseMsg" class="ai-edit-msg">{{ condenseMsg }}</p>
      <p v-if="tagMsg" class="ai-edit-msg">{{ tagMsg }}</p>
      <!-- 视图 A：只读（原样保留，外面包一层 template） -->
      <template v-if="!editing">
        <div class="detail-header">
          <span class="category-tag">{{ knowledge.category || '未分类' }}</span>
          <span class="scores">
            <span class="score-chip ai">AI 评分 <b>{{ knowledge.aiScore || 0 }}</b></span>
            <span class="score-chip mine">我的评分 <b>{{ knowledge.starRating || 0 }}</b></span>
          </span>
        </div>
        <h2 class="title">{{ knowledge.title }}</h2>
        <div class="meta">
          <span>来源: {{ formatSource(knowledge.source) }}</span>
          <span>标签: {{ knowledge.tags || '无' }}</span>
          <span>创建: {{ formatDateTime(knowledge.createdAt) }}</span>
          <span>更新: {{ formatDateTime(knowledge.updatedAt) }}</span>
        </div>
        <!-- AI 摘要：一句话看懂这条在讲什么 -->
        <div v-if="knowledge.summary" class="summary-card">
          <span class="summary-label">AI 摘要</span>
          <p class="summary-text">{{ knowledge.summary }}</p>
        </div>

        <div class="content markdown-body" v-html="renderedContent"></div>
        <div v-if="knowledge.personalNote" class="personal-note">
          <h4>个人备注</h4>
          <p>{{ knowledge.personalNote }}</p>
        </div>
        <div class="review-box">
          <div class="review-head">
            <span class="review-label">复习</span>
            <button class="gen-btn" type="button" :disabled="generating" @click="handleGenerate">
              {{ generating ? '生成中…（AI 出题）' : '生成复习卡' }}
            </button>
          </div>
          <p v-if="reviewMsg" class="review-msg" :class="reviewMsgType">{{ reviewMsg }}</p>
          <router-link v-if="hasCards" to="/review" class="go-review">去复习 →</router-link>
        </div>
      </template>

      <!-- 视图 B：编辑表单 -->
      <template v-else>
        <h2 class="title">编辑知识</h2>
        <div class="form-group">
          <label>标题 *</label>
          <input v-model="editForm.title" type="text" placeholder="给这条知识取个标题" />
        </div>
        <div class="form-group">
          <label>内容</label>
          <textarea v-model="editForm.content" rows="10" placeholder="完整内容"></textarea>
        </div>
        <div class="form-group">
          <label>个人备注</label>
          <textarea v-model="editForm.personalNote" rows="3" placeholder="你自己的理解、感悟"></textarea>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>分类</label>
            <input v-model="editForm.category" type="text" placeholder="如：编程、英语" />
          </div>
          <div class="form-group">
            <label>标签</label>
            <input v-model="editForm.tags" type="text" placeholder="逗号分隔" />
          </div>
          <div class="form-group">
            <label>我的评分</label>
            <select v-model="editForm.starRating">
              <option :value="0">未评分</option>
              <option :value="1">1 星</option>
              <option :value="2">2 星</option>
              <option :value="3">3 星</option>
              <option :value="4">4 星</option>
              <option :value="5">5 星</option>
            </select>
          </div>
        </div>
        <div class="form-actions">
          <button class="cancel-btn" type="button" @click="cancelEdit">取消</button>
          <button class="save-btn" type="button" @click="saveEdit">保存</button>
        </div>
      </template>
    </div>
    <p v-else>未找到该知识</p>

    <CondenseDialog
        :open="condenseDialogOpen"
        :target-title="knowledge?.title || ''"
        :processing="condensing"
        @cancel="cancelCondense"
        @confirm="confirmCondense"
    />
  </div>
</template>

<style scoped>
.top-actions {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}
.back-btn {
  border: none;
  background: none;
  color: #3b6ef5;
  cursor: pointer;
  font-size: 16px;
}
.delete-btn {
  background: #e53935;
  color: white;
  border: none;
  padding: 6px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}
.delete-btn:hover {
  background: #c62828;
}
.detail {
  background: white;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}
.detail-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}
.category-tag {
  background: #e8f4fd;
  color: #3b6ef5;
  padding: 4px 12px;
  border-radius: 4px;
  font-size: 13px;
}
.star {
  font-size: 16px;
  color: #ffa726;
}
.title {
  font-size: 24px;
  margin-bottom: 16px;
}
.meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  font-size: 13px;
  color: #999;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #eee;
}
.content {
  font-size: 15px;
  line-height: 1.8;
  color: #444;
}

/* 两个评分标签：不用 emoji，浅色胶囊 */
.scores {
  display: flex;
  gap: 8px;
}
.score-chip {
  font-size: 13px;
  padding: 3px 10px;
  border-radius: 12px;
}
.score-chip b {
  font-weight: 600;
}
.score-chip.ai {
  background: #eef2ff;
  color: #3b6ef5;
}
.score-chip.mine {
  background: #fff8e6;
  color: #b78600;
}

/* AI 摘要卡片 */
.summary-card {
  background: #f6f9ff;
  border-left: 3px solid #3b6ef5;
  border-radius: 8px;
  padding: 14px 18px;
  margin: 18px 0;
}
.summary-label {
  font-size: 12px;
  font-weight: 600;
  color: #3b6ef5;
}
.summary-text {
  margin: 6px 0 0;
  font-size: 15px;
  line-height: 1.6;
  color: #333;
}

/* Markdown 正文样式：让渲染后的标题、表格、代码块有正常排版 */
.markdown-body h1,
.markdown-body h2,
.markdown-body h3 {
  margin: 22px 0 10px;
  font-weight: 600;
  color: #222;
  line-height: 1.4;
}
.markdown-body h1 { font-size: 19px; }
.markdown-body h2 { font-size: 17px; }
.markdown-body h3 { font-size: 16px; }
.markdown-body p {
  margin: 0 0 12px;
}
.markdown-body pre {
  background: #f6f8fa;
  padding: 12px 14px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}
.markdown-body code {
  background: #f1f3f5;
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 13px;
  font-family: Consolas, Monaco, monospace;
}
.markdown-body pre code {
  background: none;
  padding: 0;
}
.markdown-body table {
  border-collapse: collapse;
  margin: 12px 0;
  width: 100%;
  font-size: 14px;
}
.markdown-body th,
.markdown-body td {
  border: 1px solid #e5e7eb;
  padding: 8px 10px;
  text-align: left;
}
.markdown-body th {
  background: #f8f9fa;
  font-weight: 600;
}
.markdown-body ul,
.markdown-body ol {
  margin: 0 0 12px;
  padding-left: 22px;
}
.markdown-body li {
  margin-bottom: 4px;
}
.markdown-body a {
  color: #3b6ef5;
}
.markdown-body hr {
  border: none;
  border-top: 1px solid #eee;
  margin: 20px 0;
}
.personal-note {
  margin-top: 24px;
  padding: 16px;
  background: #fffde7;
  border-radius: 8px;
}
.personal-note h4 {
  margin-bottom: 8px;
  color: #f57f17;
}
.edit-btn {
  background: #3b6ef5;
  color: white;
  border: none;
  padding: 6px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}
.edit-btn:hover {
  background: #2f5cd8;
}
.actions-right {
  display: flex;
  gap: 8px;
}
.form-group {
  margin-bottom: 16px;
}
.form-group label {
  display: block;
  margin-bottom: 6px;
  font-size: 14px;
  color: #555;
}
.form-group input,
.form-group textarea,
.form-group select {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
  box-sizing: border-box;
}
.form-group input:focus,
.form-group textarea:focus,
.form-group select:focus {
  outline: none;
  border-color: #3b6ef5;
}
.form-row {
  display: flex;
  gap: 12px;
}
.form-row .form-group {
  flex: 1;
}
.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 20px;
}
.cancel-btn,
.save-btn {
  padding: 8px 20px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
  border: none;
}
.cancel-btn {
  background: #eee;
  color: #666;
}
.save-btn {
  background: #3b6ef5;
  color: white;
}
.save-btn:hover {
  background: #2f5cd8;
}
.review-box {
  margin-top: 24px;
  padding: 16px;
  background: #f7fbff;
  border: 1px solid #e3f0fb;
  border-radius: 8px;
}
.review-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.review-label {
  color: #3b6ef5;
  font-weight: 600;
}
.gen-btn {
  background: #3b6ef5;
  color: #fff;
  border: none;
  padding: 6px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}
.gen-btn:disabled {
  background: #90caf9;
  cursor: not-allowed;
}
.review-msg {
  margin: 12px 0 0;
  font-size: 14px;
  color: #2e7d32;
}
.review-msg.error {
  color: #c62828;
}
.go-review {
  display: inline-block;
  margin-top: 8px;
  color: #3b6ef5;
  font-size: 14px;
}
</style>

<style scoped>
.top-actions {
  max-width: 1020px;
  margin: 0 auto 18px;
  align-items: center;
}

.back-btn,
.tag-edit-btn,
.ai-edit-btn,
.edit-btn,
.delete-btn {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-ink-soft);
  cursor: pointer;
  font-size: 11px;
  font-weight: 600;
}

.tag-edit-btn {
  border-color: #c9d7f5;
  background: #fff;
  color: #3562c8;
}

.tag-edit-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: var(--color-primary-soft);
}

.tag-edit-btn:disabled {
  cursor: wait;
  opacity: 0.65;
}

.ai-edit-btn {
  border-color: #c9d7f5;
  background: var(--color-primary-soft);
  color: #3562c8;
}

.ai-edit-btn:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: #e3edff;
}

.ai-edit-btn:disabled {
  cursor: wait;
  opacity: 0.65;
}

.back-btn:hover,
.edit-btn:hover {
  border-color: #bdcaDD;
  color: var(--color-primary);
}

.delete-btn {
  border-color: #ecd0cd;
  background: #fff;
  color: var(--color-danger);
}

.detail {
  width: min(1020px, 100%);
  margin: 0 auto;
  padding: 31px 34px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.detail-header {
  align-items: center;
}

.category-tag {
  padding: 3px 8px;
  border-radius: 4px;
  background: var(--color-primary-soft);
  color: #3562c8;
  font-size: 10px;
}

.score-chip {
  padding: 3px 8px;
  border-radius: 4px;
  font-size: 10px;
}

.score-chip.ai {
  background: var(--color-primary-soft);
  color: #3562c8;
}

.score-chip.mine {
  background: var(--color-amber-soft);
  color: var(--color-amber);
}

.title {
  max-width: 780px;
  margin-bottom: 13px;
  color: var(--color-ink);
  font-size: 25px;
  font-weight: 700;
  line-height: 1.4;
}

.meta {
  gap: 12px 18px;
  padding-bottom: 17px;
  border-bottom-color: var(--color-line);
  color: var(--color-muted);
  font-size: 10px;
}

.content {
  max-width: 780px;
  color: #354151;
  font-family: "Segoe UI Variable Text", "Segoe UI", "Microsoft YaHei UI", sans-serif;
  font-size: 14px;
  font-weight: 450;
  line-height: 1.9;
}

.markdown-body h1,
.markdown-body h2,
.markdown-body h3 {
  color: var(--color-ink);
}

.markdown-body strong {
  color: #1e2c3d;
  font-weight: 700;
}

.markdown-body pre {
  border: 1px solid #e3e8ef;
  border-radius: 7px;
  background: #f7f9fc;
}

.markdown-body code {
  padding: 2px 5px;
  border: 1px solid #dde6f1;
  border-radius: 4px;
  background: #eef3fa;
  color: #284f83;
  font-family: "Cascadia Mono", "JetBrains Mono", Consolas, monospace;
  font-size: 13px;
  font-weight: 600;
}

.markdown-body pre code {
  border: 0;
  background: transparent;
  color: inherit;
}

.summary-card {
  max-width: 780px;
  padding: 15px 17px;
  border: 0;
  border-left: 3px solid #7f9fe4;
  border-radius: 0 6px 6px 0;
  background: #f6f8fd;
}

.summary-label {
  color: var(--color-primary);
}

.personal-note {
  max-width: 780px;
  border: 1px solid #eee4d5;
  border-radius: 7px;
  background: #fffbf4;
}

.personal-note h4 {
  color: var(--color-amber);
}

.review-box {
  max-width: 780px;
  margin-top: 27px;
  border: 1px solid #dfe5ee;
  border-radius: 7px;
  background: #f8faff;
}

.review-label,
.go-review {
  color: var(--color-primary);
}

.gen-btn,
.save-btn {
  border-radius: var(--radius-md);
  background: var(--color-primary);
}

.gen-btn:hover:not(:disabled),
.save-btn:hover {
  background: var(--color-primary-strong);
}

.cancel-btn {
  border-radius: var(--radius-md);
}

.form-group input,
.form-group textarea,
.form-group select {
  min-height: 40px;
  border-color: var(--color-line-strong);
  border-radius: var(--radius-md);
}

.form-group textarea {
  padding-top: 10px;
  line-height: 1.6;
}

.form-group input:focus,
.form-group textarea:focus,
.form-group select:focus {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(47, 105, 242, 0.1);
}

.actions-right {
  gap: 7px;
}

.ai-edit-msg {
  max-width: 1020px;
  margin: -7px auto 16px;
  padding: 10px 13px;
  border: 1px solid #d4e0f7;
  border-radius: 6px;
  background: #f5f8ff;
  color: #45649f;
  font-size: 11px;
}

@media (max-width: 760px) {
  .top-actions {
    flex-wrap: wrap;
    gap: 9px;
  }

  .detail {
    padding: 22px 17px;
  }

  .detail-header {
    align-items: flex-start;
    flex-direction: column;
    gap: 10px;
  }

  .title {
    font-size: 21px;
  }

  .form-row {
    flex-direction: column;
    gap: 0;
  }

  .actions-right {
    flex-wrap: wrap;
    margin-left: auto;
  }
}
</style>
