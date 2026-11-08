import request from '../utils/request'

// 取今天到期的复习卡
export const getDueCards = () => {
    return request.get('/review/due')
}

// 提交一张卡的自评：quality 取 REMEMBERED / FUZZY / FORGOT
export const submitReview = (id, quality) => {
    return request.post(`/review/${id}`, null, {
        params: { quality }
    })
}

// 为某条知识生成复习卡（幂等，重复调用不会重复生成）
export const generateCards = (knowledgeId) => {
    return request.post(`/review/cards/${knowledgeId}`)
}

// 掌握现状：按阶段统计卡片数量 { initial, strengthen, consolidate, mastered, total }
export const getStats = () => {
    return request.get('/review/stats')
}

// 每条知识的最高复习阶数 { "46": 3, ... }，没有卡的知识不在返回里
export const getProgress = () => {
    return request.get('/review/progress')
}

// 为某张复习卡实时生成一道选择题（要调 AI，5 秒左右）
export const getQuiz = (cardId) => {
    return request.get(`/review/cards/${cardId}/quiz`, { timeout: 60000 })
}