<script setup>
import { ref, onMounted } from 'vue'
import { getDueCards } from '../api/review'
import { describeError } from '../utils/error'
import AppIcon from './AppIcon.vue'

const count = ref(0)
const loading = ref(true)
const errorMsg = ref('')

const load = async () => {
  loading.value = true
  errorMsg.value = ''
  try {
    const cards = await getDueCards()
    count.value = cards.length
  } catch (error) {
    console.error('获取待复习数量失败:', error)
    errorMsg.value = describeError(error, '复习数据加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <section
      class="review-banner"
      :class="{ done: !loading && !errorMsg && count === 0 }"
  >
    <div class="review-copy">
      <p class="eyebrow">TODAY · REVIEW</p>
      <h2>{{ count > 0 ? '把今天该记住的留下' : '今天的复习已经完成' }}</h2>
      <p v-if="loading" class="sub">正在统计待复习卡片…</p>
      <p v-else-if="errorMsg" class="sub error">
        {{ errorMsg }}
        <button class="retry" type="button" @click="load">重试</button>
      </p>
      <p v-else-if="count > 0" class="sub">先完成到期卡片，再继续整理新的知识。</p>
      <p v-else class="sub">可以继续整理新知识，或者再回看一轮。</p>
    </div>

    <div class="review-progress" aria-hidden="true">
      <span v-for="index in 7" :key="index" :class="{ active: index <= 3, current: index === 4 }"></span>
    </div>

    <div class="review-action">
      <div v-if="count > 0" class="review-count">
        <strong>{{ count }}</strong>
        <span>等待复习</span>
      </div>
      <router-link
          v-if="!loading && !errorMsg"
          to="/review"
          class="review-link"
      >
        {{ count > 0 ? '开始复习' : '再看看' }}
        <AppIcon name="arrow-left" :size="15" />
      </router-link>
    </div>
  </section>
</template>

<style scoped>
.review-banner {
  min-height: 126px;
  display: flex;
  align-items: center;
  gap: 25px;
  padding: 23px 25px;
  border: 1px solid #294775;
  border-radius: var(--radius-md);
  background: #1d355f;
  color: #f6f9ff;
  box-shadow: 0 8px 20px rgba(29, 53, 95, 0.14);
}

.review-banner.done {
  border-color: var(--color-line);
  background: var(--color-surface);
  color: var(--color-ink);
  box-shadow: none;
}

.review-copy {
  width: 330px;
  flex-shrink: 0;
}

.eyebrow {
  margin-bottom: 8px;
  color: #8faee6;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.13em;
}

.review-banner.done .eyebrow {
  color: var(--color-primary);
}

h2 {
  font-size: 20px;
  font-weight: 680;
  line-height: 1.35;
}

.sub {
  margin-top: 7px;
  color: #b6c6df;
  font-size: 11px;
}

.review-banner.done .sub {
  color: var(--color-muted);
}

.sub.error {
  color: #ffd6d3;
}

.retry {
  margin-left: 8px;
  border: 0;
  border-radius: 4px;
  padding: 3px 8px;
  background: rgba(255, 255, 255, 0.13);
  color: inherit;
  cursor: pointer;
  font-size: 9px;
}

.review-progress {
  flex: 1;
  display: flex;
  gap: 7px;
}

.review-progress span {
  flex: 1;
  height: 7px;
  border-radius: 4px;
  background: rgba(255, 255, 255, 0.12);
}

.review-progress span.active {
  background: #7098e8;
}

.review-progress span.current {
  background: #c89543;
}

.review-banner.done .review-progress span {
  background: #edf0f4;
}

.review-action {
  display: flex;
  align-items: center;
  gap: 18px;
}

.review-count {
  display: grid;
  justify-items: end;
}

.review-count strong {
  font-size: 26px;
  font-weight: 680;
}

.review-count span {
  color: #aebed6;
  font-size: 9px;
}

.review-link {
  height: 41px;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 0 19px;
  border-radius: 7px;
  background: #fff;
  color: #2453a8;
  font-size: 12px;
  font-weight: 700;
  text-decoration: none;
}

.review-link .app-icon {
  transform: rotate(180deg);
}

.review-banner.done .review-link {
  border: 1px solid var(--color-line);
  color: var(--color-ink-soft);
}

@media (max-width: 900px) {
  .review-banner {
    align-items: flex-start;
    flex-wrap: wrap;
  }

  .review-copy {
    width: 100%;
  }

  .review-progress {
    min-width: 220px;
  }
}

@media (max-width: 560px) {
  .review-banner {
    gap: 16px;
    padding: 20px;
  }

  .review-progress {
    width: 100%;
    flex-basis: 100%;
  }

  .review-action {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
