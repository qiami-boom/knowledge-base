<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledgeById, deleteKnowledge } from '../api/knowledge'

const route = useRoute()
const router = useRouter()

const knowledge = ref(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const id = route.params.id
    const res = await getKnowledgeById(id)
    knowledge.value = res.data
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
        <button class="delete-btn" @click="handleDelete">删除</button>
      </div>
      <div class="detail-header">
        <span class="category-tag">{{ knowledge.category || '未分类' }}</span>
        <span class="star">⭐ {{ knowledge.starRating || 0 }}</span>
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
</style>