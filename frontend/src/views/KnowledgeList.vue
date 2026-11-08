<script setup>
import { ref, onMounted, computed } from 'vue'
import { getAllKnowledge, addKnowledge ,importKnowledge} from '../api/knowledge'
import { getStats } from '../api/review'
import KnowledgeCard from '../components/KnowledgeCard.vue'
import { describeError } from '../utils/error'
import ReviewBanner from '../components/ReviewBanner.vue'

const loadError = ref('')      // 非空 = 列表加载失败
const list = ref([])
const loading = ref(true)

// 掌握现状：按复习阶段统计的卡片分布
const stats = ref(null)

const segments = computed(() => {
  if (!stats.value) return []
  const s = stats.value
  return [
    { key: 'initial',     label: '初记 1-2 阶', value: s.initial,     cls: 'fill-initial' },
    { key: 'strengthen',  label: '强化 3-4 阶', value: s.strengthen,  cls: 'fill-strengthen' },
    { key: 'consolidate', label: '巩固 5-6 阶', value: s.consolidate, cls: 'fill-consolidate' },
    { key: 'mastered',    label: '熟识 7 阶',   value: s.mastered,    cls: 'fill-mastered' }
  ]
})

const pct = (value) => {
  const total = stats.value?.total || 0
  if (total === 0) return '0%'
  return Math.round((value / total) * 100) + '%'
}

// 独立加载：统计失败不能影响知识列表
const loadStats = async () => {
  try {
    stats.value = await getStats()
  } catch (e) {
    console.warn('掌握现状加载失败', e)
  }
}

// 新增表单相关
const showForm = ref(false)
const fileInput = ref(null)
const importing = ref(null)
const form = ref({
  title: '',
  content: '',
  source: 'MANUAL',
  category: '',
  tags: '',
  starRating: 0,
  aiScore: 0,
  personalNote: ''
})

onMounted(async () => {
  loadStats()
  try {
    const data = await getAllKnowledge()
    list.value = data
  } catch (error) {
    console.error('获取列表失败:', error)
    loadError.value = describeError(error, '加载知识列表失败')
  } finally {
    loading.value = false
  }
})

const handleAdd = async () => {
  try {
    await addKnowledge(form.value)
    const data = await getAllKnowledge()
    list.value = data
    form.value = { title: '', content: '', source: 'MANUAL', category: '', tags: '', starRating: 0, aiScore: 0, personalNote: '' }
    showForm.value = false
  } catch (error) {
    console.error('新增失败:', error)
    alert(`新增失败：${describeError(error, '请检查输入后重试')}`)
  }
}

const handleImport = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  importing.value = true
  try {
    const result = await importKnowledge(file)
    const data = await getAllKnowledge()
    list.value = data
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
  <div>
    <!-- 今日复习：首页第一优先动作 -->
    <ReviewBanner />

    <!-- 掌握现状：按复习阶段统计的卡片分布 -->
    <div v-if="segments.length" class="status-card">
      <div class="status-head">
        <span>掌握现状</span>
        <span class="status-total">{{ stats.total }} 张复习卡</span>
      </div>
      <div class="status-row" v-for="seg in segments" :key="seg.key">
        <span class="seg-label">{{ seg.label }}</span>
        <div class="seg-track">
          <div class="seg-fill" :class="seg.cls" :style="{ width: pct(seg.value) }"></div>
        </div>
        <span class="seg-value">{{ seg.value }}</span>
      </div>
    </div>

    <!-- 工具栏：新增为主，导入为辅 -->
    <div class="toolbar">
      <button class="add-btn" @click="showForm = true">+ 新增知识</button>
      <button
          class="import-btn"
          @click="fileInput.click()"
          :disabled="importing"
      >
        {{ importing ? '导入中...' : '导入记忆' }}
      </button>
      <input
          ref="fileInput"
          type="file"
          accept=".md,.jsonl,.docx"
          style="display:none"
          @change="handleImport"
      />
    </div>

    <!-- 列表 -->
    <p v-if="loading">加载中...</p>
    <p v-if="loading">加载中...</p>
    <p v-else-if="loadError" class="error-tip">{{ loadError }}</p>
    <p v-else-if="list.length === 0">暂无知识，快去添加吧！</p>
    <div v-else class="card-list">
      <KnowledgeCard
          v-for="item in list"
          :key="item.id"
          :knowledge="item"
      />
    </div>

    <!-- 新增表单弹窗 -->
    <div v-if="showForm" class="modal-overlay" @click.self="showForm = false">
      <div class="modal modal-large">
        <h2>新增知识</h2>
        <div class="form-group">
          <label>标题 *</label>
          <input v-model="form.title" type="text" placeholder="给这条知识取个标题" />
        </div>
        <div class="form-group">
          <label>内容（支持 Markdown）</label>
          <textarea v-model="form.content" placeholder="完整内容，比如 AI 的回答原文" rows="12"></textarea>
        </div>
        <div class="form-group">
          <label>个人备注</label>
          <textarea v-model="form.personalNote" placeholder="你自己的理解、感悟、踩坑记录等" rows="3"></textarea>
        </div>
        <div class="form-row">
          <div class="form-group">
            <label>分类</label>
            <input v-model="form.category" type="text" placeholder="如：编程、英语" />
          </div>
          <div class="form-group">
            <label>标签</label>
            <input v-model="form.tags" type="text" placeholder="逗号分隔，如：Spring,面试" />
          </div>
          <div class="form-group">
            <label>我的评分</label>
            <select v-model="form.starRating">
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
          <button class="cancel-btn" @click="showForm = false">取消</button>
          <button class="save-btn" @click="handleAdd">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.toolbar {
  margin-bottom: 16px;
  text-align: right;
}
.add-btn {
  background: #3b6ef5;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}
.add-btn:hover {
  background: #2f5cd8;
}
.card-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
@media (max-width: 640px) {
  .card-list {
    grid-template-columns: 1fr;
  }
}
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 100;
}
.modal {
  background: white;
  border-radius: 8px;
  padding: 24px;
  width: 480px;
  max-height: 80vh;
  overflow-y: auto;
}
.modal-large {
  width: 640px;
}
.modal h2 {
  margin-bottom: 20px;
  font-size: 20px;
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

.import-btn {
  background: #fff;
  color: #888;
  border: 1px solid #ddd;
  padding: 10px 16px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  margin-left: 8px;
}
.import-btn:hover {
  background: #f5f5f5;
  color: #555;
}
.import-btn:disabled {
  background: #eee;
  cursor: not-allowed;
}
.error-tip { color: #c62828; }

/* 掌握现状卡片 */
.status-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  padding: 16px 20px;
  margin-bottom: 20px;
}
.status-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 15px;
  font-weight: 600;
  color: #222;
  margin-bottom: 12px;
}
.status-total {
  font-size: 12px;
  font-weight: 400;
  color: #888;
}
.status-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.seg-label {
  width: 92px;
  flex-shrink: 0;
  font-size: 13px;
  color: #666;
}
.seg-track {
  flex: 1;
  height: 10px;
  background: #f1f3f5;
  border-radius: 5px;
  overflow: hidden;
}
.seg-fill {
  height: 100%;
  border-radius: 5px;
  transition: width 0.3s;
}
.fill-initial { background: #a5b4fc; }
.fill-strengthen { background: #748ffc; }
.fill-consolidate { background: #4c6ef5; }
.fill-mastered { background: #3b5bdb; }
.seg-value {
  width: 32px;
  flex-shrink: 0;
  text-align: right;
  font-size: 13px;
  color: #333;
}
</style>