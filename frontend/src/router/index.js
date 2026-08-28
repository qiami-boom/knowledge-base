import { createRouter, createWebHistory } from 'vue-router'
import KnowledgeList from '../views/KnowledgeList.vue'
import KnowledgeDetail from '../views/KnowledgeDetail.vue'

const routes = [
    {
        path: '/',
        name: 'list',
        component: KnowledgeList
    },
    {
        path: '/detail/:id',
        name: 'detail',
        component: KnowledgeDetail
    }
]

const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router