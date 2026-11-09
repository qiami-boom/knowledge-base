import request from '../utils/request'
// 查全部知识列表
export const getAllKnowledge = () => {
    return request.get('/knowledges')
}

// 按 ID 查详情
export const getKnowledgeById = (id) => {
    return request.get(`/knowledges/${id}`)
}

// 新增一条
export const addKnowledge = (data) => {
    return request.post('/knowledges', data)
}

// 修改
export const updateKnowledge = (id, data) => {
    return request.put(`/knowledges/${id}`, data)
}

// 让 AI 精简一张已有知识卡
export const condenseKnowledge = (id, keepOriginal = false) => {
    return request.post(`/knowledges/${id}/condense`, { keepOriginal }, {
        timeout: 120000
    })
}

// 删除
export const deleteKnowledge = (id) => {
    return request.delete(`/knowledges/${id}`)
}
// 新增导入接口
export const importKnowledge = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/knowledge/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 300000
    })
}
