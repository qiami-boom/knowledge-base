import { createRouter, createWebHistory } from 'vue-router'
import KnowledgeList from '../views/KnowledgeList.vue'
import KnowledgeDetail from '../views/KnowledgeDetail.vue'
import Review from '../views/Review.vue'
import AgentChat from '../views/AgentChat.vue'
import Login from '../views/Login.vue'
import { me } from '../api/auth'
import { hasChecked, isVerified, markLoggedIn, markLoggedOut } from '../utils/authState'

const routes = [
    {
        path: '/login',
        name: 'login',
        component: Login,
        // public：免登录；hideHeader：不显示顶部导航
        meta: { public: true, hideHeader: true }
    },
    {
        path: '/',
        name: 'list',
        component: KnowledgeList
    },
    {
        path: '/detail/:id',
        name: 'detail',
        component: KnowledgeDetail
    },
    {
        path: '/review',
        name: 'review',
        component: Review
    },
    {
        path: '/chat',
        name: 'chat',
        component: AgentChat
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

/**
 * 登录守卫：
 * 首次进入时向 /api/auth/me 问一次"我是谁"，之后用内存缓存，避免每跳一页都请求。
 * 真正的凭证是 Cookie 里的 JSESSIONID，这里只是决定要不要展示页面。
 */
router.beforeEach(async (to) => {
    if (to.meta.public) return true

    // 已经问过后端：直接按结果放行或跳登录
    if (hasChecked()) {
        if (isVerified()) return true
        return { name: 'login', query: { redirect: to.fullPath } }
    }

    try {
        await me()
        markLoggedIn()
        return true
    } catch (e) {
        markLoggedOut()
        return { name: 'login', query: { redirect: to.fullPath } }
    }
})

export default router
