// 把错误翻译成人话：区分「请求没发出去」和「后端返回了业务错误」
export const describeError = (error, fallback = '操作失败，请稍后重试') => {
    const response = error.response
    // ① 连响应都没有：断网 / 浏览器拦截 / 跨域预检失败
    if (!response) return '无法连接后端服务，请确认 8081 已启动'

    // ② 有响应，但不是后端发的信封 —— 开发期是 Vite 代理的 500，
    //    上线后是 Nginx 的 502/504（HTML 错误页）
    const body = response.data
    const isApiEnvelope = body && typeof body === 'object' && typeof body.message === 'string'
    if (!isApiEnvelope) return '后端服务暂时不可用，请确认 8081 已启动后重试'

    // ③ 后端自己的业务错误：直接用它给的话
    return body.message || fallback
}