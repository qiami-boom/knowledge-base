<script setup>
import { ref, onMounted } from 'vue'
import { getDueCards } from '../api/review'
import { describeError } from '../utils/error'

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
    <div class="info">
      <p class="label">今日复习</p>

      <p v-if="loading" class="sub">正在统计…</p>
      <p v-else-if="errorMsg" class="sub error">
        {{ errorMsg }}
        <button class="retry" @click="load">重试</button>
      </p>
      <p v-else-if="count > 0" class="sub">
        还有 <strong>{{ count }}</strong> 张卡片等你复习
      </p>
      <p v-else class="sub">今天的卡片都复习完了</p>
    </div>

    <router-link
        v-if="!loading && !errorMsg && count > 0"
        to="/review"
        class="go"
    >
      开始复习 <span class="badge">{{ count }}</span>
    </router-link>

    <router-link
        v-else-if="!loading && !errorMsg"
        to="/review"
        class="go ghost"
    >
      再看看
    </router-link>
  </section>
</template>

<style scoped>
.review-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  margin-bottom: 20px;
  border-radius: 14px;
  background: linear-gradient(135deg, #3b6ef5, #42a5f5);
  color: #fff;
  box-shadow: 0 4px 12px rgba(25, 118, 210, 0.25);
}
.review-banner.done {
  background: #f2f6fa;
  color: #5b6b7c;
  box-shadow: none;
  border: 1px solid #e3ebf3;
}
.info .label {
  margin: 0 0 4px;
  font-size: 13px;
  letter-spacing: 1px;
  opacity: 0.85;
}
.info .sub {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.info .sub strong {
  font-size: 26px;
}
.info .sub.error {
  font-size: 14px;
  font-weight: 400;
}
.retry {
  margin-left: 8px;
  border: none;
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  padding: 4px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
}
.go {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 14px 26px;
  border-radius: 999px;
  background: #fff;
  color: #3b6ef5;
  font-size: 17px;
  font-weight: 700;
  text-decoration: none;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.12);
}
.go.ghost {
  background: transparent;
  color: #5b6b7c;
  border: 1px solid #cfdbe6;
  box-shadow: none;
  font-size: 15px;
  font-weight: 500;
  padding: 10px 20px;
}
.badge {
  background: #3b6ef5;
  color: #fff;
  border-radius: 999px;
  min-width: 24px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  font-size: 14px;
  padding: 0 6px;
}
</style>