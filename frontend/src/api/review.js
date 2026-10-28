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