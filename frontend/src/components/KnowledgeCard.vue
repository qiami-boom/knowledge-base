<script setup>
import { computed } from 'vue'

const props = defineProps({
  knowledge: {
    type: Object,
    required: true
  }
})

const scoreClass = computed(() => {
  const score = Number(props.knowledge.aiScore || 0)
  if (score >= 4.5) return 'high'
  if (score >= 3.5) return 'mid'
  return 'low'
})

const formatDate = (dateStr) => {
  if (!dateStr) return ''
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr.substring(0, 10)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit'
  }).format(date)
}

const excerpt = computed(() => {
  const text = props.knowledge.content || props.knowledge.personalNote || '还没有正文内容'
  return text.length > 92 ? `${text.substring(0, 92)}…` : text
})
</script>

<template>
  <router-link
      class="knowledge-row"
      :to="`/detail/${knowledge.id}`"
      :aria-label="`打开知识：${knowledge.title}`"
  >
    <div class="knowledge-main">
      <div class="knowledge-top">
        <span class="category-chip">{{ knowledge.category || '未分类' }}</span>
        <span v-if="knowledge.status === 'DRAFT'" class="draft-chip">草稿</span>
        <time class="knowledge-date">{{ formatDate(knowledge.createdAt) }}</time>
      </div>
      <h3>{{ knowledge.title }}</h3>
      <p>{{ excerpt }}</p>
      <div v-if="knowledge.tags" class="knowledge-tags">
        <span v-for="tag in knowledge.tags.split(',').slice(0, 4)" :key="tag">
          #{{ tag.trim() }}
        </span>
      </div>
    </div>
    <div class="knowledge-side">
      <span class="score" :class="scoreClass">
        <i></i>
        AI {{ knowledge.aiScore || 0 }}
      </span>
      <span class="open-hint">查看详情</span>
    </div>
  </router-link>
</template>

<style scoped>
.knowledge-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 96px;
  gap: 20px;
  align-items: center;
  min-height: 96px;
  padding: 16px 8px;
  border-bottom: 1px solid var(--color-line);
  color: inherit;
  text-decoration: none;
  transition: background 0.16s ease, padding 0.16s ease;
}

.knowledge-row:hover {
  padding-right: 10px;
  padding-left: 12px;
  background: #fbfcfe;
}

.knowledge-top {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 7px;
}

.category-chip,
.draft-chip {
  padding: 2px 7px;
  border-radius: 4px;
  background: var(--color-primary-soft);
  color: #3562c8;
  font-size: 9px;
  font-weight: 650;
}

.draft-chip {
  background: var(--color-amber-soft);
  color: var(--color-amber);
}

.knowledge-date {
  color: var(--color-muted);
  font-size: 9px;
}

h3 {
  overflow: hidden;
  color: var(--color-ink);
  font-size: 14px;
  font-weight: 650;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

p {
  margin-top: 6px;
  overflow: hidden;
  color: #748093;
  font-size: 10px;
  line-height: 1.55;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.knowledge-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.knowledge-tags span {
  color: #8b95a4;
  font-size: 9px;
}

.knowledge-side {
  display: grid;
  justify-items: end;
  gap: 14px;
}

.score {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #647185;
  font-size: 10px;
}

.score i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #7d91bd;
}

.score.high i {
  background: var(--color-primary);
}

.score.mid i {
  background: #6a9b7c;
}

.score.low i {
  background: #c69a59;
}

.open-hint {
  color: #a1aab7;
  font-size: 9px;
}

@media (max-width: 720px) {
  .knowledge-row {
    grid-template-columns: minmax(0, 1fr) 64px;
    gap: 10px;
    min-height: 88px;
    padding: 14px 4px;
  }

  .knowledge-row:hover {
    padding: 14px 4px;
  }

  p {
    max-width: 70vw;
  }

  .open-hint {
    display: none;
  }
}
</style>
