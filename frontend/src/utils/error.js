// 把错误翻译成人话：区分「请求没发出去」和「后端返回了业务错误」
export const describeError = (error, fallback = '操作失败，请稍后重试') => {
    if (!error.response) return '无法连接后端服务，请确认 8081 已启动'
    return error.response.data?.message || fallback
}