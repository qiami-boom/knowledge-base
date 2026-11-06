<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout as logoutApi } from './api/auth'
import { markLoggedOut } from './utils/authState'

const route = useRoute()
const router = useRouter()
const loggingOut = ref(false)

/**
 * 退出登录：先让后端销毁会话，再清本地判断并回登录页。
 * 后端退出接口是幂等的，即使会话已失效也会返回成功。
 */
const logout = async () => {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await logoutApi()
  } catch (e) {
    // 网络不通时也要让用户回到登录页，避免卡在业务页
    console.warn('退出接口调用失败，仍清除本地登录态', e)
  } finally {
    markLoggedOut()
    loggingOut.value = false
    router.replace({ name: 'login' })
  }
}
</script>

<template>
  <div id="app">
    <header v-if="!route.meta.hideHeader" class="app-header">
      <h1>个人知识库</h1>
      <nav class="nav">
        <router-link to="/chat">对话</router-link>
        <router-link to="/">知识列表</router-link>
        <router-link to="/review">今日复习</router-link>
        <a href="#" @click.prevent="logout">{{ loggingOut ? '退出中…' : '退出' }}</a>
      </nav>
    </header>
    <main :class="{ 'app-content': !route.meta.hideHeader }">
      <router-view />
    </main>
  </div>
</template>

<style scoped>
#app {
  min-height: 100vh;
  background: #f5f5f5;
}
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #1976d2;
  color: white;
  padding: 16px 24px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}
.app-header h1 {
  margin: 0;
  font-size: 22px;
}
.nav a {
  color: #fff;
  text-decoration: none;
  margin-left: 20px;
  opacity: 0.75;
  font-size: 15px;
}
.nav a.router-link-active {
  opacity: 1;
  font-weight: 600;
  border-bottom: 2px solid #fff;
  padding-bottom: 2px;
}
.app-content {
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 16px;
}
</style>
