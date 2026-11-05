import { createRouter, createWebHistory } from 'vue-router'
import KnowledgeList from '../views/KnowledgeList.vue'
import KnowledgeDetail from '../views/KnowledgeDetail.vue'
import Review from '../views/Review.vue'
import Login from '../views/Login.vue'

const routes = [
    {
        path: '/login',
        name: 'login',
        component: Login,
        meta: { public: true, hideHeader: true }   // 免登录 + 不显示顶部导航
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
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

// 登录态只存一个标记，刷新页面不丢
const isLoggedIn = () => localStorage.getItem('kb_logged_in') === '1'

router.beforeEach((to) => {
    if (to.meta.public) return true            // 白名单直接放行
    if (isLoggedIn()) return true              // 已登录放行
    return { name: 'login', query: { redirect: to.fullPath } }
})

export default router
