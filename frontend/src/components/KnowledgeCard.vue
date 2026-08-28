<template>
  <div class="card" @click="handleClick">
    <div class="card-header">
      <span class="category-tag">{{ knowledge.category || '未分类' }}</span>
      <span class="star">⭐ {{ knowledge.starRating || 0 }}</span>
    </div>
    <h3 class="title">{{ knowledge.title }}</h3>
    <p class="content-preview">{{ knowledge.content?.substring(0, 100) }}...</p>
    <div class="card-footer">
      <span class="tags">{{ knowledge.tags }}</span>
      <span class="date">{{ formatDate(knowledge.createdAt) }}</span>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'

// props：父组件传进来的数据
const props = defineProps({
  knowledge: {
    type: Object,
    required: true
  }
})

const router = useRouter()

// 点击卡片跳转到详情页
const handleClick = () => {
  router.push(`/detail/${props.knowledge.id}`)
}

// 格式化日期
const formatDate = (dateStr) => {
  if (!dateStr) return ''
  return dateStr.substring(0, 10)
}
</script>

<style scoped>
.card {
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  padding: 16px;
  cursor: pointer;
  transition: box-shadow 0.2s;
  background: #fff;
}
.card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
}
.card-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}
.category-tag {
  background: #e8f4fd;
  color: #1976d2;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}
.star {
  font-size: 14px;
  color: #ffa726;
}
.title {
  margin: 8px 0;
  font-size: 18px;
}
.content-preview {
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  margin: 0 0 12px 0;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}
</style>