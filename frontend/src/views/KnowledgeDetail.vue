<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledgeById, updateKnowledge ,deleteKnowledge } from '../api/knowledge'

const route = useRoute()
const router = useRouter()

const knowledge = ref(null)
const loading = ref(true)
// 是否处于编辑模式：false = 只读查看，true = 表单编辑
const editing = ref(false)
// 编辑草稿：独立于 knowledge，取消时整份丢弃，不污染原数据
const editForm = ref({})

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
</script>

<template>
  <div>
    <p v-if="loading">加载中...</p>
    <div v-else-if="knowledge" class="detail">
      <div class="top-actions">
        <button class="back-btn" @click="goBack">← 返回列表</button>
        <div class="actions-right">
          <button v-if="!editing" class="edit-btn" @click="openEdit">编辑</button>
          <button class="delete-btn" @click="handleDelete">删除</button>
        </div>
      </div>
      <!-- 视图 A：只读（原样保留，外面包一层 template） -->
      <template v-if="!editing">
        <div class="detail-header">
          <span class="category-tag">{{ knowledge.category || '未分类' }}</span>
          <span class="star">🤖 AI评分: {{ knowledge.aiScore || 0 }} | ⭐ 我的评分: {{ knowledge.starRating || 0 }}</span>
        </div>
        <h2 class="title">{{ knowledge.title }}</h2>
        <div class="meta">
          <span>来源: {{ knowledge.source || '手动' }}</span>
          <span>标签: {{ knowledge.tags || '无' }}</span>
          <span>创建: {{ knowledge.createdAt }}</span>
          <span>更新: {{ knowledge.updatedAt }}</span>
        </div>
        <div class="content">{{ knowledge.content }}</div>
        <div v-if="knowledge.personalNote" class="personal-note">
          <h4>个人备注</h4>
          <p>{{ knowledge.personalNote }}</p>
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
          <button class="cancel-btn" @click="cancelEdit">取消</button>
          <button class="save-btn" @click="saveEdit">保存</button>
        </div>
      </template>
    </div>
    <p v-else>未找到该知识</p>
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
  color: #1976d2;
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
  color: #1976d2;
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
  white-space: pre-wrap;
  color: #444;
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
  background: #1976d2;
  color: white;
  border: none;
  padding: 6px 16px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 14px;
}
.edit-btn:hover {
  background: #1565c0;
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