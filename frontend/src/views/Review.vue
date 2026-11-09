<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getDueCards, submitReview, getQuiz } from '../api/review'
import { describeError } from '../utils/error'

const cards = ref([])         // 本次复习队列
const index = ref(0)          // 当前第几张（指针，不删数组）
const loading = ref(true)     //首屏是否在加载
const errorMsg = ref('')      // 非空 = 出错了
const showAnswer = ref(false) // 是否已翻面
const submitting = ref(false) // 提交中，防连点

// 选择题模式
const quizMode = ref(false)
const quiz = ref(null)          // ChoiceQuestion：stem / options / correctIndex / explanation
const quizLoading = ref(false)
const picked = ref(-1)          // 用户选了第几个，-1 = 还没选
const quizCache = ref({})       // cardId -> 题目，同一张卡只生成一次，切回来不重复调 AI

const router = useRouter()
const backToChat = () => router.push('/chat')

const current = computed(() => cards.value[index.value] || null)
const finished = computed(() => !loading.value && !errorMsg.value && index.value >= cards.value.length)
const progress = computed(() =>
    cards.value.length ? `${Math.min(index.value + 1, cards.value.length)} / ${cards.value.length}` : ''
)

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

// 生成一道选择题。判分放前端做（correctIndex 随题目一起返回），省一次 AI 调用
const startQuiz = async () => {
  if (!current.value || quizLoading.value) return

  const cardId = current.value.id
  // 同一张卡生成过就复用，不再花一次 AI 调用
  if (quizCache.value[cardId]) {
    quiz.value = quizCache.value[cardId]
    picked.value = -1
    quizMode.value = true
    return
  }

  quizLoading.value = true
  errorMsg.value = ''
  try {
    const generated = await getQuiz(cardId)
    // AI 失败时后端返回的是空题（没有选项、correctIndex=-1）。
    // 直接进选择题模式会卡在"没有选项也没有下一张"的死局，这里拦住
    if (!generated
        || !Array.isArray(generated.options)
        || generated.options.length < 2
        || generated.correctIndex < 0
        || generated.correctIndex >= generated.options.length) {
      errorMsg.value = '这次没生成出可用的题目，请再试一次'
      return
    }
    quizCache.value[cardId] = generated
    quiz.value = generated
    picked.value = -1
    quizMode.value = true
  } catch (e) {
    console.error('生成选择题失败:', e)
    errorMsg.value = describeError(e, '题目生成失败，请重试')
  } finally {
    quizLoading.value = false
  }
}

const pick = (i) => {
  if (picked.value >= 0) return   // 选过一次就不能改答案
  picked.value = i
}

/**
 * 选择题答完 → 按对错提交自评 → 前进到下一张。
 * 答对算"记住"，答错算"忘记"，让选择题真正参与调度，而不是白玩一场。
 */
const finishQuiz = async () => {
  if (!current.value || !quiz.value || submitting.value) return
  if (picked.value < 0) return   // 没选就不提交：否则会被当成答错，把卡打回第 1 阶
  submitting.value = true
  errorMsg.value = ''

  const quality = picked.value === quiz.value.correctIndex ? 'REMEMBERED' : 'FORGOT'
  try {
    await submitReview(current.value.id, quality)
    index.value += 1
    quizMode.value = false
    quiz.value = null
    picked.value = -1
    showAnswer.value = false
  } catch (e) {
    console.error('提交复习结果失败:', e)
    errorMsg.value = describeError(e, '提交失败，请重试')
  } finally {
    submitting.value = false
  }
}

/** 已经复习完一轮，用同一批卡再过一遍（不重新请求，避免拿到空队列） */
const restart = () => {
  index.value = 0
  showAnswer.value = false
  quizMode.value = false
  quiz.value = null
  picked.value = -1
}

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
    <header class="page-header review-head">
      <div class="page-heading">
        <div class="page-kicker">Review</div>
        <h1 class="page-title">今日复习</h1>
        <p class="page-subtitle">先主动回忆，再翻看答案。每次自评都会决定下次出现的时间。</p>
      </div>
      <div class="page-actions">
        <span v-if="progress" class="progress">{{ progress }}</span>
      </div>
    </header>

    <p v-if="loading" class="hint">加载中…</p>

    <div v-else-if="errorMsg" class="hint error">
      <span>{{ errorMsg }}</span>
      <button class="ghost" @click="load">重试</button>
    </div>

    <div v-else-if="finished" class="done-card">
      <div class="done-icon">✓</div>
      <h3>今天的复习完成了</h3>
      <p>这一轮过了 {{ cards.length }} 张卡，答题结果已经写进下次的复习时间。</p>
      <div class="done-actions">
        <button class="primary" @click="restart">再过一遍</button>
        <button class="ghost" @click="load">刷新队列</button>
        <button class="ghost" @click="backToChat">返回对话</button>
      </div>
    </div>

    <div v-else class="card">
      <!-- 选择题模式 -->
      <template v-if="quizMode && quiz">
        <p class="quiz-stem">{{ quiz.stem }}</p>

        <div class="options">
          <button
              v-for="(opt, i) in quiz.options"
              :key="i"
              class="option"
              :class="{
                correct: picked >= 0 && i === quiz.correctIndex,
                wrong: picked === i && i !== quiz.correctIndex
              }"
              :disabled="picked >= 0"
              @click="pick(i)"
          >
            <span class="opt-label">{{ 'ABCD'[i] }}</span>
            <span class="opt-text">{{ opt }}</span>
          </button>
        </div>

        <div v-if="picked >= 0" class="quiz-result">
          <p :class="picked === quiz.correctIndex ? 'ok' : 'no'">
            {{ picked === quiz.correctIndex
                ? '答对了'
                : '答错了，正确答案是 ' + 'ABCD'[quiz.correctIndex] }}
          </p>
          <p class="explain">{{ quiz.explanation }}</p>
          <button class="primary" :disabled="submitting" @click="finishQuiz">
            {{ submitting ? '提交中…' : '下一张' }}
          </button>
        </div>
      </template>

      <!-- 问答卡模式 -->
      <template v-else>
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

        <button class="quiz-entry" :disabled="quizLoading" @click="startQuiz">
          {{ quizLoading ? '生成题目中…' : '换成选择题试试' }}
        </button>
      </template>
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
  border-radius: 6px; background: #3b6ef5; color: #fff; cursor: pointer;
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

/* 选择题模式 */
.quiz-stem {
  font-size: 17px;
  line-height: 1.7;
  color: #222;
  margin: 0 0 18px;
}
.options {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  text-align: left;
  padding: 12px 14px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #fff;
  font-size: 15px;
  color: #333;
  line-height: 1.5;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}
.option:hover:not(:disabled) {
  border-color: #3b6ef5;
  background: #f8f9ff;
}
.option:disabled {
  cursor: default;
}
.option.correct {
  border-color: #43a047;
  background: #f1f9f2;
  color: #1b5e20;
}
.option.wrong {
  border-color: #e53935;
  background: #fdf3f3;
  color: #b71c1c;
}
.opt-label {
  flex-shrink: 0;
  width: 22px;
  height: 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: #eef2ff;
  color: #3b6ef5;
  font-size: 12px;
  font-weight: 600;
}
.option.correct .opt-label {
  background: #43a047;
  color: #fff;
}
.option.wrong .opt-label {
  background: #e53935;
  color: #fff;
}
.opt-text {
  flex: 1;
}
.quiz-result {
  margin-top: 18px;
}
.quiz-result .ok {
  color: #2e7d32;
  font-weight: 600;
}
.quiz-result .no {
  color: #c62828;
  font-weight: 600;
}
.explain {
  color: #555;
  font-size: 14px;
  line-height: 1.6;
  margin: 8px 0 16px;
}
.quiz-entry {
  width: 100%;
  margin-top: 18px;
  padding: 10px;
  border: 1px dashed #d0d7e2;
  border-radius: 8px;
  background: #fafbff;
  color: #5c6b8a;
  font-size: 14px;
  cursor: pointer;
}
.quiz-entry:hover:not(:disabled) {
  border-color: #3b6ef5;
  color: #3b6ef5;
}
.quiz-entry:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 复习完成态 */
.done-card {
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  padding: 48px 32px;
  text-align: center;
}
.done-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 20px;
  border-radius: 50%;
  background: #eef7ef;
  color: #43a047;
  font-size: 26px;
  line-height: 56px;
}
.done-card h3 {
  margin: 0 0 10px;
  font-size: 20px;
  color: #222;
}
.done-card p {
  margin: 0 0 24px;
  color: #888;
  font-size: 14px;
  line-height: 1.6;
}
.done-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}
.done-actions .primary {
  width: auto;
  padding: 10px 28px;
}
.done-actions .ghost {
  padding: 10px 20px;
  border: 1px solid #ddd;
  border-radius: 6px;
  background: #fff;
  color: #666;
  font-size: 15px;
  cursor: pointer;
}
.done-actions .ghost:hover {
  border-color: #3b6ef5;
  color: #3b6ef5;
}
</style>

<style scoped>
.review-page {
  max-width: 780px;
  margin: 0 auto;
}

.review-head {
  margin-bottom: 24px;
}

.progress {
  padding: 6px 10px;
  border: 1px solid var(--color-line);
  border-radius: 999px;
  background: var(--color-surface);
  color: var(--color-ink-soft);
  font-size: 10px;
}

.hint {
  color: var(--color-muted);
  text-align: center;
}

.hint.error {
  color: var(--color-danger);
}

.card,
.done-card {
  border: 1px solid var(--color-line);
  border-radius: var(--radius-md);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
}

.card {
  padding: 28px;
}

.question,
.quiz-stem {
  color: var(--color-ink);
  font-size: 18px;
  line-height: 1.7;
}

.answer p {
  color: var(--color-ink-soft);
  font-size: 13px;
  line-height: 1.8;
}

hr {
  border-top-color: var(--color-line);
}

.primary {
  border-radius: var(--radius-md);
  background: var(--color-primary);
}

.primary:hover:not(:disabled) {
  background: var(--color-primary-strong);
}

.ratings button {
  border-radius: var(--radius-md);
}

.forgot {
  background: var(--color-danger);
}

.fuzzy {
  background: var(--color-amber);
}

.remembered {
  background: var(--color-success);
}

.meta {
  color: var(--color-muted);
}

.option {
  border-color: var(--color-line);
  border-radius: 7px;
}

.option:hover:not(:disabled) {
  border-color: var(--color-primary);
  background: #f8faff;
}

.opt-label {
  background: var(--color-primary-soft);
  color: var(--color-primary);
}

.quiz-entry {
  border-color: #cfd9e9;
  border-radius: 7px;
  background: #f8faff;
  color: #5e6d82;
}

.quiz-entry:hover:not(:disabled) {
  border-color: var(--color-primary);
  color: var(--color-primary);
}

.done-icon {
  background: var(--color-success-soft);
  color: var(--color-success);
}

.done-actions .ghost {
  border-color: var(--color-line);
  border-radius: 7px;
}

@media (max-width: 720px) {
  .review-head .page-actions {
    display: none;
  }

  .card {
    padding: 21px 17px;
  }

  .ratings {
    flex-direction: column;
  }

  .done-actions {
    flex-wrap: wrap;
  }
}
</style>
