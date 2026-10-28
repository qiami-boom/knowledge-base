import axios from 'axios'

// 全局唯一的 axios 实例：baseURL / 拦截器 / 超时都只在这里配置
const request = axios.create({
    baseURL: '/api',   // 配合 Vite 代理转发到 8081
    timeout: 60000     // 生成复习卡要等 AI，默认不设超时会一直挂着
})

// 成功响应拦截器：后端统一返回 Result 信封，这里直接剥掉两层，组件只拿 data
request.interceptors.response.use(
    (response) => response.data.data
)

export default request