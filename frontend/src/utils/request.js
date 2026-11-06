import axios from 'axios'
import router from '../router'
import { markLoggedOut } from './authState'

// 全局唯一的 axios 实例：baseURL / 拦截器 / 超时都只在这里配置
const request = axios.create({
    baseURL: '/api',   // 配合 Vite 代理转发到 8081
    timeout: 60000,    // 生成复习卡要等 AI，默认不设超时会一直挂着
    withCredentials: true // 同源走代理时其实用不上，但保留可在跨域部署时带上 Cookie
})

// 成功响应拦截器：后端统一返回 Result 信封，这里剥掉两层，组件只拿 data
request.interceptors.response.use(
    (response) => response.data.data,
    (error) => {
        const status = error.response?.status

        // 401 = 未登录或会话过期：清掉本地判断，并把用户送回登录页
        if (status === 401) {
            markLoggedOut()

            const current = router.currentRoute.value

            // 登录页本身返回 401（比如密码错）不跳转，否则会打断用户输入
            if (current.name !== 'login') {
                router.replace({
                    name: 'login',
                    query: { redirect: current.fullPath }
                })
            }
        }

        return Promise.reject(error)
    }
)

export default request
