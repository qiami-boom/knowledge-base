<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { logout as logoutApi } from './api/auth'
import { markLoggedOut } from './utils/authState'

const route = useRoute()
const router = useRouter()
const loggingOut = ref(false)

/**
 * 缓存实例的版本号：回到登录页时 +1，让 keep-alive 里的对话页作废。
 * 否则 A 退出、B 登录后，B 会看到 A 上一轮的聊天记录（缓存没清）。
 */
const viewKey = ref(0)
watch(() => route.name, (name) => {
  if (name === 'login') viewKey.value++
})

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
      <!--
        只缓存对话页：agent 回答要几十秒，用户切去知识库再回来时，
        组件不能被销毁，否则请求回来时已经没有地方接结果了。
        其它页面不缓存，避免列表/复习页显示过期数据。
      -->
      <router-view v-slot="{ Component }">
        <keep-alive include="AgentChat" :max="1">
          <component :is="Component" :key="viewKey" />
        </keep-alive>
      </router-view>
    </main>
  </div>
</template>

<style scoped>
#app {
  min-height: 100vh;
  background: #f7f8fa;
}
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  color: #222;
  padding: 14px 28px;
  border-bottom: 1px solid #eef0f3;
  position: sticky;
  top: 0;
  z-index: 10;
}
.app-header h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.3px;
}
.nav a {
  color: #555;
  text-decoration: none;
  margin-left: 24px;
  font-size: 14px;
  transition: color 0.15s;
}
.nav a:hover {
  color: #3b6ef5;
}
.nav a.router-link-active {
  color: #3b6ef5;
  font-weight: 600;
}
.app-content {
  max-width: 1320px;
  margin: 0 auto;
  padding: 28px 24px;
}
</style>
