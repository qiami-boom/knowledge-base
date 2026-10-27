import axios from 'axios'

// 所有请求用同一个 axios 实例，方便统一配置
const request = axios.create({
    baseURL: '/api'// 所有接口都以 /api 开头，配合 Vite 代理转发
})

request.interceptors.response.use(//成功响应拦截器
    (response) => response.data.data
)
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

// 删除
export const deleteKnowledge = (id) => {
    return request.delete(`/knowledges/${id}`)
}
// 新增导入接口
export const importKnowledge = (file) => {
    const formData = new FormData()
    formData.append('file', file)
    return request.post('/knowledge/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
    })
}