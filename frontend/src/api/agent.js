import request from '../utils/request'

// 从一段文本里抽 0~N 条知识草稿
// 后端：POST /api/extract  body: { text }
export const extractKnowledge = (text) => {
    return request.post('/extract', { text })
}

// 确认草稿入库：status DRAFT → ACTIVE
// 后端：POST /api/knowledges/{id}/confirm
export const confirmDraft = (id) => {
    return request.post(`/knowledges/${id}/confirm`)
}

// 丢弃草稿：物理删除
// 后端：POST /api/knowledges/{id}/discard
export const discardDraft = (id) => {
    return request.post(`/knowledges/${id}/discard`)
}

// 上传文件抽取：解析器已分好条，逐条评分后返回草稿
// 后端：POST /api/extract/file（multipart）
// 56 条的文件要跑几十次 AI 调用，timeout 单独放宽
export const extractByFile = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/extract/file', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 300000
    })
}

// 网页抓取抽取：后端调 DeepSeek Fetch 拿 Markdown 正文，再抽取
// 后端：POST /api/extract/url
export const extractByUrl = (url) => {
    return request.post('/extract/url', { url }, { timeout: 120000 })
}

// Agent 对话：后端会自己决定调用哪些工具，可能调多个，所以要给较长超时
// 后端：POST /api/agent/chat  body: { message, history }
// 返回：{ reply, cards, steps }
export const agentChat = (message, history = []) => {
    return request.post('/agent/chat', { message, history }, { timeout: 180000 })
}
