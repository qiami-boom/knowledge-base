<script setup>
import { ref, onMounted } from 'vue'
import { getAllKnowledge, addKnowledge } from '../api/knowledge'
import KnowledgeCard from '../components/KnowledgeCard.vue'

const list = ref([])
const loading = ref(true)

// 新增表单相关
const showForm = ref(false)   // 控制表单弹窗显示/隐藏
const form = ref({             // 表单数据
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
  try {
    const res = await getAllKnowledge()
    list.value = res.data
  } catch (error) {
    console.error('获取列表失败:', error)
  } finally {
    loading.value = false
  }
})

const handleAdd = async () => {
  try {
    await addKnowledge(form.value)
    // 重新加载列表
    const res = await getAllKnowledge()
    list.value = res.data
    // 清空表单，关闭弹窗
    form.value = { title: '', content: '', source: 'MANUAL', category: '', tags: '', starRating: 0, aiScore: 0, personalNote: '' }
    showForm.value = false
  } catch (error) {
    console.error('新增失败:', error)
    alert('新增失败，请检查输入')
  }
}
</script>

<template>
  <div>
    <!-- 新增按钮 -->
    <div class="toolbar">
      <button class="add-btn" @click="showForm = true">+ 新增知识</button>
    </div>

    <!-- 列表 -->
    <p v-if="loading">加载中...</p>
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
      <div class="modal">
        <h2>新增知识</h2>
        <div class="form-group">
          <label>标题 *</label>
          <input v-model="form.title" type="text" placeholder="给这条知识取个标题" />
        </div>
        <div class="form-group">
          <label>内容</label>
          <textarea v-model="form.content" placeholder="详细内容，可以是 Markdown" rows="5"></textarea>
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
  background: #1976d2;
  color: white;
  border: none;
  padding: 10px 20px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}
.add-btn:hover {
  background: #1565c0;
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
.form-group textarea {
  width: 100%;
  padding: 8px 12px;
  border: 1px solid #ddd;
  border-radius: 4px;
  font-size: 14px;
  font-family: inherit;
}
.form-group input:focus,
.form-group textarea:focus {
  outline: none;
  border-color: #1976d2;
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
  background: #1976d2;
  color: white;
}
.save-btn:hover {
  background: #1565c0;
}
</style>