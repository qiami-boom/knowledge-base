<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { login } from '../api/auth'
import { markLoggedIn } from '../utils/authState'

const router = useRouter()
const route = useRoute()

const form = ref({ username: '', password: '' })
const errorMsg = ref('')
const errorField = ref('') // 'username' | 'password' | 'both' | ''
const submitting = ref(false)

// 错误定位：只把真正填错的输入框标红
const isInvalid = (field) => errorField.value === 'both' || errorField.value === field

/**
 * 登录成功后的去向：只接受站内相对路径。
 * 防止 ?redirect=//evil.com 这类开放重定向把用户带去外站。
 */
const safeRedirect = () => {
  const target = route.query.redirect
  if (typeof target !== 'string') return '/'
  if (!target.startsWith('/') || target.startsWith('//')) return '/'
  return target
}

const submit = async () => {
  if (submitting.value) return

  errorMsg.value = ''
  errorField.value = ''

  if (!form.value.username.trim()) {
    errorMsg.value = '请输入账号'
    errorField.value = 'username'
    return
  }

  if (!form.value.password) {
    errorMsg.value = '请输入密码'
    errorField.value = 'password'
    return
  }

  submitting.value = true
  try {
    // 真正的校验在后端：前端只负责把账号密码送过去
    await login({
      username: form.value.username.trim(),
      password: form.value.password
    })
    markLoggedIn()
    router.replace(safeRedirect())
  } catch (e) {
    errorField.value = 'both'
    if (!e.response) {
      errorMsg.value = '无法连接后端服务，请确认 8081 已启动'
    } else {
      errorMsg.value = e.response.data?.message || '账号或密码错误，请重试'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <div class="brand">
        <span class="logo" aria-hidden="true">知</span>
        <h1>个人知识库</h1>
        <p class="sub">登录后继续整理你的笔记</p>
      </div>

      <!-- 表单内回车即提交：依赖原生 form + type="submit"，不手写 keydown -->
      <form class="form" @submit.prevent="submit" novalidate>
        <div class="field">
          <label for="username">账号</label>
          <input
            id="username"
            v-model="form.username"
            type="text"
            autocomplete="username"
            placeholder="请输入账号"
            :aria-invalid="isInvalid('username')"
            :disabled="submitting"
            autofocus
          />
        </div>

        <div class="field">
          <label for="password">密码</label>
          <input
            id="password"
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            :aria-invalid="isInvalid('password')"
            :disabled="submitting"
          />
        </div>

        <p v-if="errorMsg" class="error" role="alert" aria-live="assertive">
          {{ errorMsg }}
        </p>

        <button type="submit" class="submit" :disabled="submitting">
          {{ submitting ? '登录中…' : '登录' }}
        </button>
      </form>

      <p class="foot">账号由管理员创建，忘记密码请联系管理员</p>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background-color: #f5f5f5;
  /* 极淡的点阵，避免纯色背景显得空 */
  background-image: radial-gradient(circle at 1px 1px, rgba(25, 118, 210, 0.07) 1px, transparent 0);
  background-size: 18px 18px;
}

.login-card {
  position: relative;
  width: min(100%, 380px);
  padding: 36px 32px 26px;
  background: #fff;
  border: 1px solid #eaeaea;
  border-radius: 10px;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.08);
  overflow: hidden;
}

/* 顶部一条细蓝线，作为品牌记忆点 */
.login-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 3px;
  background: linear-gradient(90deg, #1976d2, #64b5f6 70%, #e3f2fd);
}

/* ---------- 品牌区 ---------- */
.brand {
  text-align: center;
  margin-bottom: 26px;
}

.logo {
  display: inline-grid;
  place-items: center;
  width: 46px;
  height: 46px;
  margin-bottom: 14px;
  border-radius: 10px;
  background: #1976d2;
  color: #fff;
  font-family: 'Songti SC', 'Noto Serif SC', 'STSong', serif;
  font-size: 22px;
  line-height: 1;
  box-shadow: 0 4px 12px rgba(25, 118, 210, 0.28);
}

.brand h1 {
  margin: 0 0 6px;
  color: #1976d2;
  font-size: 22px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.sub {
  margin: 0;
  color: #8a8a8a;
  font-size: 13px;
}

/* ---------- 表单 ---------- */
.form {
  display: grid;
  gap: 18px;
}

.field {
  display: grid;
  gap: 7px;
}

.field label {
  color: #555;
  font-size: 13px;
  letter-spacing: 0.02em;
}

.field input {
  width: 100%;
  padding: 11px 13px;
  border: 1px solid #dcdcdc;
  border-radius: 4px;
  background: #fff;
  color: #333;
  font-family: inherit;
  font-size: 15px;
  outline: none;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}

.field input::placeholder {
  color: #b5b5b5;
}

.field input:focus {
  border-color: #1976d2;
  box-shadow: 0 0 0 3px rgba(25, 118, 210, 0.12);
}

.field input[aria-invalid='true'] {
  border-color: #d32f2f;
}

.field input[aria-invalid='true']:focus {
  box-shadow: 0 0 0 3px rgba(211, 47, 47, 0.12);
}

.field input:disabled {
  background: #fafafa;
  color: #999;
}

.error {
  margin: -4px 0 0;
  color: #d32f2f;
  font-size: 13px;
  line-height: 1.5;
}

.submit {
  width: 100%;
  padding: 12px;
  border: none;
  border-radius: 4px;
  background: #1976d2;
  color: #fff;
  font-family: inherit;
  font-size: 15px;
  letter-spacing: 0.04em;
  cursor: pointer;
  transition: background 0.18s ease, box-shadow 0.18s ease, transform 0.12s ease;
}

.submit:hover:not(:disabled) {
  background: #1565c0;
}

.submit:active:not(:disabled) {
  background: #0d47a1;
  transform: translateY(1px);
}

.submit:focus-visible {
  outline: none;
  box-shadow: 0 0 0 3px rgba(25, 118, 210, 0.3);
}

.submit:disabled {
  background: #90caf9;
  cursor: progress;
}

.foot {
  margin: 20px 0 0;
  color: #a0a0a0;
  font-size: 12px;
  text-align: center;
}

/* 进场：卡片轻微上浮 */
@media (prefers-reduced-motion: no-preference) {
  .login-card {
    animation: rise 0.4s cubic-bezier(0.22, 0.7, 0.3, 1) both;
  }
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 420px) {
  .login-card {
    padding: 28px 22px 22px;
  }
}
</style>
