<script setup>
import { ref, onMounted, computed } from 'vue'
import { getAllKnowledge, addKnowledge, importKnowledge } from '../api/knowledge'
import { getStats } from '../api/review'
import { describeError } from '../utils/error'
import KnowledgeCard from '../components/KnowledgeCard.vue'
import ReviewBanner from '../components/ReviewBanner.vue'
import AppIcon from '../components/AppIcon.vue'

const loadError = ref('')
const list = ref([])
const loading = ref(true)
const stats = ref(null)
const searchQuery = ref('')
const activeFilter = ref('all')

const showForm = ref(false)
const fileInput = ref(null)
const importing = ref(false)
const saving = ref(false)
const form = ref(emptyForm())

function emptyForm() {
  return {
    title: '',
    content: '',
    source: 'MANUAL',
    category: '',
    tags: '',
    starRating: 0,
    personalNote: ''
  }
}

const segments = computed(() => {
  if (!stats.value) return []
  const source = stats.value
  return [
    { key: 'initial', label: '初记 1-2 阶', value: source.initial, cls: 'initial' },
    { key: 'strengthen', label: '强化 3-4 阶', value: source.strengthen, cls: 'strengthen' },
    { key: 'consolidate', label: '巩固 5-6 阶', value: source.consolidate, cls: 'consolidate' },
    { key: 'mastered', label: '熟识 7 阶', value: source.mastered, cls: 'mastered' }
  ]
})

const filteredList = computed(() => {
  const keyword = searchQuery.value.trim().toLowerCase()
  return list.value.filter((item) => {
    if (activeFilter.value === 'high' && Number(item.aiScore || 0) < 4) return false
    if (!keyword) return true
    return [item.title, item.content, item.category, item.tags, item.personalNote]
        .filter(Boolean)
        .some((value) => String(value).toLowerCase().includes(keyword))
  })
})

const pct = (value) => {
  const total = stats.value?.total || 0
  if (total === 0) return '0%'
  return Math.round((value / total) * 100) + '%'
}

const loadStats = async () => {
  try {
    stats.value = await getStats()
  } catch (error) {
    console.warn('掌握现状加载失败', error)
  }
}

const loadList = async () => {
  loading.value = true
  loadError.value = ''
  try {
    list.value = (await getAllKnowledge()) || []
  } catch (error) {
    console.error('获取列表失败:', error)
    loadError.value = describeError(error, '加载知识列表失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadStats()
  loadList()
})

const handleAdd = async () => {
  if (saving.value) return
  saving.value = true
  try {
    await addKnowledge(form.value)
    await Promise.all([loadList(), loadStats()])
    form.value = emptyForm()
    showForm.value = false
  } catch (error) {
    console.error('新增失败:', error)
    alert(`新增失败：${describeError(error, '请检查输入后重试')}`)
  } finally {
    saving.value = false
  }
}

const handleImport = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  importing.value = true
  try {
    const result = await importKnowledge(file)
    await Promise.all([loadList(), loadStats()])
    alert(
        `导入完成：共 ${result.total} 条，` +
        `成功评分 ${result.scoredCount} 条，` +
        `入库 ${result.importedCount} 条`
    )
  } catch (error) {
    console.error('导入失败:', error)
    alert(`导入失败：${describeError(error, '文件格式不正确')}`)
  } finally {
    importing.value = false
    event.target.value = ''
  }
}
</script>

<template>
  <div class="library-page">
    <header class="page-header">
      <div class="page-heading">
        <div class="page-kicker">Library</div>
        <h1 class="page-title">知识库</h1>
        <p class="page-subtitle">
          {{ list.length }} 条知识
          <template v-if="stats"> · {{ stats.total }} 张复习卡</template>
        </p>
      </div>
      <div class="page-actions">
        <label class="search-box">
          <AppIcon name="search" :size="16" />
          <input v-model="searchQuery" type="search" placeholder="搜索标题、标签或内容" />
        </label>
        <button class="btn btn-ghost" type="button" :disabled="importing" @click="fileInput.click()">
          <AppIcon name="file" :size="16" />
          {{ importing ? '导入中' : '导入' }}
        </button>
        <button class="btn btn-primary" type="button" @click="showForm = true">
          <AppIcon name="plus" :size="16" />
          新增知识
        </button>
        <input
            ref="fileInput"
            class="hidden-input"
            type="file"
            accept=".md,.jsonl,.docx"
            @change="handleImport"
        />
      </div>
    </header>

    <ReviewBanner />

    <section v-if="stats" class="stat-strip" aria-label="知识概览">
      <div class="stat">
        <span class="stat-label">知识总量</span>
        <strong>{{ list.length }} <small>条</small></strong>
      </div>
      <div class="stat">
        <span class="stat-label">复习卡片</span>
        <strong>{{ stats.total }} <small>张</small></strong>
      </div>
      <div class="stat">
        <span class="stat-label">正在强化</span>
        <strong>{{ stats.strengthen + stats.consolidate }} <small>张</small></strong>
      </div>
      <div class="stat">
        <span class="stat-label">已经熟识</span>
        <strong>{{ stats.mastered }} <small>张</small></strong>
      </div>
    </section>

    <div class="library-layout">
      <section class="knowledge-panel panel">
        <div class="section-head">
          <div>
            <h2>最近知识</h2>
            <p>{{ filteredList.length }} 条结果</p>
          </div>
          <div class="filter-tabs" role="tablist" aria-label="知识筛选">
            <button
                type="button"
                :class="{ active: activeFilter === 'all' }"
                @click="activeFilter = 'all'"
            >全部</button>
            <button
                type="button"
                :class="{ active: activeFilter === 'high' }"
                @click="activeFilter = 'high'"
            >高价值</button>
          </div>
        </div>

        <p v-if="loading" class="loading-state">正在加载知识…</p>
        <p v-else-if="loadError" class="error-state">{{ loadError }}</p>
        <div v-else-if="filteredList.length === 0" class="empty-state">
          {{ searchQuery ? '没有匹配的知识' : '还没有知识，先记录第一条吧。' }}
        </div>
        <div v-else class="knowledge-list">
          <KnowledgeCard
              v-for="item in filteredList"
              :key="item.id"
              :knowledge="item"
          />
        </div>
      </section>

      <aside v-if="stats" class="insights">
        <section class="insight-section">
          <div class="insight-head">
            <h2>掌握现状</h2>
            <span>{{ stats.total }} 张卡片</span>
          </div>
          <div class="mastery-total">
            <strong>{{ pct(stats.consolidate + stats.mastered) }}</strong>
            <span>进入巩固阶段</span>
          </div>
          <div class="segment-list">
            <div v-for="segment in segments" :key="segment.key" class="segment-row">
              <span>{{ segment.label }}</span>
              <div class="segment-track">
                <div class="segment-fill" :class="segment.cls" :style="{ width: pct(segment.value) }"></div>
              </div>
              <b>{{ segment.value }}</b>
            </div>
          </div>
        </section>

        <section class="insight-section insight-note">
          <span class="note-label">整理建议</span>
          <p>优先处理高 AI 评分但还没有复习卡的知识，让“保存”真正变成“记住”。</p>
        </section>
      </aside>
    </div>

    <div
        v-if="showForm"
        class="modal-overlay"
        role="dialog"
        aria-modal="true"
        aria-labelledby="add-knowledge-title"
        tabindex="-1"
        @click.self="showForm = false"
        @keydown.esc="showForm = false"
    >
      <form class="modal" @submit.prevent="handleAdd">
        <div class="modal-head">
          <div>
            <div class="page-kicker">New note</div>
            <h2 id="add-knowledge-title">新增知识</h2>
          </div>
          <button class="close-btn" type="button" aria-label="关闭" @click="showForm = false">×</button>
        </div>

        <label class="form-group">
          <span class="field-label">标题 *</span>
          <input v-model="form.title" class="text-input" type="text" placeholder="给这条知识取个标题" />
        </label>
        <label class="form-group">
          <span class="field-label">内容</span>
          <textarea v-model="form.content" class="text-area" rows="10" placeholder="支持 Markdown"></textarea>
        </label>
        <label class="form-group">
          <span class="field-label">个人备注</span>
          <textarea v-model="form.personalNote" class="text-area" rows="3" placeholder="你的理解、感悟或踩坑记录"></textarea>
        </label>
        <div class="form-row">
          <label class="form-group">
            <span class="field-label">分类</span>
            <input v-model="form.category" class="text-input" type="text" placeholder="如：编程" />
          </label>
          <label class="form-group">
            <span class="field-label">标签</span>
            <input v-model="form.tags" class="text-input" type="text" placeholder="逗号分隔" />
          </label>
          <label class="form-group">
            <span class="field-label">我的评分</span>
            <select v-model="form.starRating" class="select-input">
              <option :value="0">未评分</option>
              <option v-for="score in 5" :key="score" :value="score">{{ score }} 星</option>
            </select>
          </label>
        </div>

        <div class="modal-actions">
          <button class="btn btn-ghost" type="button" @click="showForm = false">取消</button>
          <button class="btn btn-primary" type="submit" :disabled="saving">
            {{ saving ? '保存中…' : '保存知识' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.library-page {
  max-width: 1420px;
  margin: 0 auto;
}

.search-box {
  width: min(320px, 28vw);
  height: 38px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  color: var(--color-muted);
}

.search-box:focus-within {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px rgba(47, 105, 242, 0.09);
}

.search-box input {
  width: 100%;
  border: 0;
  outline: 0;
  background: transparent;
  color: var(--color-ink);
  font-size: 11px;
}

.search-box input::placeholder {
  color: #9ca6b4;
}

.hidden-input {
  display: none;
}

.stat-strip {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 22px 0 24px;
  border-top: 1px solid var(--color-line);
  border-bottom: 1px solid var(--color-line);
}

.stat {
  min-height: 76px;
  display: grid;
  align-content: center;
  gap: 5px;
  padding: 0 22px;
  border-right: 1px solid var(--color-line);
}

.stat:last-child {
  border-right: 0;
}

.stat-label {
  color: var(--color-muted);
  font-size: 10px;
}

.stat strong {
  font-size: 20px;
  font-weight: 680;
}

.stat small {
  color: var(--color-muted);
  font-size: 9px;
  font-weight: 400;
}

.library-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 22px;
  align-items: start;
}

.knowledge-panel {
  min-width: 0;
  padding: 17px 20px 4px;
}

.section-head {
  min-height: 46px;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  border-bottom: 1px solid var(--color-line);
}

.section-head h2,
.insight-head h2 {
  font-size: 13px;
  font-weight: 680;
}

.section-head p {
  margin-top: 4px;
  color: var(--color-muted);
  font-size: 9px;
}

.filter-tabs {
  display: flex;
  gap: 3px;
  padding: 3px;
  border-radius: 6px;
  background: #edf0f4;
}

.filter-tabs button {
  min-height: 27px;
  padding: 0 10px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #778396;
  cursor: pointer;
  font-size: 9px;
}

.filter-tabs button.active {
  background: #fff;
  color: var(--color-ink);
  box-shadow: var(--shadow-sm);
}

.knowledge-list {
  min-width: 0;
}

.insights {
  display: grid;
  gap: 14px;
}

.insight-section {
  padding: 17px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}

.insight-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 15px;
}

.insight-head span {
  color: var(--color-muted);
  font-size: 9px;
}

.mastery-total {
  display: flex;
  align-items: baseline;
  gap: 7px;
  margin-bottom: 16px;
}

.mastery-total strong {
  font-size: 29px;
  font-weight: 680;
}

.mastery-total span {
  color: var(--color-muted);
  font-size: 9px;
}

.segment-list {
  display: grid;
  gap: 10px;
}

.segment-row {
  display: grid;
  grid-template-columns: 74px minmax(70px, 1fr) 20px;
  align-items: center;
  gap: 8px;
  color: #788598;
  font-size: 9px;
}

.segment-track {
  height: 6px;
  overflow: hidden;
  border-radius: 3px;
  background: #edf0f4;
}

.segment-fill {
  height: 100%;
  border-radius: 3px;
  background: #9cb7f3;
}

.segment-fill.strengthen {
  background: #7398e9;
}

.segment-fill.consolidate {
  background: #4d79dd;
}

.segment-fill.mastered {
  background: #2758bf;
}

.segment-row b {
  color: var(--color-ink-soft);
  font-weight: 600;
  text-align: right;
}

.insight-note {
  border-left: 3px solid #9bb6f0;
  background: #f8faff;
}

.note-label {
  color: var(--color-primary);
  font-size: 9px;
  font-weight: 700;
}

.insight-note p {
  margin-top: 7px;
  color: #69778b;
  font-size: 10px;
  line-height: 1.7;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: rgba(18, 28, 43, 0.5);
  backdrop-filter: blur(3px);
}

.modal {
  width: min(720px, 100%);
  max-height: 88vh;
  overflow-y: auto;
  padding: 24px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: 0 24px 70px rgba(18, 28, 43, 0.2);
}

.modal-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 20px;
}

.modal-head h2 {
  font-size: 20px;
  font-weight: 700;
}

.close-btn {
  width: 30px;
  height: 30px;
  border: 0;
  border-radius: 6px;
  background: #f1f3f6;
  color: #7e8998;
  cursor: pointer;
  font-size: 20px;
  line-height: 1;
}

.form-group {
  display: block;
  margin-bottom: 15px;
}

.form-row {
  display: grid;
  grid-template-columns: 1.2fr 1.5fr 0.8fr;
  gap: 12px;
}

.modal-actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  margin-top: 8px;
}

@media (max-width: 1100px) {
  .library-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .insights {
    grid-template-columns: 1fr 1fr;
  }
}

@media (max-width: 760px) {
  .search-box {
    width: 100%;
  }

  .page-actions {
    flex-wrap: wrap;
  }

  .search-box {
    flex-basis: 100%;
  }

  .stat-strip {
    grid-template-columns: 1fr 1fr;
  }

  .stat:nth-child(2) {
    border-right: 0;
  }

  .stat:nth-child(-n + 2) {
    border-bottom: 1px solid var(--color-line);
  }

  .knowledge-panel {
    padding: 15px 14px 4px;
  }

  .insights {
    grid-template-columns: 1fr;
  }

  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
