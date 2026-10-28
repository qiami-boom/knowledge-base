<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDueCards, submitReview } from '../api/review'

const cards = ref([])         // 本次复习队列
const index = ref(0)          // 当前第几张（指针，不删数组）
const loading = ref(true)     //首屏是否在加载
const errorMsg = ref('')      // 非空 = 出错了
const showAnswer = ref(false) // 是否已翻面
const submitting = ref(false) // 提交中，防连点

const current = computed(() => cards.value[index.value] || null)
const finished = computed(() => !loading.value && !errorMsg.value && index.value >= cards.value.length)
const progress = computed(() =>
    cards.value.length ? `${Math.min(index.value + 1, cards.value.length)} / ${cards.value.length}` : ''
)

// 错误分层：没拿到 response = 请求没发出去；拿到了 = 后端回了错误
const describeError = (e, fallback) => {
  if (!e.response) return '无法连接后端服务，请确认 8081 已启动'
  return e.response.data?.message || fallback
}

const load = async () => {
  loading.value = true
  errorMsg.value = ''
  index.value = 0
  showAnswer.value = false
  try {
    cards.value = await getDueCards()
  } catch (e) {
    console.error('加载待复习卡片失败:', e)
    errorMsg.value = describeError(e, '加载待复习卡片失败')
    cards.value = []
  } finally {
    loading.value = false
  }
}

onMounted(load)

const reveal = () => { showAnswer.value = true }

const rate = async (quality) => {
  if (submitting.value || !current.value) return   // 双保险：连点 + 无卡
  submitting.value = true
  errorMsg.value = ''
  try {
    await submitReview(current.value.id, quality)
    index.value += 1        // 成功才前进，失败留在原地
    showAnswer.value = false
  } catch (e) {
    console.error('提交复习结果失败:', e)
    errorMsg.value = describeError(e, '提交失败，请重试')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="review-page">
    <div class="head">
      <h2>今日复习</h2>
      <span v-if="progress" class="progress">{{ progress }}</span>
    </div>

    <p v-if="loading" class="hint">加载中…</p>

    <div v-else-if="errorMsg" class="hint error">
      <span>{{ errorMsg }}</span>
      <button class="ghost" @click="load">重试</button>
    </div>

    <div v-else-if="finished" class="hint">
      <span>今天的卡片都复习完了。</span>
      <button class="ghost" @click="load">再看看</button>
    </div>

    <div v-else class="card">
      <p class="question">{{ current.question }}</p>

      <div v-if="showAnswer" class="answer">
        <hr />
        <p>{{ current.answer }}</p>
      </div>

      <div class="actions">
        <button v-if="!showAnswer" class="primary" @click="reveal">显示答案</button>

        <div v-else class="ratings">
          <button class="forgot"     :disabled="submitting" @click="rate('FORGOT')">忘记</button>
          <button class="fuzzy"      :disabled="submitting" @click="rate('FUZZY')">模糊</button>
          <button class="remembered" :disabled="submitting" @click="rate('REMEMBERED')">记住</button>
        </div>
      </div>

      <p class="meta">当前阶数：{{ current.stage }}</p>
    </div>
  </div>
</template>

<style scoped>
.review-page { max-width: 640px; margin: 0 auto; }
.head { display: flex; align-items: baseline; justify-content: space-between; }
.head h2 { margin: 0 0 16px; }
.progress { color: #888; font-size: 14px; }
.hint { color: #666; }
.hint.error { color: #c62828; }
.hint .ghost, .hint button { margin-left: 12px; }
.card {
  background: #fff; border-radius: 8px; padding: 24px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}
.question { font-size: 18px; line-height: 1.6; margin: 0 0 16px; }
.answer p { color: #333; line-height: 1.6; }
hr { border: none; border-top: 1px solid #eee; margin: 16px 0; }
.actions { margin-top: 24px; }
.primary {
  width: 100%; padding: 12px; font-size: 16px; border: none;
  border-radius: 6px; background: #1976d2; color: #fff; cursor: pointer;
}
.ratings { display: flex; gap: 12px; }
.ratings button {
  flex: 1; padding: 12px; font-size: 15px; border: none;
  border-radius: 6px; color: #fff; cursor: pointer;
}
.ratings button:disabled { opacity: 0.5; cursor: not-allowed; }
.forgot { background: #e53935; }
.fuzzy { background: #fb8c00; }
.remembered { background: #43a047; }
.meta { margin: 16px 0 0; color: #999; font-size: 13px; }
</style>