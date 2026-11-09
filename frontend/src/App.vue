<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppIcon from './components/AppIcon.vue'
import { logout as logoutApi } from './api/auth'
import { markLoggedOut } from './utils/authState'

const route = useRoute()
const router = useRouter()
const loggingOut = ref(false)
const viewKey = ref(0)

const navigation = [
  { name: 'chat', label: '对话', icon: 'chat', to: '/chat' },
  { name: 'list', label: '知识库', icon: 'library', to: '/' },
  { name: 'review', label: '今日复习', icon: 'review', to: '/review' }
]

watch(() => route.name, (name) => {
  if (name === 'login') viewKey.value++
})

const logout = async () => {
  if (loggingOut.value) return
  loggingOut.value = true
  try {
    await logoutApi()
  } catch (error) {
    console.warn('退出接口调用失败，仍清除本地登录态', error)
  } finally {
    markLoggedOut()
    loggingOut.value = false
    router.replace({ name: 'login' })
  }
}
</script>

<template>
  <div
      class="app-shell"
      :class="{ 'app-shell-public': route.meta.hideHeader }"
  >
    <aside v-if="!route.meta.hideHeader" class="app-sidebar">
      <router-link class="app-brand" to="/">
        <span class="app-brand-name">个人知识库</span>
        <span class="app-brand-sub">Personal research desk</span>
      </router-link>

      <div class="nav-label">Workspace</div>
      <nav class="sidebar-nav" aria-label="主要导航">
        <router-link
            v-for="item in navigation"
            :key="item.name"
            :to="item.to"
            class="nav-item"
        >
          <AppIcon :name="item.icon" />
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-context">
        <span class="context-label">当前空间</span>
        <strong>我的知识工作台</strong>
        <span>所有内容按账号隔离</span>
      </div>

      <div class="sidebar-footer">
        <div class="sidebar-user">
          <span class="avatar">ME</span>
          <span>
            <strong>个人空间</strong>
            <small>已安全登录</small>
          </span>
        </div>
        <button class="logout-btn" type="button" :disabled="loggingOut" @click="logout">
          <AppIcon name="logout" :size="17" />
          {{ loggingOut ? '退出中' : '退出' }}
        </button>
      </div>
    </aside>

    <div v-if="!route.meta.hideHeader" class="mobile-topbar">
      <router-link to="/" class="mobile-brand">个人知识库</router-link>
      <button class="mobile-logout" type="button" :disabled="loggingOut" @click="logout">
        {{ loggingOut ? '退出中' : '退出' }}
      </button>
    </div>

    <main class="app-main" :class="{ 'app-main-public': route.meta.hideHeader }">
      <router-view v-slot="{ Component }">
        <keep-alive include="AgentChat" :max="1">
          <component :is="Component" :key="viewKey" />
        </keep-alive>
      </router-view>
    </main>

    <nav v-if="!route.meta.hideHeader" class="mobile-nav" aria-label="移动端导航">
      <router-link
          v-for="item in navigation"
          :key="item.name"
          :to="item.to"
          class="mobile-nav-item"
      >
        <AppIcon :name="item.icon" :size="19" />
        <span>{{ item.label }}</span>
      </router-link>
    </nav>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  background: var(--color-canvas);
}

.app-sidebar {
  position: sticky;
  top: 0;
  width: 228px;
  height: 100vh;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  padding: 24px 18px 18px;
  background: #182130;
  color: #f7f9fc;
}

.app-brand {
  display: flex;
  flex-direction: column;
  padding: 6px 10px 29px;
  text-decoration: none;
}

.app-brand-name {
  color: #fff;
  font-size: 16px;
  font-weight: 650;
}

.app-brand-sub {
  margin-top: 4px;
  color: #8290a4;
  font-size: 10px;
}

.nav-label {
  padding: 0 12px;
  margin: 15px 0 8px;
  color: #68778d;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.sidebar-nav {
  display: grid;
  gap: 3px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 11px;
  min-height: 42px;
  padding: 0 12px;
  border-radius: 7px;
  color: #aab4c4;
  font-size: 13px;
  text-decoration: none;
  transition: background 0.16s ease, color 0.16s ease;
}

.nav-item:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
}

.nav-item.router-link-active {
  background: #243149;
  color: #fff;
  box-shadow: inset 2px 0 0 #6e99ff;
}

.sidebar-context {
  display: grid;
  gap: 4px;
  margin-top: 26px;
  padding: 13px 12px;
  border: 1px solid rgba(255, 255, 255, 0.07);
  border-radius: 7px;
  color: #7f8da2;
  font-size: 9px;
  line-height: 1.5;
}

.sidebar-context strong {
  color: #b9c4d3;
  font-size: 10px;
  font-weight: 600;
}

.context-label {
  color: #617086;
  font-size: 8px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.sidebar-footer {
  margin-top: auto;
  padding-top: 14px;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.sidebar-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 8px 11px;
}

.sidebar-user > span:last-child {
  display: grid;
  gap: 2px;
}

.sidebar-user strong {
  color: #e7edf5;
  font-size: 11px;
  font-weight: 600;
}

.sidebar-user small {
  color: #738197;
  font-size: 9px;
}

.avatar {
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #dce6fb;
  color: #24446f;
  font-size: 10px;
  font-weight: 700;
}

.logout-btn {
  width: 100%;
  min-height: 34px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 10px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #8694a8;
  cursor: pointer;
  font-size: 10px;
}

.logout-btn:hover {
  background: rgba(255, 255, 255, 0.05);
  color: #d9e2ef;
}

.app-main {
  flex: 1;
  min-width: 0;
  min-height: 100vh;
  padding: 26px 32px 34px;
}

.app-main-public {
  min-height: 100vh;
  padding: 0;
}

.mobile-topbar,
.mobile-nav {
  display: none;
}

@media (max-width: 760px) {
  .app-shell {
    display: block;
    padding-top: 56px;
  }

  .app-sidebar {
    display: none;
  }

  .mobile-topbar {
    position: fixed;
    inset: 0 0 auto;
    z-index: 30;
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 18px;
    border-bottom: 1px solid var(--color-line);
    background: rgba(255, 255, 255, 0.96);
    backdrop-filter: blur(12px);
  }

  .mobile-brand {
    color: var(--color-ink);
    font-size: 15px;
    font-weight: 700;
    text-decoration: none;
  }

  .mobile-logout {
    border: 0;
    background: transparent;
    color: var(--color-muted);
    font-size: 11px;
  }

  .app-main {
    min-height: calc(100vh - 56px);
    padding: 20px 16px 86px;
  }

  .app-main-public {
    min-height: 100vh;
    padding: 0;
  }

  .mobile-nav {
    position: fixed;
    inset: auto 0 0;
    z-index: 30;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    padding: 7px 10px calc(7px + env(safe-area-inset-bottom));
    border-top: 1px solid var(--color-line);
    background: rgba(255, 255, 255, 0.97);
    backdrop-filter: blur(14px);
  }

  .mobile-nav-item {
    min-height: 48px;
    display: grid;
    place-items: center;
    gap: 3px;
    color: var(--color-muted);
    font-size: 9px;
    text-decoration: none;
  }

  .mobile-nav-item.router-link-active {
    color: var(--color-primary);
  }
}
</style>
