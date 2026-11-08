<template>
  <div class="card" @click="handleClick">
    <div class="card-header">
      <span class="category-tag">{{ knowledge.category || '未分类' }}</span>
      <span class="score-chip">AI 评分 <b>{{ knowledge.aiScore || 0 }}</b></span>
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
  border: 1px solid transparent;
  border-radius: 12px;
  padding: 18px 20px;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
  background: #fff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}
.card:hover {
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.09);
  transform: translateY(-1px);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.category-tag {
  background: #eef2ff;
  color: #3b6ef5;
  padding: 2px 8px;
  border-radius: 6px;
  font-size: 12px;
}
.score-chip {
  font-size: 12px;
  padding: 2px 9px;
  border-radius: 10px;
  background: #eef2ff;
  color: #3b6ef5;
}
.score-chip b {
  font-weight: 600;
}
.title {
  margin: 8px 0;
  font-size: 17px;
  font-weight: 600;
  color: #222;
}
.content-preview {
  color: #666;
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 12px 0;
}
.card-footer {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #999;
}
</style>